# DrushtiAI

Android invigilation companion: sign in with Supabase, create exams, pair a detection backend via QR, run a **surveillance** session that **polls** `cheating_snapshots`, and review snapshots on the exam detail screen.

This document captures everything needed to build the **companion website / backend** that ingests a live video feed, runs cheating detection, uploads evidence, and **generates QR codes** the app expects.

---

## Repository layout

| Path | Purpose |
|------|---------|
| `app/` | Android app (Kotlin, Material 3, supabase-kt, CameraX, ML Kit barcode) |
| `supabase_schema.sql` | **Required** Postgres tables, RLS policies, `auth.users` → `profiles` trigger |
| `DESIGN.md` | Visual design tokens and UI patterns for aligning the website with the app |

---

## Supabase configuration (Android)

- **URL + anon key** live in project-root `local.properties` (not committed):

  ```properties
  supabase.url=https://YOUR_PROJECT.supabase.co
  supabase.anon.key=YOUR_ANON_OR_PUBLISHABLE_KEY
  ```

- Gradle injects them into `BuildConfig`; `DrushtiApplication` calls `SupabaseHelper.init()` on startup.
- Client installs **Auth** + **Postgrest** plugins only (no Realtime in app today).

---

## Database schema (`public`)

Run `supabase_schema.sql` once in **Supabase → SQL**. Core tables:

### `profiles`

| Column | Type | Notes |
|--------|------|--------|
| `id` | `uuid` PK | `references auth.users` |
| `full_name` | `text` | From sign-up metadata or Settings |
| `updated_at` | `timestamptz` | default `now()` |

### `exams`

| Column | Type | Notes |
|--------|------|--------|
| `id` | `uuid` PK | App uses this everywhere (`IntentExtras.EXAM_ID`) |
| `user_id` | `uuid` | Invigilator; `references auth.users` |
| `subject` | `text` | |
| `exam_date` | `date` | ISO `YYYY-MM-DD` from app |
| `exam_time` | `text` | Free string, e.g. `10:00` |
| `student_count` | `int` | |
| `room_notes` | `text` nullable | |
| `status` | `text` | App uses `draft`, `live`, `completed` |
| `camera_connected` | `bool` | Set `true` after QR link |
| `linked_device_id` | `text` nullable | **Opaque pairing payload** (JSON string recommended; see [QR link contract](#qr-link-contract-app--website)) |
| `created_at` / `updated_at` | `timestamptz` | |

### `cheating_snapshots`

| Column | Type | Notes |
|--------|------|--------|
| `id` | `uuid` PK | Optional on insert (DB default) |
| `exam_id` | `uuid` FK → `exams.id` | **Must match** the session being monitored |
| `image_url` | `text` | **Publicly readable HTTPS URL** (e.g. Supabase Storage or CDN) |
| `label` | `text` nullable | Shown in grid / viewer title |
| `created_at` | `timestamptz` | |

### Row Level Security (RLS)

- All three tables use RLS. Policies tie rows to **`auth.uid()`** matching `profiles.id` or `exams.user_id`, and snapshots to exams owned by the user.
- **Implication for the detection server:** the **anon key in a browser** cannot insert snapshots *as* the pipeline unless the user is logged in with JWT. For a **headless** ingest service, use one of:
  1. **Service role key** (server-only; bypasses RLS) — recommended for a trusted backend.
  2. **Supabase Edge Function** with service role to validate a secret + insert row + upload file.
  3. **Additional RLS policy** (e.g. insert allowed with a signed device token) — requires schema + app changes.

The Android app only uses the **anon key + user session**; it never needs the service role.

---

## Authentication flow (Android)

1. **`MainActivity`** — Email/password **sign-in** or **sign-up** via `AuthRepository` (`signInWith` / `signUpWith` Email provider).
2. Sign-up sends `user_metadata.full_name`; DB trigger fills `profiles.full_name` when possible.
3. Session restored from storage after `auth.awaitInitialization()`.
4. Logged-in users route to **`HomeActivity`**; **`SettingsActivity`** can update `profiles.full_name` and Auth metadata.

**Website parity:** use the same Supabase project, same Auth (email magic link or password), and the same **`profiles` / `exams`** rows if you want a web dashboard for the same invigilator.

---

## Exam & surveillance flow (Android)

High-level sequence:

1. **Home** → **Start exam** or **FAB** → **`NewExamActivity`**
2. User fills subject, date, time, students, notes → **Save** → row inserted in `exams` (`status` default `draft`).
3. **Link camera** → **`QrScanActivity`** (CameraX + ML Kit) reads **first barcode `rawValue`** → string passed back.
4. App calls `PATCH`-style update: `camera_connected = true`, `linked_device_id = <payload>` (see QR contract below).
5. **Start exam** → **`SurveillanceActivity`** with `IntentExtras.EXAM_ID`.
6. **Start surveillance** → `exams.status` set to **`live`**, then a coroutine **polls every 4 seconds** `GET cheating_snapshots?exam_id=eq.<id>&order=created_at.desc`.
7. **Stop surveillance** → `status` set to **`completed`**, navigate to **`HomeActivity`** (clear task).

**Exam detail:** `ExamDetailActivity` loads one exam, shows read-only card + **Edit** mode, grid of snapshots → tap opens **`SnapshotViewerActivity`** (fullscreen, share URL, delete).

---

## QR link contract (app ↔ website)

Implementation: `QrLinkContract.kt` (validates JSON payloads).

- The app stores the scanned string in **`exams.linked_device_id`** unchanged (after validation).
- **Recommended QR payload** (UTF-8 text, JSON):

```json
{
  "v": 1,
  "exam_id": "550e8400-e29b-41d4-a716-446655440000",
  "ws_url": "wss://detector.example.com/ws/SESSION_TOKEN",
  "ingest_url": "https://detector.example.com/api/snapshots"
}
```

| Field | Required | Notes |
|-------|-----------|--------|
| `v` | Optional | Schema version for your backend |
| `exam_id` | **Required if JSON** | Must equal the open exam’s `exams.id` or the app shows *wrong exam* and **does not** link |
| `examId` | Alternative key | Same as `exam_id` |
| `ws_url` | Optional | Hint for browser to open WebSocket to your live pipeline |
| `ingest_url` | Optional | Hint for your upload API |

**Legacy:** If the payload is **not** valid JSON, it is still stored (any string). Your website can start with plain tokens, but JSON + `exam_id` avoids cross-room mistakes.

**Website responsibilities:**

1. After the invigilator creates an exam (on app or web), know **`exams.id`**.
2. Generate a QR encoding the JSON above (or legacy token).
3. Run the detector; on each incident upload image to storage, then **insert** `cheating_snapshots` with that `exam_id` and public `image_url`.

---

## Snapshot ingestion (backend checklist)

1. **Image hosting** — `image_url` must load in Glide on Android (HTTPS, CORS not required for mobile). Typical: Supabase Storage public bucket or signed long-lived URL.
2. **Insert row** — `POST /rest/v1/cheating_snapshots` with service role or policy that allows your server.
3. **Optional columns** — `label` for UI (e.g. `"Possible phone · seat A3"`).
4. **Latency** — App polls every **4s**; for faster UI use shorter poll or add Supabase **Realtime** subscription on the website + optional app change.

---

## REST / PostgREST patterns (reference)

Base: `https://<project>.ref.supabase.co/rest/v1/`

Headers for user-scoped calls:

- `apikey: <anon key>`
- `Authorization: Bearer <user_jwt>`

Examples (invigilator session):

- List exams: `GET /exams?user_id=eq.<uid>&order=created_at.desc`
- List snapshots: `GET /cheating_snapshots?exam_id=eq.<exam_id>&order=created_at.desc`

Exact filter syntax matches PostgREST; the Android client uses **supabase-kt** wrappers.

---

## Android ↔ backend integration summary

| Concern | App behavior | Website / backend |
|---------|----------------|-------------------|
| Auth | Email + password, JWT in client | Same project; optional web login |
| Exam CRUD | PostgREST with user JWT | Same, or admin tools with service role |
| QR | Scans one barcode, validates JSON `exam_id` | Display QR with JSON including `exam_id` |
| Pairing field | `linked_device_id` | Parse stored JSON for `ws_url` / tokens |
| Snapshots | Poll DB every 4s | Insert rows + public `image_url` |
| Status | `draft` → `live` → `completed` | Can read `status` to gate ingest |

---

## Optional SQL for server-side inserts (document only)

If you use the **service role** in a backend, RLS is bypassed — no change needed.

If you must stay with **anon** + custom claims, add a dedicated policy (example pattern only; adjust to your threat model):

```sql
-- Example ONLY: allow inserts from a Edge Function using a custom claim or vault secret.
-- Do not expose secrets in the mobile app.
```

Prefer **Edge Function** or **server with service role** for detection uploads.

---

## Building the Android app

- JDK 17+, Android Studio.
- Set `local.properties` as above.
- `./gradlew assembleDebug`

---

## Further reading in repo

- `DESIGN.md` — colors, typography, components for a matching web UI.
- `QrLinkContract.kt` — QR validation source of truth.
- `supabase_schema.sql` — canonical schema.
