import { createClient } from "npm:@supabase/supabase-js@2.75.0";

// This endpoint has no mailer credentials, OTP delivery or code generation.
// A user first verifies their email through Supabase Auth. Only a separate,
// explicit in-app action can consume the preapproved Primary Owner authorization.
const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const reply = (status: number, error: string) =>
  new Response(JSON.stringify({ error }), { status, headers });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply(405, "Method not supported.");
  if (!url || !serviceKey || !publicKey)
    return reply(503, "Owner activation is temporarily unavailable.");
  const bearer = req.headers.get("Authorization") ?? "";
  if (!bearer.startsWith("Bearer ")) return reply(401, "Sign in first.");

  const auth = createClient(url, publicKey, {
    auth: { persistSession: false, autoRefreshToken: false }
  });
  const { data, error } = await auth.auth.getUser(bearer.slice(7));
  if (error || !data.user) return reply(401, "Your session expired.");
  const user = data.user;
  if (!user.email || !user.email_confirmed_at)
    return reply(403, "Verify your email address before activating.");

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false }
  });

  // This function checks the private allowlist and creates an immutable lock
  // for the exact verified Auth user after a zero-record fresh start. It does
  // NOT grant a role and can only be called by service_role.
  const bootstrap = await admin.rpc("bootstrap_verified_platform_owner", {
    target_user_id: user.id
  });
  if (bootstrap.error || bootstrap.data !== true)
    return reply(403, "This account is not approved for platform ownership.");

  // Atomic SQL: verify owner email & immutable user lock, consume the single
  // owner authorization, and grant one Primary Admin role. No emailed code.
  const activation = await admin.rpc("activate_verified_primary_owner", {
    target_user_id: user.id
  });
  if (activation.error || activation.data !== true)
    return reply(403, "Owner activation was not authorized. Verify the approved email.");

  return new Response(JSON.stringify({ activated: true }), { status: 200, headers });
});
