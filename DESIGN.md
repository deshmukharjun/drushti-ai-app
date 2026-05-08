# DrushtiAI — design system (app + web alignment)

Use this when building the companion website so it feels like the same product as the Android app.

---

## Brand

- **Primary / logo navy:** `#000F2E` (`drushti_navy`)
- **Navy accent (surfaces):** `#1A2B52` (`drushti_navy_light`)
- **Neutral gray (secondary text):** `#8D9097` (`drushti_gray`)

The status bar and navigation bar use **navy** with **light icons** (not light status bar).

---

## Surfaces & text

| Token | Hex | Usage |
|-------|-----|--------|
| `drushti_surface` | `#FFFFFF` | Page background |
| `drushti_surface_muted` | `#F4F6FA` | Subtle fills, avatar circles |
| `drushti_surface_card` | `#EEF1F8` | Chips, nested cards, soft panels |
| `drushti_on_surface` | `#0F1729` | Primary body text |
| `drushti_on_surface_muted` | `#5C6578` | Secondary labels, hints |
| `drushti_hint` | `#5C6578` | Input hints |
| `drushti_outline` | `#94A0B8` | Borders, dividers |
| `drushti_outline_strong` | `#000F2E` | Strong focus / emphasis borders |

---

## Framework

- **Material Design 3** (Light, `NoActionBar`).
- **Forced light mode** in application class (`MODE_NIGHT_NO`) so the navy/white scheme stays consistent.
- **Corner radius:** primary buttons and many cards use **12dp**; larger summary cards **16dp**.
- **Elevation:** cards typically **2–4dp**; bottom nav **8dp**.

---

## Components

### Buttons

- **Filled:** navy background, white label (`Widget.DrushtiAI.Button`).
- **Outlined:** navy stroke + navy text (`materialButtonOutlinedStyle` overlay).

### Text fields

- Outlined Material text fields with **navy** stroke/hint treatment (`Widget.DrushtiAI.TextInputLayout` / `TextInputEditText`).
- Password / icon end tints align with navy on light surfaces.

### Toolbars

- Light surface background, **navy** title text.
- Back arrow asset tinted **navy** on light screens; **white** on dark overlays (e.g. fullscreen snapshot viewer).

### Cards

- `MaterialCardView` for exam summaries, alerts, snapshot tiles.
- Snapshot grid: **2 columns**, image ~120dp height, label single line ellipsized.

### FAB

- Primary actions: **navy** background, white icon (e.g. home “new exam”).
- Destructive FAB (delete snapshot): **red** `#C62828`.

### Bottom navigation

- Icons and labels use **selector**: checked = navy, default = muted gray.

---

## Motion & density

- Surveillance and lists rely on **RecyclerView** + Glide for images; no custom motion spec documented.
- Prefer **comfortable padding** (16–20dp screen edges, 12–18dp inside cards).

---

## Copy tone

- Short, operational (“Start exam”, “Link camera”, “Drushti AI model connected”).
- Empty states are slightly friendly (“The hall is quiet—no invigilations yet…”).

---

## Web implementation notes

- Map CSS variables to the hex values above.
- Prefer a **distinct display font** for marketing pages if needed, but keep **dashboard UI** clean and close to Material (Roboto / system UI is acceptable).
- Match **navy header bar** on web dashboards if you use a top app bar.
- QR display page: high contrast, large quiet zone, label the exam subject + date for the invigilator.

---

## Assets

- App icons under `mipmap-*`; vector icons under `res/drawable` (e.g. `ic_arrow_back`, `ic_person`, `ic_close_alert`).
- Website can reuse palette and corner radii without copying proprietary logo files unless licensed.
