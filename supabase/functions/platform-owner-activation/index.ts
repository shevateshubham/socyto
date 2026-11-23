import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const headers = {
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS"
};
const reply = (status: number, payload: Record<string, unknown>) =>
  new Response(JSON.stringify(payload), { status, headers });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.method !== "POST") return reply(405, { error: "Method not supported." });
  if (!url || !serviceKey || !publicKey) return reply(503, { error: "Activation is not configured." });
  const bearer = req.headers.get("Authorization") ?? "";
  if (!bearer.startsWith("Bearer ")) return reply(401, { error: "Sign in first." });

  let body: Record<string, unknown>;
  try {
    const raw = await req.text();
    if (!raw || raw.length > 512) return reply(400, { error: "Invalid activation request." });
    body = JSON.parse(raw);
  } catch { return reply(400, { error: "Invalid activation request." }); }
  if (body.action !== "activate") return reply(400, { error: "Unsupported action. Use verified owner activation." });

  const auth = createClient(url, publicKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: identity, error: identityError } = await auth.auth.getUser(bearer.slice(7));
  const user = identity?.user;
  if (identityError || !user) return reply(401, { error: "Sign in again." });
  if (!user.email || !user.email_confirmed_at) return reply(403, { error: "Verify your signup email first." });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: config, error: configError } = await admin.from("platform_owner_bootstrap_config")
    .select("approved_email").eq("singleton", true).maybeSingle();
  if (configError || !config) return reply(503, { error: "Owner configuration unavailable." });
  if (user.email.toLowerCase() !== String(config.approved_email).toLowerCase())
    return reply(403, { error: "This email is not approved for Primary Owner access." });

  const { data: existing, error: existingError } = await admin.from("platform_administrators")
    .select("user_id").eq("user_id", user.id).maybeSingle();
  if (existingError) return reply(503, { error: "Unable to check activation status." });
  if (existing) return reply(200, { activated: true, already_activated: true });

  // Privileged RPCs check the permanent allowlist, verified signup,
  // exclusive owner-user lock and one-time authorization consumption.
  const bootstrap = await admin.rpc("bootstrap_verified_platform_owner", { target_user_id: user.id });
  if (bootstrap.error || bootstrap.data !== true)
    return reply(403, { error: "Approved owner identity could not be reserved." });
  const activation = await admin.rpc("activate_verified_primary_owner", { target_user_id: user.id });
  if (activation.error || activation.data !== true)
    return reply(403, { error: "Verified owner activation was not authorized." });
  return reply(200, { activated: true, already_activated: false });
});