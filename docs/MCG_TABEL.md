# Attendra MCG Tabel

## Scope and isolation

The user requested a standard-project copy first, followed by MCG work in the current folder. No separate customer Setup was requested.

- Standard source copy: `D:\Attendra-Standard-before-MCG-20261012`, branch `main`, baseline `526b4e26aaf08e44006dedffa1196607a11212fb`.
- MCG workspace: `D:\hr-erp-back-front`, branch `codex/mcg`.
- Standard delivery repository (user requested 2026-10-12): `https://github.com/UNIVERSECOD/Attendra-Standard-20261012.git`, branch `main`. The standard copy's `origin` points there; its application baseline remains `526b4e2` with a documentation-only repository-split commit. MCG remains in `hr_erp_attendra` on `codex/mcg`; no merge into its `main` was requested.
- The copy has independent Git objects (`clone --no-hardlinks`), local configuration files and local agent instructions. Its original GitHub remote is preserved as `source`; `origin` is the standard delivery repository above. Configuration hashes and ACLs were checked without printing secrets. Dependencies, build output and runtime data were not copied. Local configuration and untracked agent instructions are not pushed.
- **The project copy is not a backup of the live database, photos or native installation.** Existing customer data remains in its original storage.
- Do not merge the customer-specific policy into general `main` without approval. The copies still have the same Docker container names and native installer identity; do not start both with the ordinary Compose/Setup workflow as if parallel installations were supported.

## Confirmed business rules

The user clarified that a full day follows **that date's assigned hours and entry/exit tolerances**, not a fixed eight-hour constant. An approved permission counts toward the day only when “İş saatından çıxılmasın” is selected.

| Case | MCG daily display |
| --- | --- |
| Complete assigned STANDARD/NIGHT shift, including accepted entry/exit tolerance | `1` |
| Complete six-hour or twelve-hour shift | Also `1` |
| Complete shift with two overtime hours | `1`; OT remains in the separate monthly total |
| Incomplete shift | Whole counted hours including OT, with minutes discarded for display: 8h16m becomes numeric `8` |
| Approved non-deductible permission fills a missing part of the shift | May complete `1`; overlapping credits are counted once |
| Deductible permission | Removes counted time; work outside the shift cannot buy back a full-day marker |
| Latest visit has no exit | No full-day marker; show counted whole hours and explain missing exit in the screen tooltip |
| FLEXIBLE/FIRST_ENTRY | Existing calendar-day hourly allocation; no fixed daily norm, no day marker/OT |
| No valid assigned schedule | Hours only; do not infer a day or OT from fallback 09:00–17:00 |
| Only historical summary remains, without sufficient source evidence | Show archived whole hours; warning toast/tooltip disclose missing evidence, with no invented day or OT |
| Approved leave, holiday, full-day deducted permission | Existing `Q/I` behavior preserved |
| Future date or unworked rest day | Display numeric `0` without adding to attendance/absence totals (existing `Q/I` can still be displayed) |

STANDARD/NIGHT continue using the existing first-in/last-out span minus break. Flexible intervals continue using closed interval sums, split at Asia/Baku midnight. This feature does not change the underlying attendance or overtime policy.

For day completion only, accepted tolerance gaps are normalized to the shift boundary, work outside the shift is clipped, and existing permission rules are applied again. This prevents overtime from compensating for a late/early/incomplete shift, and prevents permission plus tolerance from being credited twice. Recorded minutes are never replaced by `1` or increased by tolerance.

## API, screen and Excel

- `GET /api/tabel` and `/api/tabel/export` accept `mode=MCG`. Omitted mode defaults to `STANDARD` for backward compatibility. Existing six-argument service methods retain standard behavior.
- The MCG branch's Tabel page explicitly requests `MCG` for both display and export. If the backend is older and returns no MCG mode, it shows an error toast rather than presenting standard hours as MCG results.
- Existing `daily` numbers still mean hours. New `mcgDaily` records contain a boolean day marker, counted/regular/OT minutes, permission credit and source/status flags. Consumers must not sum markers and hours as the same unit.
- Monthly columns: full days, hours on days not represented by `1` (excluding OT), overtime hours, counted working days, and total counted hours. Total hours include permission credit and OT, but **not artificial tolerance credit**. Do not calculate total hours as `fullDays * 8`.
- Daily worked cells on screen and Excel are numeric: `1` when all full-day conditions hold, otherwise whole counted hours (`workedMinutes / 60`, truncated). No unit suffix, decimal fraction, OT line or `*`/`?` marker appears in these cells. Existing leave/holiday `Q/I` codes are preserved. Days without a value, including future days, display numeric `0`; underlying nulls and attendance/absence counting stay unchanged. Exact minute calculations and monthly totals remain unchanged; screen tooltips retain exact durations and source/exit warnings. Excel stores numeric cells. Legend and frozen identity columns are included.
- Terminated employees stay in historical Tabel and their names remain red. There are no `employmentStatus` or `terminationDate` Excel columns.
- Filters, export success/error toasts and session handling remain. Missing historical source data produces a warning toast and explanatory tooltips/legend.

## Data and delivery safety

This is a read-only report projection. There is no new migration, mass recalculation, device request, employee mutation, configuration replacement or Setup change. Tenant-scoped source reads and request-local schedule caches are used. Legacy explicitly assigned work schedules are distinguished from fallback hours without changing their existing attendance calculations.

Build/push alone does not update the already installed Windows application or running Docker containers. Native/production deployment and visual user acceptance remain separate steps. Use the existing backup-preserving update workflow when deployment is authorized.

## Verification

See the latest MCG entry in `TASK_STATUS.md` for exact build/test counts and environment limitations. Unit tests cover daily norms, tolerances including seconds, overtime, paid/deducted/overlapping permissions, missing exit, legacy/fallback assignment, flexible carry-in, future days, summary-only history, mode/filter forwarding and Excel content. Disposable PostgreSQL/API checks use synthetic records only and an internal Docker network without physical-device access.
