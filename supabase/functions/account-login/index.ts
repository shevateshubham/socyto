import { createClient } from "npm:@supabase/supabase-js@2.75.0";

// Phone/password sign-in is resolved server-side. Only a correct password can
// produce an Auth session. User existence and account roles are never disclosed.
const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const phonePattern = /^\+[1-9][0-9]{7,14}$/;
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const invalid = () => new Response(
  JSON.stringify({ error: "Invalid mobile number or password." }),
  { status: 401, headers }
);
Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return new Response(null, { status: 405, headers });
  if (!url || !serviceKey || !anonKey) {
    return new Response(JSON.stringify({ error: "Sign-in is temporarily unavailable." }),
      { status: 503, headers });
  }

  let body: Record<string, unknown>;
  try {
    if (Number(req.headers.get("content-length") ?? "0") > 8192) return invalid();
    body = await req.json();
  } catch { return invalid(); }

  const phone = typeof body.phone === "string" ? body.phone.trim() : "";
  const password = body.password;
  if (!phonePattern.test(phone) || typeof password !== "string" ||
      password.length < 1 || password.length > 128) return invalid();

  const data = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  const auth = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  // Managed firm/client accounts use internal synthetic Auth emails, but only
  // the server can resolve their E.164 phone to the account ID. The public app
  // never queries privileged tables to find an identity.
  const [firm, client] = await Promise.all([
    data.from("businesses").select("firm_code")
      .eq("contact_phone", phone).maybeSingle(),
    data.from("clients").select("client_code")
      .eq("client_phone", phone).maybeSingle()
  ]);
  if (firm.error || client.error) {
    return new Response(JSON.stringify({ error: "Sign-in is temporarily unavailable." }),
      { status: 503, headers });
  }
  if (firm.data?.firm_code && client.data?.client_code) {
    // Ambiguous phone identities must never log in to the wrong tenant.
    return invalid();
  }
  const code = firm.data?.firm_code ?? client.data?.client_code;
  const credentials = code
    ? { email: String(code).toLowerCase() + "@login.socyto.invalid", password }
    : { phone, password };
  const { data: session, error } = await auth.auth.signInWithPassword(credentials);
  if (error || !session.session || !session.user) return invalid();

  return new Response(JSON.stringify({
    access_token: session.session.access_token,
    refresh_token: session.session.refresh_token,
    expires_in: session.session.expires_in,
    user: { id: session.user.id, app_metadata: session.user.app_metadata }
  }), { status: 200, headers });
});
