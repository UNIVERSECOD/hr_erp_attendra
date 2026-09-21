# QA report template

Copy and fill at end of a QA run.

```markdown
# HR ERP SaaS QA Report

- **Date:**
- **Commit / branch:**
- **Agent scope:** full | modules:… | algorithms only | deploy only
- **Decision:** GO | NO-GO

## Executive summary
1–3 sentences: readiness, top blockers.

## Phase results

| Phase | Result | Notes |
|-------|--------|-------|
| 0 Inventory | PASS/FAIL | |
| 1 Build & tests | PASS/FAIL | |
| 2 Modules | PASS/FAIL | |
| 3 Algorithms | PASS/FAIL | |
| 4 Security/SaaS | PASS/FAIL | |
| 5 Deploy dry-run | PASS/FAIL | |
| 6 UI smoke | PASS/FAIL | |

## Automated test evidence
- Backend: `X` passed / `Y` failed (list failures)
- ISAPI: …
- Frontend build: …

## Findings

### P0
- [ ] `path` — issue — evidence — fix

### P1
- [ ] …

### P2
- [ ] …

### P3
- [ ] …

## Duplicate / dead-code decisions
| Item | Decision | Owner |
|------|----------|-------|

## Algorithm verdicts
| Algorithm | Verdict | Gaps |
|-----------|---------|------|
| Punch ingest | | |
| Attendance sync | | |
| Door sync | | |
| Day calc | | |
| Scheduler | | |
| Device user sync | | |
| Device import | | |
| Leave interactions | | |

## SaaS env checklist
Required secrets/env vars for first production tenant (list).

## Recommended fix order
1.
2.
3.

## Re-test plan after fixes
Commands + manual smoke steps.
```
