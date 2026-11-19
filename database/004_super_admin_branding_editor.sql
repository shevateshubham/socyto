-- Multi-firm brand management: trusted, platform-admin-only writes.
-- Run after 003_business_branding.sql. No business administrator or client can
-- modify their own or another firm's branding through this API.
--
-- A public storage bucket contains company logos only; never invoices or site media.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'business-logos','business-logos',true, 2097152,
  array['image/png','image/jpeg','image/webp']::text[]
) on conflict (id) do update
set public = true, file_size_limit = 2097152,
    allowed_mime_types = array['image/png','image/jpeg','image/webp']::text[];

-- A storage object name is business_id/uuid.ext, so logos are easily
-- attributable and cannot overwrite another business' path.
create policy socyto_platform_upload_business_logos
on storage.objects for insert to authenticated
with check (
  bucket_id = 'business-logos'
  and public.is_platform_administrator()
  and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\\.(png|jpg|webp)$'
  and exists (
    select 1 from public.businesses b
    where b.business_id::text = split_part(name,'/',1)
  )
);

alter table public.business_branding
add column if not exists logo_storage_path text;

alter table public.business_branding
add constraint branding_logo_storage_path_valid
check (
  logo_storage_path is null or
  (
    logo_storage_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\\.(png|jpg|webp)$'
    and split_part(logo_storage_path,'/',1) = business_id::text
  )
);

-- The function checks actual auth, checks owned storage logo, and upserts a
-- brand row in a single transaction. Callers cannot spoof the current user.
create or replace function public.save_business_branding(
  target_business_id uuid,
  new_display_name text,
  new_primary_color_hex text,
  new_secondary_color_hex text,
  new_accent_color_hex text,
  new_background_color_hex text,
  new_text_color_hex text,
  new_logo_storage_path text default null
)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if not public.is_platform_administrator() then
    raise exception 'Not authorized to manage business branding'
      using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.businesses where business_id = target_business_id
  ) then
    raise exception 'Business does not exist' using errcode = '22023';
  end if;
  if new_display_name is null
     or length(trim(new_display_name)) not between 1 and 80 then
    raise exception 'Invalid display name' using errcode = '22023';
  end if;
  if new_primary_color_hex !~ '^#[0-9A-Fa-f]{6}$'
     or new_secondary_color_hex !~ '^#[0-9A-Fa-f]{6}$'
     or new_accent_color_hex !~ '^#[0-9A-Fa-f]{6}$'
     or new_background_color_hex !~ '^#[0-9A-Fa-f]{6}$'
     or new_text_color_hex !~ '^#[0-9A-Fa-f]{6}$' then
    raise exception 'Invalid six-digit hex colour' using errcode = '22023';
  end if;
  if new_logo_storage_path is not null then
    if new_logo_storage_path !~
       '^[0-9a-f-]{36}/[0-9a-f-]{36}\\.(png|jpg|webp)$'
       or split_part(new_logo_storage_path,'/',1) != target_business_id::text
       or not exists (
           select 1 from storage.objects
           where bucket_id = 'business-logos'
             and name = new_logo_storage_path
       ) then
      raise exception 'Logo must belong to the selected business'
        using errcode = '22023';
    end if;
  end if;

  insert into public.business_branding(
    business_id, display_name, logo_url, logo_storage_path,
    primary_color_hex, secondary_color_hex, accent_color_hex,
    background_color_hex, text_color_hex, updated_at
  )
  values (
    target_business_id, trim(new_display_name), null, new_logo_storage_path,
    upper(new_primary_color_hex), upper(new_secondary_color_hex),
    upper(new_accent_color_hex), upper(new_background_color_hex),
    upper(new_text_color_hex), now()
  )
  on conflict (business_id) do update set
    display_name = excluded.display_name,
    logo_url = null,
    logo_storage_path = excluded.logo_storage_path,
    primary_color_hex = excluded.primary_color_hex,
    secondary_color_hex = excluded.secondary_color_hex,
    accent_color_hex = excluded.accent_color_hex,
    background_color_hex = excluded.background_color_hex,
    text_color_hex = excluded.text_color_hex,
    updated_at = now();
end;
$$;

revoke all on function public.save_business_branding(
  uuid,text,text,text,text,text,text,text
) from public, anon;
grant execute on function public.save_business_branding(
  uuid,text,text,text,text,text,text,text
) to authenticated;

-- Existing RLS grants read only. All writes are routed through this RPC.
revoke insert, update, delete on public.business_branding from authenticated;
grant select on public.business_branding to authenticated;

-- Security note: grant ownership of this function to a trusted database owner.
-- Test that accounts in two different tenants cannot list or change each other's
-- branding, nor upload logos under another business ID.
