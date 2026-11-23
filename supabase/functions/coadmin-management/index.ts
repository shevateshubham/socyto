import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const json = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const response = (status: number, payload: Record<string, unknown>) =>
  new Response(JSON.stringify(payload), { status, headers: json });

function normalizeContact(value: unknown) {
  if (typeof value !== "string") return null;
  const entry = value.trim();
  if (entry.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(entry)) {
    return { method: "email" as const, value: entry.toLowerCase() };
  }
  const digits = /^[0-9]{10}$/.test(entry) ? "+91" + entry : entry;
  if (/^\+[1-9][0-9]{7,14}$/.test(digits)) {
    return { method: "phone" as const, value: digits };
  }
  return null;
}

async function randomCode() {
  const random = crypto.getRandomValues(new Uint8Array(32));
  const code = Array.from(random, b => b.toString(16).padStart(2, "0")).join("");
  const digestBuffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(code));
  const digest = Array.from(new Uint8Array(digestBuffer), b => b.toString(16).padStart(2, "0")).join("");
  return { code, digest };
}

Deno.serve(async req => {
  if (req.method !== "POST") return response(405, { error: "Method not supported." });
  if (!url || !serviceKey || !publicKey) return response(503, { error: "Service unavailable." });
  const header = req.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return response(401, { error: "Sign in required." });
  if (Number(req.headers.get("content-length") ?? 0) > 4096) return response(413, { error: "Request too large." });

  const userAuth = createClient(url, publicKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: verified, error: authError } = await userAuth.auth.getUser(header.slice(7));
  if (authError || !verified.user) return response(401, { error: "Invalid session." });
  const caller = verified.user;
  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const { data: lock, error: ownerError } = await admin.from("platform_owner_identity_lock")
    .select("owner_user_id").eq("singleton", true).maybeSingle();
  if (ownerError || !lock) return response(503, { error: "Owner configuration unavailable." });
  if (lock.owner_user_id !== caller.id) return response(403, { error: "Primary owner access required." });

  const { data: ownerAdmin, error: roleError } = await admin.from("platform_administrators")
    .select("user_id").eq("user_id", caller.id).maybeSingle();
  if (roleError || !ownerAdmin) return response(403, { error: "Primary owner access required." });

  let body: Record<string, unknown>;
  try { body = await req.json(); }
  catch { return response(400, { error: "Invalid request." }); }
  const action = body.action;
  if (action === "list") {
    const [members, invites] = await Promise.all([
      admin.from("platform_coadmins")
        .select("user_id,contact_method,contact_value,active,created_at,revoked_at")
        .order("created_at", { ascending: false }),
      admin.from("platform_coadmin_invitations")
        .select("invitation_id,contact_method,contact_value,created_at,expires_at,claimed_at,revoked_at")
        .order("created_at", { ascending: false }).limit(100)
    ]);
    if (members.error || invites.error) return response(503, { error: "Unable to load administrators." });
    return response(200, { admins: members.data, invitations: invites.data });
  }

  if (action === "invite") {
    const contact = normalizeContact(body.contact);
    if (!contact) return response(400, { error: "Enter a valid mobile number or email address." });
    const [members, invites] = await Promise.all([
      admin.from("platform_coadmins").select("user_id")
        .eq("contact_method", contact.method).eq("contact_value", contact.value)
        .eq("active", true).limit(1),
      admin.from("platform_coadmin_invitations")
        .select("invitation_id,expires_at")
        .eq("contact_method", contact.method).eq("contact_value", contact.value)
        .is("claimed_at", null).is("revoked_at", null).limit(1)
    ]);
    if (members.error || invites.error) return response(503, { error: "Unable to check administrator." });
    if (members.data?.length) return response(409, { error: "This contact already belongs to an active Co-Admin." });
    const current = invites.data?.[0];
    if (current && Date.parse(current.expires_at) > Date.now())
      return response(409, { error: "An invitation is still active for this contact. No new code was issued." });
    if (current) {
      const retired = await admin.from("platform_coadmin_invitations")
        .update({ revoked_at: new Date().toISOString() })
        .eq("invitation_id", current.invitation_id).is("claimed_at", null).is("revoked_at", null);
      if (retired.error) return response(503, { error: "Unable to retire an expired invitation." });
    }
    const { code, digest } = await randomCode();
    const created = await admin.from("platform_coadmin_invitations")
      .insert({
        invited_by: caller.id, contact_method: contact.method, contact_value: contact.value,
        token_digest: digest, expires_at: new Date(Date.now() + 24 * 60 * 60_000).toISOString()
      }).select("invitation_id,expires_at").single();
    if (created.error || !created.data)
      return response(409, { error: "An invitation already exists; no new code was issued." });
    // Only the verified Primary Super Admin sees this secret, exactly once.
    // They share it privately with the nominated contact. No email/SMS delivery
    // is claimed and no secret is stored in plaintext.
    return response(201, { invitation_id: created.data.invitation_id,
      expires_at: created.data.expires_at, one_time_code: code });
  }

  if (action === "revoke") {
    const id = body.user_id;
    if (typeof id !== "string" || !/^[0-9a-f-]{36}$/i.test(id) || id === caller.id)
      return response(400, { error: "Invalid Co-Admin identity." });
    const changed = await admin.from("platform_coadmins")
      .update({ active: false, revoked_at: new Date().toISOString() })
      .eq("user_id", id).eq("active", true).select("user_id").maybeSingle();
    if (changed.error || !changed.data) return response(404, { error: "Active Co-Admin not found." });
    return response(200, { success: true });
  }
  if (action === "cancel_invitation") {
    const id = body.invitation_id;
    if (typeof id !== "string" || !/^[0-9a-f-]{36}$/i.test(id)) return response(400, { error: "Invalid invitation." });
    const changed = await admin.from("platform_coadmin_invitations")
      .update({ revoked_at: new Date().toISOString() })
      .eq("invitation_id", id).is("claimed_at", null).is("revoked_at", null)
      .select("invitation_id").maybeSingle();
    if (changed.error || !changed.data) return response(404, { error: "Open invitation not found." });
    return response(200, { success: true });
  }
  return response(400, { error: "Unsupported action." });
});
