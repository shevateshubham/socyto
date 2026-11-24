import { createClient } from "npm:@supabase/supabase-js@2.75.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const reply = (status: number, data: Record<string, unknown>) =>
  new Response(JSON.stringify(data), {status, headers});
function temporaryPassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply(405,{ error:"POST required." });
  if (!url || !secret || !publicKey) return reply(503,{ error:"Account provisioning unavailable." });
  const bearer = req.headers.get("Authorization") ?? "";
  if (!bearer.startsWith("Bearer ")) return reply(401,{ error:"Sign in required." });
  const token = bearer.slice(7);
  const userClient = createClient(url,publicKey,{auth:{autoRefreshToken:false,persistSession:false},
    global:{ headers:{Authorization:"Bearer " + token} }});
  const {data:identity,error:authError} = await userClient.auth.getUser(token);
  if (authError || !identity.user || !identity.user.email_confirmed_at)
    return reply(401,{error:"Verified Super Admin login required."});
  const admin = createClient(url,secret,{auth:{autoRefreshToken:false,persistSession:false}});
  const {data:owner,error:ownerError} = await admin.from("platform_administrators")
    .select("user_id").eq("user_id",identity.user.id).maybeSingle();
  if(ownerError || !owner) return reply(403,{error:"Only Super Admin may provision a firm."});

  let body: Record<string,unknown>;
  try {
    const raw=await req.text();
    if(raw.length>2048) return reply(400,{error:"Invalid request."});
    body=JSON.parse(raw);
  } catch { return reply(400,{error:"Invalid request."}); }
  if(body.action!=="provision_firm") return reply(400,{error:"Unsupported action."});
  const name=typeof body.business_name==="string"?body.business_name.trim():"";
  const email=typeof body.administrator_email==="string"?body.administrator_email.trim().toLowerCase():"";
  const type=typeof body.business_type==="string"?body.business_type.trim():"";
  if(name.length<2 || name.length>80 || type.length<2 || type.length>60 ||
    email.length>254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    return reply(400,{error:"Enter a valid firm name, administrator email and business type."});
  const password=temporaryPassword();

  // Supabase Auth refuses duplicate emails; never reset an existing user's password.
  const {data:created,error:createError}=await admin.auth.admin.createUser({
    email, password, email_confirm:true,
    app_metadata:{socyto_password_reset_required:true,socyto_provisioned_role:"business_admin"}
  });
  if(createError || !created.user) {
    if(createError?.message?.toLowerCase().includes("already") || createError?.code==="email_exists")
      return reply(409,{error:"This email already has an account. Choose a different Firm Admin email; no password was changed."});
    return reply(409,{error:"Firm Admin account could not be created; no firm was registered."});
  }
  // The existing database RPC validates the caller's real JWT, grants membership,
  // creates branding, and sets the new business pending.
  const {data:firmId,error:firmError}=await userClient.rpc("register_business_for_existing_user",{
    new_business_name:name,administrator_user_id:created.user.id,new_business_type:type
  });
  if(firmError || typeof firmId!=="string") {
    await admin.auth.admin.deleteUser(created.user.id);
    return reply(409,{error:"Firm registration failed. New login was rolled back."});
  }
  return reply(201,{
    success:true, business_id:firmId, email, temporary_password:password,
    requires_password_change:true, business_status:"pending",
    message:"Copy the one-time temporary password and share it privately. Activate the firm before Firm Admin access."
  });
});
