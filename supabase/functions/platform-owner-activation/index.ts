import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const jsonHeaders = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const reply = (status: number, error: string) =>
  new Response(JSON.stringify({ error }), { status, headers: jsonHeaders });

function codeAndDigest(): Promise<{ code: string; digest: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const code = Array.from(bytes, x => x.toString(16).padStart(2, "0")).join("");
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(code))
    .then(buffer => ({
      code, digest: Array.from(new Uint8Array(buffer),
        x => x.toString(16).padStart(2, "0")).join("")
    }));
}

async function deliverEmail(to: string, code: string): Promise<boolean> {
  const apiKey = Deno.env.get("SOCYTO_ACTIVATION_EMAIL_API_KEY");
  const from = Deno.env.get("SOCYTO_ACTIVATION_EMAIL_FROM");
  if (!apiKey || !from) return false;
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { "Authorization": "Bearer " + apiKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      from, to: [to], subject: "Your SOCYTO Super Admin activation code",
      text: "Your one-time SOCYTO activation code is: " + code +
        "\nIt expires in 30 minutes. Do not share it."
    })
  });
  return response.ok;
}

async function deliverSms(to: string, code: string): Promise<boolean> {
  const sid = Deno.env.get("SOCYTO_ACTIVATION_TWILIO_SID");
  const token = Deno.env.get("SOCYTO_ACTIVATION_TWILIO_TOKEN");
  const from = Deno.env.get("SOCYTO_ACTIVATION_TWILIO_FROM");
  if (!sid || !token || !from) return false;
  const body = new URLSearchParams({ To: to, From: from,
    Body: "SOCYTO Super Admin code: " + code + " (valid 30 minutes). Never share it." });
  const response = await fetch(
    "https://api.twilio.com/2010-04-01/Accounts/" + encodeURIComponent(sid) + "/Messages.json", {
      method: "POST",
      headers: { "Authorization": "Basic " + btoa(sid + ":" + token),
        "Content-Type": "application/x-www-form-urlencoded" },
      body
    });
  return response.ok;
}

Deno.serve(async req => {
  if (req.method !== "POST") return reply(405, "Unsupported request.");
  if (!url || !serviceKey || !anonKey) return reply(503, "Activation is unavailable.");
  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return reply(401, "Sign in required.");
  const jwt = authHeader.slice(7);
  const verifier = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  const { data: identity, error: authError } = await verifier.auth.getUser(jwt);
  if (authError || !identity.user) return reply(401, "Your session expired.");
  const user = identity.user;
  const possible = [
    ...(user.email_confirmed_at && user.email ? [{
      method: "email", recipient: user.email.trim().toLowerCase()
    }] : []),
    ...(user.phone_confirmed_at && user.phone ? [{
      method: "phone", recipient: user.phone.startsWith("+") ? user.phone : "+" + user.phone
    }] : [])
  ];
  if (possible.length === 0) return reply(403, "Verify your email or mobile first.");

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  // First setup after a deliberate zero-record reset: establish the same
  // immutable owner identity from private configuration, but only for a user
  // who has actually verified an approved email or phone. This does not
  // generate an invitation and cannot be called by the public client.
  const { data: bootstrapped, error: bootstrapError } = await admin.rpc(
    "bootstrap_verified_platform_owner", { target_user_id: user.id }
  );
  if (bootstrapError || bootstrapped !== true)
    return reply(403, "This verified account is not approved for platform ownership.");

  // Permanent platform-owner identity lock: matching a contact alone is
  // insufficient. Only this verified Auth user can request any invitation.
  const { data: lock, error: lockError } = await admin
    .from("platform_owner_identity_lock")
    .select("owner_user_id,approved_email,approved_phone")
    .eq("singleton", true).maybeSingle();
  if (lockError || !lock) return reply(503, "Platform owner configuration is unavailable.");
  if (lock.owner_user_id !== user.id) {
    return reply(403, "This account is not authorized as platform owner.");
  }
  const matchingContacts = possible.filter(c =>
    (c.method === "email" && c.recipient === lock.approved_email) ||
    (c.method === "phone" && c.recipient === lock.approved_phone));
  if (matchingContacts.length === 0) {
    return reply(403, "Verify your approved platform-owner email or mobile first.");
  }

  // Already-active platform owners never need another bootstrap code.
  const { data: existingAdmin, error: existingAdminError } = await admin
    .from("platform_administrators").select("user_id")
    .eq("user_id", user.id).maybeSingle();
  if (existingAdminError) return reply(503, "Activation is unavailable.");
  if (existingAdmin) return reply(409, "This account already has Super Admin access.");

  // No automatic reissue. An outstanding unexpired code stays valid until it is
  // used or expires; pressing Request again never replaces or resends it.
  const { data: existingInvite, error: existingInviteError } = await admin
    .from("platform_admin_bootstrap_invites")
    .select("invitation_id,expires_at")
    .eq("intended_user_id", user.id)
    .is("claimed_at", null).maybeSingle();
  if (existingInviteError) return reply(503, "Activation is unavailable.");
  if (existingInvite && Date.parse(existingInvite.expires_at) > Date.now()) {
    return reply(409, "An activation code is already active. Use the code sent earlier or request another after it expires.");
  }
  let approved: { authorization_id: string; last_sent_at: string | null } | null = null;
  let method = "";
  let recipient = "";
  for (const contact of matchingContacts) {
    const { data, error } = await admin.from("platform_owner_authorizations")
      .select("authorization_id,last_sent_at")
      .eq("contact_method", contact.method).eq("contact_value", contact.recipient)
      .is("consumed_at", null).maybeSingle();
    if (error) return reply(503, "Activation is unavailable.");
    if (data) {
      const available = contact.method === "email"
        ? !!Deno.env.get("SOCYTO_ACTIVATION_EMAIL_API_KEY") &&
          !!Deno.env.get("SOCYTO_ACTIVATION_EMAIL_FROM")
        : !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_SID") &&
          !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_TOKEN") &&
          !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_FROM");
      if (available) {
        approved = data; method = contact.method; recipient = contact.recipient; break;
      }
      if (!approved) {
        approved = data; method = contact.method; recipient = contact.recipient;
      }
    }
  }
  if (!approved) return reply(403, "Platform-owner approval is required.");
  const providerAvailable = method === "email"
    ? !!Deno.env.get("SOCYTO_ACTIVATION_EMAIL_API_KEY") &&
      !!Deno.env.get("SOCYTO_ACTIVATION_EMAIL_FROM")
    : !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_SID") &&
      !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_TOKEN") &&
      !!Deno.env.get("SOCYTO_ACTIVATION_TWILIO_FROM");
  if (!providerAvailable) return reply(503, "Activation message delivery is not configured.");
  const limit = new Date(Date.now() - 15 * 60_000).toISOString();
  const { data: reserved, error: reserveError } = await admin.from("platform_owner_authorizations")
    .update({ last_sent_at: new Date().toISOString() })
    .eq("authorization_id", approved.authorization_id)
    .is("consumed_at", null)
    .or("last_sent_at.is.null,last_sent_at.lt." + limit)
    .select("authorization_id").maybeSingle();
  if (reserveError) return reply(503, "Activation is unavailable.");
  if (!reserved) return reply(429, "A code was requested recently. Try again later.");

  // An expired code is retired only following this explicit request; no
  // scheduled job or app sign-in generates a replacement.
  if (existingInvite) {
    const { error: deleteError } = await admin
      .from("platform_admin_bootstrap_invites")
      .delete().eq("invitation_id", existingInvite.invitation_id)
      .eq("intended_user_id", user.id).is("claimed_at", null);
    if (deleteError) return reply(503, "Activation is unavailable.");
  }

  const { code, digest } = await codeAndDigest();
  const { data: invitation, error: inviteError } =
    await admin.from("platform_admin_bootstrap_invites")
      .insert({
        token_digest: digest, intended_user_id: user.id,
        expires_at: new Date(Date.now() + 30 * 60_000).toISOString()
      }).select("invitation_id").single();
  if (inviteError || !invitation) {
    // Unique unclaimed-invite index rejects a concurrent request.
    return reply(409, "An activation request is already pending. Try the existing code.");
  }

  let delivered = false;
  try {
    delivered = method === "email"
      ? await deliverEmail(recipient, code) : await deliverSms(recipient, code);
  } catch { delivered = false; }
  if (!delivered) {
    await admin.from("platform_admin_bootstrap_invites")
      .delete().eq("invitation_id", invitation.invitation_id);
    await admin.from("platform_owner_authorizations")
      .update({ last_sent_at: null }).eq("authorization_id", approved.authorization_id);
    return reply(503, "Activation message delivery failed. Try again later.");
  }
  return new Response(JSON.stringify({
    success: true, delivery: method, expires_in_minutes: 30
  }), { status: 200, headers: jsonHeaders });
});
