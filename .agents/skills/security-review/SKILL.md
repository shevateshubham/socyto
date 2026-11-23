---
name: security-review
description: Review SOCYTO authentication, Supabase row-level access and protection of client/project data.
---
# Security Review Skill
1. Read SECURITY.md. Inspect actual backend configuration, schema, policies and API paths.
2. Verify every table and storage resource exposed to a client is protected by server-side authorization.
3. Attempt cross-client reads and writes using direct APIs, not just screen navigation.
4. Verify admin edits, expense access, and client publication boundaries.
5. Do not put privileged keys in app, screenshots, logging or the repository.
6. Review JWT role source, update USING/WITH CHECK and publication audit.
7. Record verified tests separately from unchecked assumptions. Do not mark security complete solely from documentation.
