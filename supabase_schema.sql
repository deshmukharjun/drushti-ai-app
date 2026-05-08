-- REQUIRED SETUP (one time per Supabase project)
-- Dashboard → SQL → New query → paste this entire file → Run.
--
-- Without this script you will see errors like:
--   "Could not find the table 'public.profiles' in the schema cache"
-- The DrushtiAI app expects public.profiles, public.exams, and public.cheating_snapshots.
--
-- After success: Table Editor should list those tables. Policies are dropped and recreated so this file is safe to re-run.

create table if not exists public.profiles (
  id uuid primary key references auth.users on delete cascade,
  full_name text,
  updated_at timestamptz not null default now()
);

create table if not exists public.exams (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users on delete cascade,
  subject text not null,
  exam_date date not null,
  exam_time text not null,
  student_count int not null default 0,
  room_notes text,
  status text not null default 'draft',
  camera_connected boolean not null default false,
  linked_device_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.cheating_snapshots (
  id uuid primary key default gen_random_uuid(),
  exam_id uuid not null references public.exams on delete cascade,
  image_url text not null,
  label text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.exams enable row level security;
alter table public.cheating_snapshots enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;

drop policy if exists "exams_select_own" on public.exams;
drop policy if exists "exams_insert_own" on public.exams;
drop policy if exists "exams_update_own" on public.exams;
drop policy if exists "exams_delete_own" on public.exams;

drop policy if exists "snapshots_select_own" on public.cheating_snapshots;
drop policy if exists "snapshots_insert_own" on public.cheating_snapshots;
drop policy if exists "snapshots_delete_own" on public.cheating_snapshots;

create policy "profiles_select_own" on public.profiles for select using (auth.uid() = id);
create policy "profiles_update_own" on public.profiles for update using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles for insert with check (auth.uid() = id);

create policy "exams_select_own" on public.exams for select using (auth.uid() = user_id);
create policy "exams_insert_own" on public.exams for insert with check (auth.uid() = user_id);
create policy "exams_update_own" on public.exams for update using (auth.uid() = user_id);
create policy "exams_delete_own" on public.exams for delete using (auth.uid() = user_id);

create policy "snapshots_select_own" on public.cheating_snapshots for select using (
  exists (select 1 from public.exams e where e.id = exam_id and e.user_id = auth.uid())
);
create policy "snapshots_insert_own" on public.cheating_snapshots for insert with check (
  exists (select 1 from public.exams e where e.id = exam_id and e.user_id = auth.uid())
);
create policy "snapshots_delete_own" on public.cheating_snapshots for delete using (
  exists (select 1 from public.exams e where e.id = exam_id and e.user_id = auth.uid())
);

-- Profiles row: full_name comes from sign-up metadata (app sends user_metadata.full_name).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  perform set_config('row_security', 'off', true);
  insert into public.profiles (id, full_name)
  values (
    new.id,
    nullif(trim(coalesce(new.raw_user_meta_data->>'full_name', '')), '')
  )
  on conflict (id) do update set
    full_name = coalesce(
      nullif(trim(full_name), ''),
      excluded.full_name
    ),
    updated_at = now();
  return new;
end;
$$;

-- Optional one-time backfill for accounts created before the app sent full_name:
-- update public.profiles p
-- set full_name = coalesce(nullif(trim(p.full_name), ''), nullif(trim(u.raw_user_meta_data->>'full_name'), ''))
-- from auth.users u
-- where u.id = p.id;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create index if not exists exams_user_id_created_at_idx on public.exams (user_id, created_at desc);
create index if not exists cheating_snapshots_exam_id_idx on public.cheating_snapshots (exam_id, created_at desc);
