import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

function respond(status: number, payload: Record<string, unknown>): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
  });
}

function randomPassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return respond(405, { error: "Method not allowed." });
  if (!url || !secret || !publicKey)
    return respond(503, { error: "Onboarding service configuration is incomplete." });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return respond(401, { error: "Sign in required." });
  const token = auth.slice(7);
  const signedIn = createClient(url, publicKey, {
    auth: { autoRefreshToken: false, persistSession: false },
    global: { headers: { Authorization: "Bearer " + token } }
  });
  const { data: identity, error: authError } = await signedIn.auth.getUser(token);
  if (authError || !identity.user || !identity.user.email_confirmed_at)
    return respond(401, { error: "Sign in using a verified account." });

  const backend = createClient(url, secret, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  const { data: owner, error: roleError } = await backend.from("platform_administrators")
    .select("user_id").eq("user_id", identity.user.id).maybeSingle();
  if (roleError || !owner)
    return respond(403, { error: "Only an authorized Super Admin can create firm logins." });

  let input: Record<string, unknown>;
  try { input = await req.json(); }
  catch { return respond(400, { error: "Invalid request." }); }
  if (input.action !== "provision_firm")
    return respond(400, { error: "Unsupported action." });

  const name = typeof input.business_name === "string" ? input.business_name.trim() : "";
  const email = typeof input.administrator_email === "string"
    ? input.administrator_email.trim().toLowerCase() : "";
  const type = typeof input.business_type === "string" ? input.business_type.trim() : "";
  if (name.length < 2 || name.length > 80 || type.length < 2 || type.length > 60 ||
      email.length > 254 || !/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)) {
    return respond(400, { error: "Enter a valid firm name, type and administrator email." });
  }

  // A firm administrator is a new identity with a one-time password; never
  // reset or seize an existing Supabase identity, including a client identity.
  const password = randomPassword();
  const { data: created, error: createError } = await backend.auth.admin.createUser({
    email, password, email_confirm: true,
    app_metadata: { socyto_password_reset_required: true }
  });
  if (createError || !created.user) {
    return respond(409, {
      error: "This email could not be provisioned. If an account exists, use verified-account registration instead."
    });
  }

  // The registration RPC uses the currently authenticated Super Admin identity,
  // not service-role bypass privileges. It creates the tenant/membership/branding
  // together, transactionally.
  const { data: businessId, error: registerError } = await signedIn.rpc(
    "register_business_for_existing_user",
    {
      new_business_name: name,
      administrator_user_id: created.user.id,
      new_business_type: type
    }
  );
  if (registerError || typeof businessId !== "string") {
    const { error: rollbackError } = await backend.auth.admin.deleteUser(created.user.id);
    return respond(409, {
      error: rollbackError
        ? "Firm registration failed and needs administrator cleanup before retrying."
        : "Firm registration failed; the unused temporary login was withdrawn."
    });
  }

  // The temporary password is never inserted into app tables or logged.
  return respond(201, {
    business_id: businessId,
    email,
    temporary_password: password,
    requires_password_change: true
  });
});
