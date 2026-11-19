-- SOCYTO staging-only, read-only tenant-security audit.
-- Execute after migrations are deployed and before allowing customer data.
-- This catches missing RLS and inadvertent anonymous grants; it does not replace
-- two-firm integration tests using separate authenticated accounts.

select c.relname as table_name, c.relrowsecurity as rls_enabled,
       c.relforcerowsecurity as force_rls
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in (
    'businesses','platform_administrators','business_members',
    'clients','projects','project_requirements','material_receipts',
    'project_publications','business_subscriptions','business_branding'
  )
order by c.relname;

-- Expected: no rows (anonymous users cannot read the private tables).
select table_name,privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee = 'anon'
  and table_name in (
    'businesses','platform_administrators','business_members',
    'clients','projects','project_requirements','material_receipts',
    'project_publications','business_subscriptions','business_branding'
  )
order by table_name,privilege_type;

-- Expected: platform memberships, subscriptions and branding are read-only
-- for ordinary authenticated users. Mutations require server-side authorized RPCs.
select table_name,privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee = 'authenticated'
  and table_name in (
    'businesses','platform_administrators','business_members',
    'business_subscriptions','business_branding','project_publications'
  )
order by table_name,privilege_type;

-- Verify all policies and tenant conditions manually against two real firms.
select schemaname,tablename,policyname,cmd,roles,qual,with_check
from pg_policies where schemaname = 'public'
  and tablename in (
    'businesses','business_members','clients','projects',
    'project_requirements','material_receipts',
    'project_publications','business_branding'
  )
order by tablename,policyname;
