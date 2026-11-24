#!/usr/bin/env python3
"""SOCYTO production E2E integrity: real authenticated role chain, no privileged bypass.

Requires explicit SOCYTO_E2E_ADMIN_EMAIL and SOCYTO_E2E_ADMIN_PASSWORD.
Creates one test firm, Firm Admin, Client and project through the same app APIs.
Does NOT log credentials or silently delete test records. Run only with approval.
"""
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

BASE = os.environ["SOCYTO_SUPABASE_URL"].rstrip("/")
API_KEY = os.environ["SOCYTO_SUPABASE_PUBLISHABLE_KEY"]
OWNER_EMAIL = os.environ["SOCYTO_E2E_ADMIN_EMAIL"]
OWNER_PASSWORD = os.environ["SOCYTO_E2E_ADMIN_PASSWORD"]

def api(method, path, token=None, body=None, represent=False):
    headers = {"apikey": API_KEY, "Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if represent:
        headers["Prefer"] = "return=representation"
    if body is not None:
        headers["Content-Type"] = "application/json"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, headers=headers, data=data, method=method)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        # Never print raw responses; they can contain account or credential data.
        return error.code, None

def require(condition, label):
    if not condition:
        raise AssertionError(label)
    print("PASS | " + label, flush=True)

def sign_in(email, password):
    status, result = api("POST", "/auth/v1/token?grant_type=password",
                         body={"email": email, "password": password})
    require(status == 200 and isinstance(result, dict) and bool(result.get("access_token")),
            "Email/password sign-in succeeds")
    return result["access_token"], result["user"]

def query(table, token, fields="*", filter_string=""):
    path = "/rest/v1/" + table + "?select=" + urllib.parse.quote(fields, safe=",()")
    if filter_string:
        path += "&" + filter_string
    status, rows = api("GET", path, token)
    require(status == 200 and isinstance(rows, list), table + " authorized read works")
    return rows

def create(table, token, payload):
    status, rows = api("POST", "/rest/v1/" + table, token, payload, represent=True)
    require(status == 201 and isinstance(rows, list) and len(rows) == 1,
            table + " created with genuine role authorization")
    return rows[0]

def update(table, token, filters, payload):
    status, _ = api("PATCH", "/rest/v1/" + table + "?" + filters, token, payload)
    require(status in (200, 204), table + " draft update succeeds")

def new_password():
    return "Sa7!" + secrets.token_urlsafe(22)

def login_reset_login(email, temporary, role):
    temp_token, person = sign_in(email, temporary)
    require(person.get("app_metadata", {}).get("socyto_password_reset_required") is True,
            role + " flagged for first-login password reset")
    # Before reset: backend RLS must not expose any project.
    require(query("projects", temp_token, "project_id") == [],
            role + " cannot access project before password change")
    permanent = new_password()
    status, response = api("POST", "/functions/v1/client-onboarding", temp_token, {
        "action": "complete_first_login", "new_password": permanent
    })
    require(status == 200 and response and response.get("success") is True,
            role + " first-login password replacement")
    old_status, _ = api("POST", "/auth/v1/token?grant_type=password",
                        body={"email": email, "password": temporary})
    require(old_status in (400, 401), role + " temporary password revoked")
    fresh, info = sign_in(email, permanent)
    require(info.get("app_metadata", {}).get("socyto_password_reset_required") is not True,
            role + " new login no longer requires password reset")
    return fresh

def run():
    run_id = secrets.token_hex(5)
    prefix = "socyto-e2e-" + run_id
    test_email_firm = prefix + "-firm@example.com"
    test_email_client = prefix + "-client@example.com"
    created = {}
    try:
        admin, admin_user = sign_in(OWNER_EMAIL, OWNER_PASSWORD)
        require(bool(query("platform_administrators", admin, "user_id",
                          "user_id=eq." + admin_user["id"])),
                "existing, verified Super Admin has platform role")
        # Do not create a dummy Super Admin: platform owner's locked identity is immutable.
        status, firm = api("POST", "/functions/v1/firm-onboarding", admin, {
            "action": "provision_firm",
            "business_name": "SOCYTO E2E " + run_id,
            "administrator_email": test_email_firm,
            "business_type": "interior_design"
        })
        require(status == 201 and isinstance(firm, dict) and firm.get("success") is True,
                "Super Admin provisions firm and Firm Admin credentials")
        created["firm_id"] = firm["business_id"]
        created["firm_admin_email"] = test_email_firm
        require(firm.get("business_status") == "pending" and
                len(firm.get("temporary_password", "")) >= 20,
                "new firm pending, unique one-time Firm Admin password issued")
        firm_admin = login_reset_login(test_email_firm, firm["temporary_password"], "Firm Admin")
        require(query("businesses", firm_admin, "business_id",
                      "business_id=eq." + created["firm_id"]) == [],
                "pending Firm Admin cannot access firm workspace")
        status, _ = api("POST", "/rest/v1/rpc/set_business_account_status", admin, {
            "target_business_id": created["firm_id"], "new_status": "active"
        })
        require(status in (200, 204), "only Super Admin activates firm")
        rows = query("businesses", firm_admin, "business_id,account_status",
                     "business_id=eq." + created["firm_id"])
        require(len(rows) == 1 and rows[0]["account_status"] == "active",
                "Firm Admin can access own active workspace")
        reject, _ = api("POST", "/functions/v1/firm-onboarding", firm_admin, {
            "action": "provision_firm", "business_name": "Forbidden Sub-Firm",
            "administrator_email": prefix + "-forbidden@example.com",
            "business_type": "interior_design"
        })
        require(reject == 403, "Firm Admin cannot create a firm or another Firm Admin")

        client = create("clients", firm_admin, {
            "business_id": created["firm_id"],
            "client_name": "SOCYTO E2E Client",
            "client_email": test_email_client
        })
        created["client_id"] = client["client_id"]
        project = create("projects", firm_admin, {
            "business_id": created["firm_id"],
            "client_id": created["client_id"],
            "project_name": "SOCYTO E2E Interior Works",
            "project_type": "interior_design"
        })
        created["project_id"] = project["project_id"]
        checklist = create("project_requirements", firm_admin, {
            "business_id": created["firm_id"],
            "project_id": created["project_id"],
            "requirement_name": "Electrical rough-in",
            "tracking_type": "checklist", "effort_score": 1
        })
        quantity = create("project_requirements", firm_admin, {
            "business_id": created["firm_id"],
            "project_id": created["project_id"],
            "requirement_name": "Flooring installation",
            "tracking_type": "quantity", "effort_score": 3,
            "required_quantity": 100, "unit_name": "sq m"
        })
        filter_base = "business_id=eq." + created["firm_id"] + "&project_id=eq." + created["project_id"]
        update("project_requirements", firm_admin,
               filter_base + "&requirement_id=eq." + checklist["requirement_id"],
               {"is_completed": True})
        update("project_requirements", firm_admin,
               filter_base + "&requirement_id=eq." + quantity["requirement_id"],
               {"completed_quantity": 50})
        status, response = api("POST", "/functions/v1/client-onboarding", firm_admin, {
            "action": "provision_client", "business_id": created["firm_id"],
            "client_id": created["client_id"]
        })
        require(status == 201 and response and response.get("success") is True,
                "Firm Admin issues client temporary credentials")
        created["client_email"] = test_email_client
        client_token = login_reset_login(test_email_client, response["temporary_password"], "Client")
        project_rows = query("projects", client_token, "project_id", filter_base)
        require(len(project_rows) == 1, "Client sees own assigned project")
        published_before = query("project_publications", client_token,
                                 "publication_id", filter_base)
        require(published_before == [], "Client does not see unpublished draft requirements")
        require(query("project_requirements", client_token, "requirement_id", filter_base) == [],
                "Client has no direct read access to private requirement drafts")
        status, publication = api("POST", "/rest/v1/rpc/publish_project_progress", firm_admin, {
            "target_business_id": created["firm_id"],
            "target_project_id": created["project_id"],
            "publication_note": "Published test works"
        })
        require(status == 200 and isinstance(publication, str),
                "Firm Admin publishes immutable client snapshot")
        snapshots = query("project_publications", client_token,
                          "overall_progress_percent,published_requirements,public_update_note",
                          filter_base + "&order=published_at.desc")
        require(len(snapshots) == 1 and float(snapshots[0]["overall_progress_percent"]) == 62.5,
                "Client sees correct effort-weighted published progress (62.5%)")
        tasks = {x["requirement_name"]: x for x in snapshots[0]["published_requirements"]}
        require("Electrical rough-in" in tasks and
                tasks["Electrical rough-in"]["is_completed"] is True and
                "Flooring installation" in tasks and
                float(tasks["Flooring installation"]["completed_quantity"]) == 50.0,
                "Client sees actual published work titles and quantities")
        update("project_requirements", firm_admin,
               filter_base + "&requirement_id=eq." + quantity["requirement_id"],
               {"completed_quantity": 80})
        same_snapshot = query("project_publications", client_token,
                              "overall_progress_percent,published_requirements",
                              filter_base)[0]
        require(float(same_snapshot["overall_progress_percent"]) == 62.5 and
                float(next(x for x in same_snapshot["published_requirements"]
                           if x["requirement_name"] == "Flooring installation")
                      ["completed_quantity"]) == 50.0,
                "Unpublished Firm Admin edits are invisible to Client")
        blocked, _ = api("POST", "/rest/v1/project_requirements", client_token, {
            "business_id": created["firm_id"], "project_id": created["project_id"],
            "requirement_name": "Forbidden client change",
            "tracking_type": "checklist", "effort_score": 1
        })
        require(blocked in (401, 403), "Client cannot create or change work requirements")
        require(query("material_receipts", client_token, "material_receipt_id", filter_base) == [],
                "Client cannot read private material expenses")
        require(query("projects", client_token, "project_id",
                      "project_id=eq." + str(uuid.uuid4())) == [],
                "Client cannot read unrelated projects")
        print("PASS | Connected role-chain and publication integrity complete")
        return 0
    except Exception as ex:
        print("FAIL | " + type(ex).__name__ + ": " + str(ex), file=sys.stderr)
        return 1
    finally:
        print("CLEANUP_REQUIRED | Created test record identifiers below; no automatic deletion")
        print("RECORD_IDS | " + json.dumps(created, sort_keys=True))
        # Temporary passwords and bearer tokens deliberately never printed or persisted.

if __name__ == "__main__":
    if not BASE.startswith("https://") or "supabase.co" not in BASE:
        raise SystemExit("Refusing unexpected backend URL")
    sys.exit(run())
