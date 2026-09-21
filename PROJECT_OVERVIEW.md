# PROJECT OVERVIEW

## 1. Project Summary

This repository contains a monorepo-based HR ERP system focused on office HR operations, attendance management, employee management, reporting, and physical access-device integration.

The project is designed for Azerbaijan-based HR workflows and is evolving toward a more complete ERP platform with:

- employee and company management
- attendance and access-log tracking
- shift / work schedule (`iş qrafiki`, `növbə`) support
- leave and reporting workflows
- multi-tenant data separation
- device integration through Hikvision / ISAPI-compatible access-control hardware
- Azerbaijani-first user experience

Repository: `Saidshi4/hr-erp-back-front`

---

## 2. High-Level Architecture

The repository is structured as a monorepo with three main application layers:

1. **Frontend** — React SPA for HR users
2. **Backend** — Spring Boot API for business logic and data access
3. **ISAPI service** — Spring Boot integration service for device communication

### Architecture flow

```text
Frontend (React)
    ↓
Backend API (Spring Boot)
    ↓
PostgreSQL
    ↑
ISAPI Service (Spring Boot)
    ↑
Face-recognition / access-control devices
```

### Main idea

- The **frontend** is the HR interface.
- The **backend** owns core business rules, CRUD, attendance usage, reporting, and tenant-aware data access.
- The **ISAPI** service communicates with physical devices and persists imported punch/access events.
- The backend then consumes those events for attendance and HR workflows.

---

## 3. Repository Structure

Root-level structure currently includes:

- `backend/` — main HR ERP backend API
- `frontend/` — main web UI
- `isapi/` — device integration service
- `docs/` — documentation area
- `docker-compose.yml` — local orchestration
- `docker-entrypoint-initdb.d/` — database initialization assets
- `README.md` — base monorepo introduction

Other root files/folders may be operational or environment-related.

---

## 4. Technology Stack

### Backend
- Java 17
- Spring Boot 3
- Spring Security
- Spring Data JPA / Hibernate
- Flyway
- Maven

### Frontend
- React 18
- TypeScript
- Vite
- Tailwind CSS
- Zustand

### ISAPI Service
- Java 21
- Spring Boot
- Spring Data JPA
- Jackson
- Gradle
- Springdoc OpenAPI

### Database / Infra
- PostgreSQL 15
- Docker
- Docker Compose
- Nginx

---

## 5. Product Purpose

The system’s purpose is to help an HR/admin team manage a company’s workforce and attendance operations in one place.

At a business level, the product is intended to support:

- employee profile management
- office access / attendance tracking
- schedule and shift planning
- attendance reporting
- work-hour / table (`tabel`) style summaries
- leave-related workflows
- device-connected real-world office monitoring

The product is especially shaped around companies using facial-recognition or access-control devices to capture office entry/exit activity.

---

## 6. Core Product Modules

Based on repository documentation and current project direction, the main modules include:

### 6.1 Employee Management
Likely handles:
- employee creation and editing
- assignment to company / tenant
- device user mapping
- profile and organizational information

### 6.2 Attendance / Davamiyyət
Handles:
- raw access punches from devices
- daily attendance visibility
- current presence/live status
- entry/exit summaries
- attendance screens for HR monitoring

### 6.3 Access Logs
Handles:
- imported device events
- device-origin audit history
- debugging and reconciliation of attendance events

### 6.4 Shift / İş qrafiki / Növbə
Handles:
- work start and end times
- break definitions
- lateness tolerance
- schedule rules used by attendance calculations

### 6.5 Tabel / Work Hour Summaries
Handles:
- worked-time summaries
- daily/monthly duration calculations
- reporting-friendly attendance aggregation

### 6.6 Device Management
Handles:
- device configuration
- per-device connectivity settings
- device user sync
- cursor reset / event re-import control

### 6.7 Leave / Reporting
Expected to include:
- leave management
- HR reports
- summary statistics and dashboards

---

## 7. Multi-Tenant Intent

The repository README states that the system is designed with multi-tenant support in mind.

### Multi-tenant direction
- Each company/tenant should have isolated data.
- Data isolation is expected to be handled with `company_id` / `tenant_id` style fields.
- Devices are expected to be mapped to the correct tenant.
- Attendance events should ultimately belong to the right company context.

### Practical implication
This means future and current features should be implemented with tenant separation in mind, even if some current project areas still operate with single-company assumptions.

---

## 8. Device Integration Overview

A major differentiator of this project is physical device integration.

### Current integration concept
The system integrates with ISAPI-compatible access-control / face-recognition devices.

### Documented flow
- The ISAPI service communicates with physical devices.
- It polls or streams attendance/device events.
- Device events are stored in the shared database layer.
- The backend reads these events and uses them for attendance-related functionality.

### Existing documented proxy flow
The README documents a backend proxy flow for device user management:

Frontend → Backend → ISAPI

Documented backend endpoints include operations like:
- create device user
- list device users
- get device user
- update device user
- delete device user
- sync a device user
- upload face image

### Operational note
The repository also documents cursor reset endpoints for stuck event-history polling, showing that device sync/import state is operationally important.

---

## 9. Attendance Domain — Business Understanding

Attendance is one of the most important modules in this project.

### Current business context
The company can have multiple Hikvision devices. These devices are used to capture employee presence in the office.

### Required business reality
Devices should be treated as **bidirectional checkpoints**, not rigid entry-only or exit-only devices.

That means an employee may:
- enter from device 1 and leave from device 1
- enter from device 1 and leave from device 2
- enter from device 2 and leave from device 1
- enter from device 2 and leave from device 2

### Why this matters
Attendance logic must not permanently rely on:
- one device = only giriş
- another device = only çıxış

Instead, attendance should be inferred from the employee’s **ordered daily event sequence**.

---

## 10. Attendance Algorithm Direction (Business-Side Requirement)

The intended algorithm direction for the project is:

### 10.1 Raw event principle
Keep imported raw device events intact.

### 10.2 Daily inference principle
For each employee/day:
- sort punches chronologically
- infer in/out state from sequence
- support multiple in/out events in one day

### 10.3 Summary display principle
For the live attendance screen (`davamiyyət`), the system should mainly show:
- **first valid entry of the day**
- **final valid exit of the day**
- **current status**

Intermediate short exits should not replace the final daily exit in UI summary.

### 10.4 Worked-time principle
For `tabel` and hour calculation, worked time should be based on actual presence intervals:
- in → out
- in → out
- in → out

Then total worked time becomes the sum of all inside intervals.

This is more accurate than using device role assumptions.

---

## 11. Attendance Status Model (Current Intended Direction)

For the current office-based use case, the key attendance statuses are intended to be:

- `İşdə`
- `Gecikib`
- `İşdə deyil`
- `İş saatı bitib`

### Intended meaning
- **İşdə** — employee is currently inside / active in workday
- **Gecikib** — employee arrived later than allowed tolerance
- **İşdə deyil** — employee is currently outside or has not entered during work hours
- **İş saatı bitib** — workday is over / final leave is complete

### Current known business issue
A known issue exists in the current project behavior:
- after an employee leaves, the attendance screen may still continue showing `İşdə`

This is a target area for refinement.

---

## 12. Shift / İş Qrafiki Direction

Shift settings are intended to become a stronger source of truth for attendance logic.

### Shift configuration should define
- shift name
- start time
- end time
- break duration or break configuration
- maximum allowed lateness / tolerance

### Why this matters
Instead of hardcoding lateness logic globally, attendance status should use the assigned shift rules.

### Example
If a shift starts at `09:00` and lateness tolerance is `30 minutes`:
- arrival at `09:05` → normal `İşdə`
- arrival at `09:31` → `Gecikib`

### Current business assumption
For the company currently being discussed, office time is effectively centered around:
- start: `09:00`
- end: `17:00`

But this should ideally be shift-configurable rather than fixed in code.

---

## 13. Tabel / Worked Hour Logic Direction

The `tabel` or daily work-hour summary should reflect real worked time, not only first-in / last-out display.

### Intended rule
- display can stay simple
- calculation should stay accurate

### Recommended direction
Worked time should be computed from actual inside intervals rather than fixed assumptions.

#### Example
If someone punches:
- 09:00 in
- 13:00 out
- 14:00 in
- 14:50 out
- 15:00 in
- 17:00 out

Then worked time should be calculated from:
- 09:00–13:00
- 14:00–14:50
- 15:00–17:00

### Practical result
This naturally deducts time spent outside the office, including lunch and short temporary exits, without needing device-specific rules.

---

## 14. Authentication Direction

Authentication was identified as an important next-stage improvement area for the project.

### MVP direction chosen for auth
- JWT-based authentication
- signup/login support
- username + password flow
- multi-user capable
- simple HR-only role model for first implementation stage

### Intended frontend behavior
- login page
- signup page
- route protection
- logout
- token storage and API authorization header handling

This area is part of the project roadmap and implementation progression.

---

## 15. Localization / Azerbaijani UX Direction

The project is intended to provide Azerbaijani-first UI wording.

### Direction
- central Azerbaijani dictionary / i18n structure
- no mixed English/Russian leftovers in normal UI
- consistent terminology across modules

### Likely target areas
- menus
- forms
- buttons
- validation
- attendance statuses
- dashboard headings
- table labels
- messages / toasts

This is an important product-quality layer, especially because HR staff use the UI operationally every day.

---

## 16. Current Repository Reality (What is verified vs inferred)

### Verified from repository root / README
Verified directly from repository inspection:
- monorepo structure exists
- `backend`, `frontend`, `isapi`, `docs` folders exist
- README describes architecture and stack
- README documents device proxy endpoints and cursor reset concepts
- backend uses Spring Boot/Maven style structure
- frontend uses React/Vite/TypeScript style structure

### Inferred from project context and active work
The following are based on user requirements, current project direction, and earlier implementation planning rather than a full code audit of every class/file:
- exact attendance service file names
- exact current attendance calculation classes
- exact frontend component hierarchy for `davamiyyət`
- exact DB schema details for attendance summary/tabel logic
- exact implementation of shift-to-attendance linkage

This file is therefore a **combined project overview**, not a full line-by-line source-code audit.

---

## 17. Backend Responsibility Summary

The backend is the main business-logic layer and is expected to handle:

- employee CRUD and tenant-scoped business data
- attendance aggregation and report generation
- shift and work schedule management
- device-related orchestration through backend APIs
- security and authentication logic
- integration with PostgreSQL through JPA/Hibernate
- migration/versioning via Flyway

### Operational significance
As the project evolves, the backend is the most likely place for:
- attendance algorithm refactor
- JWT auth integration
- shift-based lateness logic
- report accuracy improvements

---

## 18. Frontend Responsibility Summary

The frontend is the operational UI used by HR/admin users.

It is expected to handle:
- dashboards
- employee pages
- attendance (`davamiyyət`) screens
- reports / tables
- device user management screens
- admin workflows
- future auth UI and localization UX

### UX goals
The frontend should present:
- clean Azerbaijani terminology
- simple attendance summaries
- accurate statuses
- easy HR-friendly workflows

---

## 19. ISAPI Service Responsibility Summary

The ISAPI service exists specifically for device integration concerns.

### Its role includes
- connecting to supported devices
- importing / synchronizing attendance-related events
- handling lower-level device communication logic
- enabling backend-facing integration without putting hardware-specific details directly in the main backend

This separation is useful because hardware communication is usually noisier and more operationally complex than the HR business layer itself.

---

## 20. Local Development / Startup

The repository README documents Docker-based startup.

### Quick start
```bash
docker-compose up --build
```

### Documented access points
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- ISAPI Service: `http://localhost:8081`
- pgAdmin: `http://localhost:5050`

### Documented default login in README
- username: `admin`
- password: `admin123`

> Note: this reflects current documented setup and may change as proper JWT authentication is introduced.

---

## 21. Documentation Goals of This File

This file is intended to act as a shared project-level reference for:

- product understanding
- technical onboarding
- feature planning
- PR planning
- future refactoring discussions
- alignment between business rules and implementation direction

It is especially useful because the project is evolving quickly across multiple areas at once.

---

## 22. Known Active Improvement Themes

Current major improvement themes discussed for this repository include:

1. **Shift / İş qrafiki improvements**
   - custom shift definitions
   - break time support
   - lateness tolerance

2. **Azerbaijani localization**
   - central dictionary
   - UI terminology consistency

3. **Authentication**
   - real signup/login
   - multi-user support
   - JWT security

4. **Attendance algorithm refactor**
   - bidirectional devices
   - interval-based worked-time calculation
   - correct live status
   - first-in / final-out summary logic

These changes represent the project’s movement from MVP-like behavior toward a more operational production HR system.

---

## 23. Recommended Documentation to Add Later

This file is intentionally broad. Over time, it would be beneficial to add more focused documents such as:

- `docs/attendance-flow.md`
- `docs/device-integration.md`
- `docs/authentication.md`
- `docs/tenant-model.md`
- `docs/frontend-structure.md`
- `docs/backend-domain-model.md`
- `docs/shift-and-tabel-rules.md`

That would make onboarding and future maintenance much easier.

---

## 24. Recommended Next Technical Clarifications

To make project documentation even more accurate later, the following should be captured from code and/or business decisions:

- exact attendance entities/tables
- exact source of truth for `davamiyyət` status
- exact `tabel` calculation implementation
- exact device-event import model and reconciliation rules
- exact shift schema and employee-shift linkage
- exact auth/session model after JWT implementation
- exact i18n file structure after AZ dictionary rollout

---

## 25. Final Summary

This repository is an HR ERP monorepo that combines:

- HR business workflows
- attendance management
- physical device integration
- tenant-aware architecture direction
- modern frontend/backend stack
- ongoing product refinement for real office usage

Its most important product-specific complexity is the intersection between:
- people data
- work schedule logic
- raw device punches
- live attendance status
- worked-time reporting

That is also the area where the system currently has the most important refinement opportunities.

The long-term direction should keep emphasizing:
- correctness of attendance logic
- configurable shift rules
- clean Azerbaijani UX
- reliable authentication
- maintainable architecture
