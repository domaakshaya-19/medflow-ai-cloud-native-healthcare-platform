# Architecture implementation status

Baseline audited October 30, 2026. This document distinguishes source implementation from live verification and will be updated as work completes.

| Diagram component | Baseline | Source / API evidence | UI / security / tests | Missing work |
|---|---|---|---|---|
| React role dashboards | Partial | frontend/src/App.jsx, layouts/AppShell.jsx, pages/Dashboard.jsx | JWT session restoration; role routes; real API counts for Patient/Doctor/Admin | Clinical pages; additional supported staff workflows; browser tests |
| Gateway | Implemented core | backend/api-gateway; routes /api/auth,patients,doctors,appointments,medical-records,lab,prescriptions | CORS; downstream JWT; PatientRoutingTest | New routes, bounded requests, rate limits |
| Eureka | Implemented | backend/discovery-server; 8761 | All eight existing clients registered; public health | Register new services |
| Authentication | Implemented core | backend/auth-service; /api/auth/register,login,me,staff; UserAccount | BCrypt/JWT; nine role enum; admin-only staff provisioning; integration tests | Staff provisioning UI |
| Patient | Implemented core | backend/patient-service; /api/patients; Patient profile | Ownership; patient onboarding/edit UI; controller tests | Regression browser verification |
| Doctor | Partial | backend/doctor-service; /api/doctors,me,directory | Role checks; directory/detail UI; DoctorApiTest | Profile UI, actual scheduling/slot model |
| Appointment | Implemented core | backend/appointment-service; /api/appointments,me,doctor/me,doctor/me/queue | Ownership, transitions, queue constraints; booking/manage UI; API/client tests | Cross-patient/time-slot collision review; slot model |
| Medical Records | Backend implemented | backend/medical-records-service; /api/medical-records; MedicalRecord | Ownership + appointment validation; tests; live API passed at 18085 | Replace placeholder UI |
| Laboratory | Backend implemented | backend/lab-service; /api/lab/orders,results; LabOrder/LabResult | Ownership/roles/reference checks; tests; live API passed | Replace placeholder UI |
| Pharmacy | Partial domain only | backend/prescription-service; /api/prescriptions; Prescription/Medicine | Clinician writes/patient reads; tests; live API passed | Prescription UI, separate catalog/stock/dispensing ownership |
| AI Service/Gemini | Missing | No module or key configured at baseline | None | Service, consent-gated provider, JWT, failure/readiness handling |
| AI Orchestrator | Missing | None | None | Deterministic intent/agent selection, controlled tool dispatch |
| Patient Agent | Missing | None | None | Differentiated patient-owned read tools |
| Doctor Agent | Missing | None | None | Assigned clinical/queue tools |
| Emergency Agent | Missing | None | None | Administrative workflows; emergency safety behavior |
| Pharmacy Agent | Missing | None | None | Authorized prescriptions/catalog tools; no writes |
| Billing Agent | Missing | None | None | Owned invoices/status tools; no real payments |
| Operations Agent | Missing | None | None | Role-scoped operational tools |
| Nutrition/Recovery Agent | Missing | None | None | Sourced food information and general wellness tools |
| Controlled tools | Missing | None | None | Fixed registry, independent authorization, schema validation, filtered outputs, tests |
| Nutrition service/dataset/processing/recommendations/progress | Missing | None | None | Separate service/database; sourced seed dataset; transparent nonclinical rules |
| Billing | Missing | None | None | Separate service/database; invoices/charges/synthetic payment status |
| Emergency | Missing | None | None | Separate service/database; requests/assignment/admin priority/history |
| Notifications | Missing | None | None | Separate service/database; preferences/in-app delivery/reminders |
| PostgreSQL | Implemented for existing seven | application.yml per service; encrypted ignored configuration | Seven credential checks and live API flows passed; role scope review retained | Five new databases/roles approved separately; migrations for new services |
| Docker/Compose | Partial | Existing Dockerfiles and foundation-only docker-compose.yml | Existing Compose not run; native topology working | Full isolated development topology; validate without launching |
| Kubernetes/AWS | Missing | No deployment evidence | No deployment | Templates/documentation only; no paid resources authorized |
| CI/CD | Partial | .github/workflows/ci.yml | Maven verify + frontend build configured | Security/artifact validation; hosted execution unverified |
| DevSecOps | Partial | npm audit evidence | Four initial high findings; no configured Sonar/Trivy/ZAP execution | Compatible dependency updates, CI scans, truthful execution results |
| Actuator/Prometheus/Grafana | Partial | Actuator health in every existing service | Health verified; no Prometheus/Grafana running | Metrics/provisioning templates and validation |

Baseline evidence: all ten application HTTP endpoints healthy, 100 existing backend tests previously passing, full existing synthetic API smoke passing. Current-run builds and browser checks will be recorded separately in TEST_RESULTS.md. All existing databases, review documents, and unrelated applications must remain preserved. No commits or pushes authorized.
