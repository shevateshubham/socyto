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


/** Codes are display identifiers only. UUIDs and verified membership remain authority. */
function newAccountCode(prefix: "F" | "C") {
  const random = new Uint32Array(1);
  crypto.getRandomValues(random);
  return prefix + (random[0] % 1_000_000_000).toString().padStart(9, "0");
}
function validPhone(value: unknown): value is string {
  return typeof value === "string" && /^[+][1-9][0-9]{7,14}$/.test(value);
}
function validEmail(s: unknown): s is string { return typeof s === "string" && s.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim()); }
function validName(value: unknown): value is string {
  return typeof value === "string" && value.trim().length >= 2 && value.trim().length <= 80;
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


  if (action === "provision_firm" || action === "provision_client_account") {
    const isFirm = action === "provision_firm";
    const name = isFirm ? body.firm_name : body.client_name;
    const contact = body.contact ?? body.phone;
    if (!validName(name) || !(validPhone(contact) || validEmail(contact))) {
      return reply(400, { error: "Provide a name and a valid mobile number or email address." });
    }
    const phone = validPhone(contact) ? contact.trim() : null;
    const contactEmail = validEmail(contact) ? contact.trim().toLowerCase() : null;
    let firmId: string | undefined;
    if (isFirm) {
      // Operational Co-Admins can provision firms; they cannot manage other
      // administrators or alter the primary platform owner.
      const [{ data: platformAdmin, error: ownerError },
             { data: coAdmin, error: coError }] = await Promise.all([
        admin.from("platform_administrators").select("user_id")
          .eq("user_id", identity.user.id).maybeSingle(),
        admin.from("platform_coadmins").select("user_id")
          .eq("user_id", identity.user.id).eq("active", true).maybeSingle()
      ]);
      if (ownerError || coError || (!platformAdmin && !coAdmin))
        return reply(403, { error: "Firm management permission required." });
    } else {
      if (typeof body.business_id !== "string" || !uuid.test(body.business_id)) {
        return reply(400, { error: "Invalid firm." });
      }
      firmId = body.business_id;
      const { data: member, error } = await admin.from("business_members")
        .select("membership_id").eq("business_id", firmId)
        .eq("user_id", identity.user.id).eq("member_role", "business_admin").maybeSingle();
      if (error || !member) return reply(403, { error: "Firm Admin access required." });
      const { data: firm, error: firmError } = await admin.from("businesses")
        .select("account_status").eq("business_id", firmId).maybeSingle();
      if (firmError || firm?.account_status !== "active") {
        return reply(403, { error: "Firm is not active." });
      }
    }
    // Every account gets its own Auth identity. Reused phone numbers are rejected
    // by Auth, never silently connected to a pre-existing user.
    for (let attempt = 0; attempt < 6; attempt++) {
      const code = newAccountCode(isFirm ? "F" : "C");
      const email = contactEmail ?? code.toLowerCase() + "@login.socyto.invalid";
      const password = temporaryPassword();
      let createdUser: { id: string } | null = null;
      let createError: Error | null = null;
      if (contactEmail) {
        // Public Auth signup requests the real verification email. Admin
        // createUser() alone does not guarantee a confirmation was dispatched.
        const publicAuth = createClient(url, publicKey, {
          auth: { autoRefreshToken: false, persistSession: false }
        });
        const registration = await publicAuth.auth.signUp({ email: contactEmail, password });
        const user = registration.data.user;
        if (registration.error || !user || !user.identities?.length) {
          createError = registration.error ?? new Error("This email already has an account.");
        } else {
          const result = await admin.auth.admin.updateUserById(user.id, {
            app_metadata: {
              ...user.app_metadata,
              socyto_password_reset_required: true,
              socyto_account_type: isFirm ? "firm" : "client"
            }
          });
          if (result.error) {
            await admin.auth.admin.deleteUser(user.id);
            createError = result.error;
          } else createdUser = result.data.user;
        }
      } else {
        const result = await admin.auth.admin.createUser({
          email, password, email_confirm: true, phone: phone!,
          phone_confirm: false,
          app_metadata: {
            socyto_password_reset_required: true,
            socyto_account_type: isFirm ? "firm" : "client"
          }
        });
        createdUser = result.data.user;
        createError = result.error;
      }
      if (createError || !createdUser) {
        if (createError?.message?.toLowerCase().includes("already") ||
            (createError as { code?: string } | null)?.code === "email_exists") {
          return reply(409, { error: "This contact already has an account. Use account recovery." });
        }
        return reply(503, { error: "Unable to create account or request verification. Try again later." });
      }
      let createdRecordId: string | undefined;
      try {
        if (isFirm) {
          const { data: business, error } = await admin.from("businesses")
            .insert({ business_name: name.trim(), business_type: "interior_design",
                      contact_phone: phone, contact_email: contactEmail, firm_code: code, account_status: "active" })
            .select("business_id").single();
          if (error || !business) throw error ?? Error("Firm insert failed");
          createdRecordId = business.business_id;
          const { error: membershipError } = await admin.from("business_members")
            .insert({ business_id: createdRecordId, user_id: createdUser.id, member_role: "business_admin" });
          if (membershipError) throw membershipError;
          const { error: brandingError } = await admin.from("business_branding")
            .insert({ business_id: createdRecordId, display_name: name.trim() });
          if (brandingError) throw brandingError;
        } else {
          const { data: client, error } = await admin.from("clients")
            .insert({ business_id: firmId, client_name: name.trim(), client_phone: phone, client_email: contactEmail,
                      client_code: code, login_user_id: createdUser.id })
            .select("client_id").single();
          if (error || !client) throw error ?? Error("Client insert failed");
          createdRecordId = client.client_id;
        }
        const verificationRequested = !!contactEmail;
        return reply(201, {
          success: true, login_id: code, temporary_password: password,
          record_id: createdRecordId, requires_password_change: true,
          verification_required: !!contactEmail,
          verification_requested: verificationRequested
        });
      } catch (error) {
        // Undo partially created provisioned resources; never leave a working orphan login.
        if (createdRecordId && isFirm) {
          await admin.from("businesses").delete().eq("business_id", createdRecordId);
        }
        if (createdRecordId && !isFirm) {
          await admin.from("clients").delete().eq("client_id", createdRecordId).eq("business_id", firmId!);
        }
        await admin.auth.admin.deleteUser(createdUser.id);
        if (String(error).includes("23505") || String(error).includes("duplicate key")) continue;
        return reply(409, { error: "Unable to finish onboarding. No credentials were issued." });
      }
    }
    return reply(503, { error: "Unable to allocate a unique account ID. Try again." });
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
