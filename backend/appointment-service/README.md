# Appointment Service

Day 3 appointment and doctor-day queue management. Runs on port `8084` as `appointment-service`, registers with Eureka, and owns the `medflow_appointment` PostgreSQL database. Configure `APPOINTMENT_DB_URL`, `APPOINTMENT_DB_USER`, `APPOINTMENT_DB_PASSWORD`, `EUREKA_SERVER_URL`, and the same `JWT_SECRET` used by Auth and Doctor Service. `APPOINTMENT_SERVICE_PORT` overrides the port.

From the repository root, after creating the database and setting environment variables, run `mvn -pl backend/appointment-service spring-boot:run`. Requests route through the gateway at `/api/appointments/**`. Health is at `http://localhost:8084/actuator/health`.

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/appointments` | PATIENT self |
| GET | `/api/appointments/me` | PATIENT self |
| GET | `/api/appointments/{id}` | Owner PATIENT, assigned DOCTOR, ADMIN |
| PUT | `/api/appointments/{id}` | Owner PATIENT rescheduling, assigned DOCTOR status, ADMIN |
| DELETE | `/api/appointments/{id}` | Authorized cancellation; record retained |
| GET | `/api/appointments` | ADMIN |
| GET | `/api/appointments/doctor/me` | Assigned DOCTOR |
| GET | `/api/appointments/doctor/me/queue` | Assigned DOCTOR, today's active queue |

The JWT subject is the Auth user UUID. For patient operations, Appointment Service forwards the caller's bearer token to Patient Service `/api/patients/by-auth-user/{authUserId}` and stores the returned Patient profile UUID as `patientId`. Patient-owned queries and authorization checks use that resolved profile UUID. `doctorId` stores the Doctor Service profile ID. Both services are queried through Eureka/OpenFeign. Doctor existence lookup forwards the authenticated patient's unchanged bearer token to `/api/doctors/directory/{id}`; doctor-owned queries continue forwarding the doctor's own token to `/api/doctors/me`. Appointment Service does not mint service or ADMIN tokens. Downstream authorization failures retain the existing 502 response and never trigger a privileged retry. If either required profile lookup fails, Appointment Service returns a clear `404` or `502` instead of accepting an unverified identity.

Queue numbers start at 1 per doctor and appointment date. New appointments receive `max(queueNumber) + 1`; cancellation retains the number. A unique database constraint prevents duplicate queue numbers for a doctor/date, so a simultaneous collision returns `409` rather than storing duplicates. A separate unique constraint prevents duplicate patient/doctor/date/time bookings, including attempts to rebook an identical cancelled slot. Dates and times use the service's local timezone; configure the runtime timezone consistently across instances.
