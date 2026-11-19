-- Protect internal RLS predicates from anonymous API callers.
-- Supabase default function privileges may grant anon EXECUTE even when PUBLIC was revoked.
revoke execute on function public.is_platform_administrator() from anon, public;
revoke execute on function public.is_business_administrator(uuid) from anon, public;
revoke execute on function public.is_project_client(uuid,uuid) from anon, public;
grant execute on function public.is_platform_administrator() to authenticated;
grant execute on function public.is_business_administrator(uuid) to authenticated;
grant execute on function public.is_project_client(uuid,uuid) to authenticated;
