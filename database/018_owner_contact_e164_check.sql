-- Correct the escaped '+' regex in migration 017's deployed CHECK.
alter table public.platform_owner_identity_lock
 drop constraint if exists socyto_owner_phone_e164;
alter table public.platform_owner_identity_lock
 add constraint socyto_owner_phone_e164
 check (approved_phone ~ '^[+][1-9][0-9]{7,14}$');
