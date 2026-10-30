# MedFlow-AI Windows demo — October 31, 2026

## Verified on this laptop on October 30

- Cloned `https://github.com/domaakshaya-19/medflow-ai-cloud-native-healthcare-platform.git`, branch `main`, commit `3336d45`. The destination was empty. No existing files or review documents were removed.
- Java 21.0.7, Maven 3.9.16, Node 22.20.0, npm 10.9.3, Git, and Docker 29.6.2 are installed. PostgreSQL 14 is installed and accepting connections on 5432. No tools were installed.
- RAM: 15.31 GiB total, about 1.98 GiB free at inspection. C: had 37.91 GiB free. Close unused applications before starting all JVMs. The launcher caps each JVM heap at 384 MiB; total process memory is larger than heap.
- `mvn.cmd -B verify`: all nine modules succeeded; 100 tests across 15 suites, zero failures/errors. Tests use isolated H2/stub configurations and do not establish PostgreSQL functionality.
- `npm.cmd --prefix frontend ci` and `npm.cmd --prefix frontend run build`: succeeded.
- All nine backend health endpoints are UP. Eureka reports exactly one UP registration for each of the eight clients. Frontend delivery returns HTTP 200, and browser inspection verified the React sign-in form renders.
- The seven MedFlow-only role passwords were reset with explicit user authorization after scope review. Each role owns only its matching database, has no role memberships or cross-database dependencies, and had no active sessions. No other application reference was found in the inspected local project configurations. Seven unique, cryptographically generated 64-character passwords are stored encrypted locally. All seven database connection checks passed. See [role scope review](ROLE_SCOPE_REVIEW.md).
- Live synthetic API checks passed through Gateway: patient registration/login and identity, profile onboarding/read-back, doctor provisioning and directory, appointment booking/read-back and valid status transitions, medical record creation/read-back, lab order/result creation/read-back, and prescription creation/read-back. Protected endpoints rejected unauthenticated requests with 401.
- Medical Records uses **18085** because unrelated `securevault-backend` occupies 8085. SecureVault and its PostgreSQL container were left running unchanged. Gateway discovers Medical Records through Eureka at its actual port.
- Authenticated browser interactions remain a manual presentation check. Medical Records, Laboratory, and Prescriptions have backend APIs but placeholder React pages.
- npm audit reports four high-severity findings involving axios, follow-redirects, source-map-js, and vite. No dependency upgrade or security bypass was applied.

Treat `local-runtime/verification.txt`, when present, as a separate dated run result. A successful build or Gateway health check does not mean downstream features work.

## First-time setup

Open PowerShell in this folder:

```powershell
Set-Location 'C:\Users\sreej\OneDrive\Desktop\MedFlow-AI'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd -B verify
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
```

Root `.env` and `frontend/.env` were copied from their templates without overwriting anything. Root `.env` still contains placeholders; do not use those placeholder passwords. Missing Patient and Doctor settings were added to `.env.example`.

For the approved creation of missing local development databases/roles, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Provision-Local.ps1
```

Enter the PostgreSQL administrator username (normally `postgres`) and password in that terminal. Password input is hidden. Existing roles/databases are preserved: the script does not reset passwords, alter ownership, drop data, or grant new access to existing databases. Existing application-role passwords are requested when needed. If existing database ownership or access differs, inspect it locally before changing anything.

New role passwords and the shared JWT secret are generated with a cryptographic random generator. Choose a demo administrator password of 10–72 characters with uppercase, lowercase, number, and symbol. The administrator login is `admin@example.test`. Existing accounts with this email are not reset by the application.

Credentials are stored as Windows DPAPI-encrypted SecureStrings in ignored `local-runtime/secrets.xml`, tied to this Windows account. No credentials are put in source, command arguments, or printed. Do not share the local runtime directory, credentials, tokens, environment dumps, or raw logs. Avoid PowerShell transcripts when handling secrets.

Provisioning then invokes the full launcher. This laptop is already provisioned; **do not provision again for normal startup**. PostgreSQL 14 is older than the README's stated 16+ prerequisite, but the live database-backed smoke flows passed on this installation. Docker is unnecessary for the native MedFlow run. The existing Compose file only covers the older Auth foundation and cannot start all seven database-backed services.

The scripts isolate module discovery to the active PowerShell engine's own modules, avoiding duplicate `Microsoft.PowerShell.Security` type definitions. The original eight databases for other projects remain untouched. The demo administrator bootstrap setting was replaced with a generated strong encrypted value to satisfy the existing policy; no existing application administrator password was reset.

For a future forgotten-password recovery, first audit role scope with the reset workflow:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Reset-MedFlowPasswords.ps1
```

This prompts securely for the PostgreSQL administrator password, audits catalogs and active sessions, writes ignored `local-runtime/role-scope-audit.json`, and pauses for review. It cannot reset roles until an approval file names that exact audit ID and `reset-exact-seven` action. Review every finding and obtain explicit approval for any uncertain/shared dependency before authorizing. It changes only the seven named role passwords in one transaction, preserves role attributes, writes Windows-encrypted application configuration, verifies each connection, and starts the project. It does not save or reset the PostgreSQL administrator password. SQL statement logging is suppressed only within the sensitive transaction, with no global logging changes; plaintext generated passwords are never sent as SQL literals.

`Repair-AuthCredential.ps1` remains available when a password is known and only the locally stored Auth credential needs correcting; it does not reset PostgreSQL. Neither recovery script is needed for routine startup now.

If databases already exist and no provisioning is wanted, use `scripts/Configure-Local.ps1` instead to enter existing application passwords and the existing shared JWT secret securely. It refuses to overwrite an existing encrypted configuration.

## Start and stop

```powershell
# PostgreSQL must already be running. This launcher does not modify Windows services.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Local.ps1

# Only Eureka, Gateway, and frontend; this is NOT a complete application:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Local.ps1 -InfrastructureOnly

# Stop only processes launched and tracked by this project's launcher:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Stop-Local.ps1
```

The launcher starts Eureka, Auth, Patient, Doctor, Appointment, Medical Records, Lab, Prescription, Gateway, then Vite, checking health between launches. It uses the `local` profile and explicit loopback advertisement because all services run on this laptop. Do not use these discovery overrides in containers or on multiple hosts.

Hidden process IDs, executable paths, and start times are recorded in ignored `local-runtime/processes.json`. Occupied untracked ports cause an error; no unrelated process is killed. Tracked listeners are reused. If a tracked process is unhealthy, inspect its local logs and stop/restart it explicitly. Start/stop scripts should not run concurrently. The stop script checks process identity before terminating in reverse order; finish requests before stopping. PostgreSQL and its data remain untouched. Keep the manifest while processes are running.

This restricted executor can reach localhost services while being unable to enumerate their host processes. The launcher now probes TCP ports directly, rather than relying on incomplete listener inventories. A repeated infrastructure launch was checked: it reused all three reachable services. If a tracked healthy service is reachable but process identity is unavailable, it is reused with a warning; the stop script cannot terminate it unless identity checks pass. Run shutdown from a normal host PowerShell terminal when necessary. Early duplicate attempts exited on occupied ports; original process identities have been preserved in the normalized manifest.

Environment files are parsed as data, never executed. Secrets come from encrypted storage, not `.env`. Spring uses `ddl-auto: update`; start only against the approved development databases because startup can update their schema. This is not a production migration system.

| Component | Port | Development database/role |
|---|---:|---|
| PostgreSQL | 5432 | Existing installation |
| Eureka | 8761 | None |
| Auth | 8081 | medflow_auth |
| Patient | 8082 | medflow_patient |
| Doctor | 8083 | medflow_doctor |
| Appointment | 8084 | medflow_appointment |
| Medical Records | 18085 | medflow_medical_records |
| Lab | 8086 | medflow_lab |
| Prescription | 8087 | medflow_prescription |
| Gateway | 8080 | None |
| React/Vite | 5173 | None |

Each application has `<PREFIX>_DB_URL`, `<PREFIX>_DB_USER`, and `<PREFIX>_DB_PASSWORD`; prefixes match the template, including `MEDICAL_RECORDS`. All authenticated services share `JWT_SECRET`. The launcher reads non-secret URL/user overrides from root `.env`, uses local Eureka, and sets frontend API origin to port 8080.

Keep `MEDICAL_RECORDS_SERVICE_PORT=18085` in the ignored root `.env` on this laptop. Startup and smoke checks share `Get-LocalServicePorts.ps1`, so they both respect that override. Do not stop SecureVault or point MedFlow health checks at port 8085.

## URLs and functional verification

- Frontend: <http://localhost:5173> (also <http://127.0.0.1:5173>)
- Eureka: <http://localhost:8761>
- Gateway health: <http://localhost:8080/actuator/health>
- Each backend's health endpoint: `http://localhost:<port>/actuator/health`

After the full launcher completes, wait about 30 seconds for discovery and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Demo.ps1
Get-Content .\local-runtime\verification.txt
```

This smoke script checks every health endpoint, exactly one UP registration per service, and unauthenticated 401 responses through Gateway. It then uses normal APIs to create uniquely named synthetic patient/doctor accounts, onboard profiles, find the doctor, book an appointment, change it to IN_PROGRESS, create/read a medical record, create/read a lab order and result, and create/read a prescription. It never mints tokens or disables authorization. Tokens remain in memory, and only pass/fail paths are recorded. Synthetic records are retained; rerunning creates another uniquely named set. Stop after an unexpected failure and inspect the affected service's exception privately.

Generated demo account credentials are encrypted in `local-runtime/demo-<id>.xml`; they are not printed. For a browser presentation, register your own synthetic patient with a password you choose. Use `.test` email addresses, invented names, and no real health information.

## Presentation flow

1. Show Eureka registrations and public health endpoints. Explain that the Gateway uses service discovery.
2. In the frontend, choose Register, enter a synthetic name/email and strong password, then sign in. Complete Patient Profile Setup and open the dashboard/account pages. Reload to check session restoration; sign out and confirm protected routes require login.
3. Open Doctors. The smoke script creates an available synthetic doctor; use the directory and detail page to demonstrate discovery and filtering.
4. Book an appointment for that doctor on a future date, inspect its details and patient appointment list. Use the doctor's API login to demonstrate the assigned doctor list/queue and status updates. A queue for today will not contain tomorrow's booking.
5. Medical Records, Laboratory, and Prescriptions currently have **placeholder React pages**. Demonstrate their authenticated API smoke results, not a completed clinical UI. Their API clients and backend controllers exist.
6. Clinical records require an appointment IN_PROGRESS or COMPLETED. Lab orders require a matching CONFIRMED, IN_PROGRESS, or COMPLETED appointment. IDs must be Patient/Doctor profile IDs, not Auth user IDs. The smoke script resolves these through normal APIs.
7. Keep JWTs and passwords off-screen during API demos. Show only sanitized statuses and synthetic record fields. Do not paste bearer tokens into the guide or terminal history.

The repository README describes an older Day-1 scope and is not a complete inventory of the current code. AI/Gemini is not configured; do not claim AI functionality. Clinical UI completion, cloud/Kubernetes deployment, and monitoring are outside the verified local demo.

## Troubleshooting without data loss

- 503 through Gateway: inspect Eureka registrations and downstream health; Gateway health alone is insufficient. Allow discovery cache refresh.
- Database authentication/connection failure: check the role, matching database, password, and PostgreSQL service. Never reset an existing password or delete a database to get a green health check.
- Port conflict: inspect the port owner locally and stop only a verified project-owned process. Never stop all Java/Node processes.
- Startup exception: inspect `local-runtime/<service>.out.log` and `.err.log` privately; fix only the affected configuration/module and rebuild it. Preserve security and authorization.
- Memory pressure: close unused applications; do not stop unrelated processes automatically. Increase heap only after inspecting available memory and the actual exception.
- To apply source changes, stop tracked services, rebuild affected modules (or `mvn.cmd verify`), and restart. Existing review DOCX/PPTX files are preserved.

Remaining manual presentation check: open the frontend, register a fresh synthetic patient with a password you choose, sign in, complete the profile, browse doctors, and book an appointment. Backend API flows and connectivity are verified; authenticated browser interactions are not yet verified. Database provisioning and credential recovery are complete. No password should be sent in chat.
