# Device employee reconciliation

The Azerbaijani **Əməkdaşları uyğunlaşdır** action is available to head-office HR on the device page and the area device panel. Area changes remain local until this action is explicitly confirmed.

## Preview and confirmation

- Backend resolves the bridge ID, verifies device ID/IP/name/enabled state and live connectivity, then reads the complete physical user list through the strict ISAPI snapshot endpoint.
- Desired employees come from current area membership and individual assignments. Existing assigned non-terminated employees stay; only active missing employees are added. Terminated employees are never added.
- Unknown terminal users, duplicate terminal numbers, name mismatches and non-normal terminal user types are shown separately and preserved.
- Known unassigned employees appear in the removal list. HR profiles, photos, attendance and assignments are not deleted. The ISAPI shared user/photo cache is also preserved by this removal route.
- The user reviews names/counts and checks the explicit confirmation before applying. Additions run before removals. Each write is checked with a fresh physical snapshot; failures or unverified results stop remaining steps and require a new preview. Photo failure is reported separately.

## API and operational limits

`POST /api/devices/{backendId}/employees/reconciliation/preview` returns a server-generated token and grouped rows. `POST .../apply` takes `{token, step, confirmed}` and applies one operation at a time. The frontend sends sequential requests; nginx allows up to 180 seconds per reconciliation request.

Tokens are bound to tenant, actor, device identity, current assignments and the physical snapshot. They expire after 15 minutes. Each step rechecks source state and the device list; repeated completed steps return their prior progress without repeating physical writes. Device-level locks serialize reconciliation requests within this process. Tokens are held only in memory (maximum 1,000 previews); a restart or routing to another backend instance requires a fresh preview. This is intended for the existing single-backend deployment. Physical devices and external writers cannot participate in a database transaction; concurrent changes detected by readback stop the sequence. No background retry is scheduled.

Audit entries record actor, device, employee database ID, action and verified/partial result. No device credentials or biometric data are included. Existing additive employee sync remains available, including retrying an unavailable face upload.

## Verification (2026-10-11)

- 331 backend tests; 33 ISAPI tests; frontend lint/build; all three Docker images and Compose configuration.
- 12 isolated Docker API checks with a stateful mock bridge: preview, explicit consent, add-before-delete, repeated-step safety, preserved unknown/manual users and HR data, audit results, idempotence and offline rejection. Browser preview and confirmed apply verified against that fixture.
- Real-device read-only previews verified on two terminals. After explicit confirmation naming the target and nine records, nine removals succeeded with physical readback. A fresh preview showed no remaining removal operations. The other terminal's list, HR employee count and assignment counts were unchanged. Ambiguous identity was preserved. Real-device additions/photo uploads were not exercised in this acceptance check.
- Customer installation updated after successful backup; no new database migration, volume recreation or credential change.
