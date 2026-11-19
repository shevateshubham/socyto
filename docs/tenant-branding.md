# Business branding and white-label display

SOCYTO is one business using the shared multi-tenant platform. Future subscribers
can have their own display name, logo and colour palette without publishing
separate APKs.

## Tenant branding fields

The `business_branding` table has one row per `business_id`:
- `display_name` — business-facing name displayed in the application
- `logo_url` — approved HTTPS image in the public `business-logos` bucket
- `primary_color_hex` — primary controls and header
- `secondary_color_hex` — supporting colour
- `accent_color_hex` — accent colour
- `background_color_hex` — main background
- `text_color_hex` — primary body text

SOCYTO's first palette: olive `#2E4A2B`, soft olive `#6B8E49`,
gold `#D4AF7C`, warm ivory `#F7F5F0` and charcoal `#1F1F1F`.
Its approved logo can be uploaded when the storage bucket is provisioned.
Neither the business nor its logo is inserted as a fabricated production record.

## Security boundaries

- The brand is selected only after authentication from database-authorized
  business ownership or a client's assigned project.
- RLS on `business_branding` prevents reading other firms' configurations.
- Logo URLs are restricted in the Android app to HTTPS objects on the configured
  backend host, under its `business-logos` public storage path.
- Only **approved business logos** belong in that public bucket. Client images,
  site photographs and invoices must remain private.
- Initial branding changes are controlled by trusted platform provisioning,
  not by editing client-provided role or business IDs.
- Super Admin can inspect multiple firms. Switching firms changes the displayed
  theme to the selected firm; sign-out resets it.

## Still required before production

Create and secure the logo bucket, provision branding for each real firm, build
a Super Admin branding editor and image upload/validation flow, add colour
contrast/accessibility checks and test with two real tenant accounts.

The client-side colour and URL validation supplements database policies.
It is not an authorization mechanism.
