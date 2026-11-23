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
  const auth = createClient(url, publicKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: session, error: identityError } = await auth.auth.getUser(bearer.slice(7));
  if (identityError || !session.user) return reply(401, { error: "Sign in again." });
  const user = session.user;
  const email = user.email?.toLowerCase() ?? "";
  if (!email || !user.email_confirmed_at) return reply(403, { error: "Verify your email before activation." });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const [{ data: config }, { data: lock }, { data: existing }] = await Promise.all([
    admin.from("platform_owner_bootstrap_config").select("approved_email").eq("singleton", true).maybeSingle(),
    admin.from("platform_owner_identity_lock").select("owner_user_id").eq("singleton", true).maybeSingle(),
    admin.from("platform_administrators").select("user_id").eq("user_id", user.id).maybeSingle()
  ]);
  if (!config || config.approved_email !== email || (lock && lock.owner_user_id !== user.id))
    return reply(403, { error: "Not an authorized owner." });
  if (existing) return reply(200, { activated: true, already_activated: true });

  let body: Record<string, unknown> = {};
  try {
    const raw = await req.text();
    if (raw.length > 1024) return reply(400, { error: "Invalid request." });
    if (raw.trim()) body = JSON.parse(raw);
  } catch { return reply(400, { error: "Invalid request." }); }

  if (body.action === "request_code") {
    const { data: previous, error: checkError } = await admin.from("platform_owner_activation_challenges")
      .select("requested_at,expires_at,consumed_at,attempts").eq("user_id", user.id).maybeSingle();
    if (checkError) return reply(503, { error: "Activation unavailable." });
    const now = Date.now();
    if (previous && !previous.consumed_at && Date.parse(previous.expires_at) > now)
      return reply(409, { error: "A code request is already pending. Check your email; a new code was not generated." });
    // Explicit requests only, no automatic resends. Minimum delay between requests.
    if (previous && now - Date.parse(previous.requested_at) < 15 * 60_000)
      return reply(429, { error: "Please use your previously requested code or try again later." });
    const { error: gateError } = await admin.from("platform_owner_activation_challenges")
      .upsert({ user_id: user.id, requested_at: new Date().toISOString(),
        expires_at: new Date(now + 10 * 60_000).toISOString(),
        verified_at: null, attempts: 0, consumed_at: null }, { onConflict: "user_id" });
    if (gateError) return reply(503, { error: "Unable to reserve activation request." });
    // Supabase Auth is responsible for generating, storing and emailing the one-time secret.
    const { error: mailError } = await auth.auth.signInWithOtp({
      email, options: { shouldCreateUser: false }
    });
    if (mailError) {
      await admin.from("platform_owner_activation_challenges")
        .delete().eq("user_id", user.id).is("verified_at", null);
      return reply(503, { error: "Could not send activation email. Check email provider configuration." });
    }
    return reply(200, { code_requested: true, expires_in_seconds: 600,
      message: "Check your approved email for the one-time code. Do not request it again." });
  }

  if (body.action === "verify_code") {
    const code = body.code;
    if (typeof code !== "string" || !/^[0-9]{6,8}$/.test(code))
      return reply(400, { error: "Enter the activation code sent to your email." });
    const { data: challenge } = await admin.from("platform_owner_activation_challenges")
      .select("expires_at,verified_at,consumed_at,attempts").eq("user_id", user.id).maybeSingle();
    if (!challenge || challenge.consumed_at || Date.parse(challenge.expires_at) <= Date.now())
      return reply(409, { error: "No valid activation request. Request a code explicitly." });
    if (challenge.attempts >= 5) return reply(429, { error: "Too many attempts." });
    // An invalid attempt is charged BEFORE checking the code.
    const { data: charged, error: chargeError } = await admin.from("platform_owner_activation_challenges")
      .update({ attempts: challenge.attempts + 1 })
      .eq("user_id", user.id).eq("attempts", challenge.attempts)
      .is("consumed_at", null).select("user_id").maybeSingle();
    if (chargeError || !charged) return reply(409, { error: "Request changed; retry." });
    const { data: verified, error: otpError } = await auth.auth.verifyOtp({ email, token: code, type: "email" });
    if (otpError || verified.user?.id !== user.id)
      return reply(403, { error: "Invalid or expired activation code." });
    const { error: markError } = await admin.from("platform_owner_activation_challenges")
      .update({ verified_at: new Date().toISOString() })
      .eq("user_id", user.id).is("verified_at", null).is("consumed_at", null);
    if (markError) return reply(503, { error: "Unable to confirm activation." });
    const bootstrap = await admin.rpc("bootstrap_verified_platform_owner", { target_user_id: user.id });
    if (bootstrap.error || bootstrap.data !== true) return reply(403, { error: "Owner identity cannot be activated." });
    const activation = await admin.rpc("activate_verified_primary_owner", { target_user_id: user.id });
    if (activation.error || activation.data !== true) return reply(403, { error: "Activation not authorized." });
    return reply(200, { activated: true });
  }
  return reply(409, { activated: false, code_required: true,
    message: "Request your one-time activation code explicitly." });
});