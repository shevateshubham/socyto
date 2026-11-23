import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function reply(status: number, payload: Record<string, unknown>) {
  return new Response(JSON.stringify(payload), {
    status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
  });
}

function temporaryPassword() {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply(405, { error: "Method not allowed." });
  if (!url || !secret || !publicKey) return reply(503, { error: "Authentication service not configured." });

  const bearer = req.headers.get("Authorization") ?? "";
  const token = bearer.startsWith("Bearer ") ? bearer.slice(7) : "";
  if (!token) return reply(401, { error: "Sign in required." });

  const userClient = createClient(url, publicKey, {
    auth: { autoRefreshToken: false, persistSession: false },
    global: { headers: { Authorization: "Bearer " + token } }
  });
  const { data: identity, error: identityError } = await userClient.auth.getUser(token);
  if (identityError || !identity.user) return reply(401, { error: "Session expired. Sign in again." });

  let body: Record<string, unknown>;
  try { body = await req.json(); }
  catch { return reply(400, { error: "Invalid request." }); }

  const admin = createClient(url, secret, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  const action = body.action;

  if (action === "complete_first_login") {
    if (identity.user.app_metadata?.socyto_password_reset_required !== true) {
      return reply(409, { error: "This account does not require first-login activation." });
    }
    const newPassword = body.new_password;
    if (typeof newPassword !== "string" || newPassword.length < 12 ||
        newPassword.length > 128 || !/[A-Za-z]/.test(newPassword) ||
        !/[0-9]/.test(newPassword)) {
      return reply(400, { error: "Choose a password of 12–128 characters containing letters and numbers." });
    }
    const { error } = await admin.auth.admin.updateUserById(identity.user.id, {
      password: newPassword,
      app_metadata: { ...identity.user.app_metadata, socyto_password_reset_required: false }
    });
    if (error) return reply(500, { error: "Password update failed; retry securely." });
    return reply(200, { success: true, message: "Password updated. Sign in with your new password." });
  }

  if (action !== "provision_client") return reply(400, { error: "Unsupported action." });
  const businessId = body.business_id;
  const clientId = body.client_id;
  if (typeof businessId !== "string" || !uuid.test(businessId) ||
      typeof clientId !== "string" || !uuid.test(clientId)) {
    return reply(400, { error: "Invalid client or business ID." });
  }

  // Validate authorization server-side: never trust business ID or role assertions from the app.
  const { data: membership, error: membershipError } = await admin.from("business_members")
    .select("membership_id")
    .eq("business_id", businessId)
    .eq("user_id", identity.user.id)
    .eq("member_role", "business_admin").maybeSingle();
  if (membershipError || !membership) return reply(403, { error: "Firm Admin access required." });

  const { data: firm, error: firmError } = await admin.from("businesses")
    .select("account_status").eq("business_id", businessId).maybeSingle();
  if (firmError || firm?.account_status !== "active")
    return reply(403, { error: "Business must be active." });

  const { data: client, error: clientError } = await admin.from("clients")
    .select("client_id,client_email,login_user_id")
    .eq("business_id", businessId).eq("client_id", clientId).maybeSingle();
  if (clientError || !client) return reply(404, { error: "Client not found in your firm." });

  const { count: projectCount, error: projectError } = await admin.from("projects")
    .select("project_id", { count: "exact", head: true })
    .eq("business_id", businessId).eq("client_id", clientId);
  if (projectError || !projectCount)
    return reply(409, { error: "Save a project for this client before issuing a login." });

  if (client.login_user_id)
    return reply(409, { error: "This client already has a login. Use account recovery; no new temporary password was issued." });

  const email = typeof client.client_email === "string" ? client.client_email.trim().toLowerCase() : "";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)
    return reply(400, { error: "A valid client email is required before issuing a login." });

  // Do not take over an existing identity or silently reset an existing user's password.
  const password = temporaryPassword();
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email, password, email_confirm: true,
    app_metadata: { socyto_password_reset_required: true }
  });
  if (createError || !created.user) {
    if (createError?.message?.toLowerCase().includes("already") ||
        createError?.code === "email_exists") {
      return reply(409, { error: "This email already has an account. Link its verified login instead; no password was changed." });
    }
    return reply(409, { error: "Unable to create a new client login. Verify the email or contact the platform administrator." });
  }

  const { data: linked, error: linkError } = await admin.from("clients")
    .update({ login_user_id: created.user.id })
    .eq("business_id", businessId).eq("client_id", clientId).is("login_user_id", null)
    .select("client_id").maybeSingle();
  if (linkError || !linked) {
    // Roll back our own new auth account to avoid an orphan login on race/failure.
    await admin.auth.admin.deleteUser(created.user.id);
    return reply(409, { error: "Client login was changed or could not be linked. Retry after refreshing." });
  }

  // Only returned once; never persisted in the clients/projects tables or system logs.
  return reply(201, {
    success: true,
    email,
    temporary_password: password,
    requires_password_change: true,
    message: "Copy and share this temporary password privately. It will not be displayed again."
  });
});
