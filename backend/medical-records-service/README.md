# Medical Records Service

The Medical Records Service owns clinical records created from completed or in-progress appointments. It runs on port `8085`, registers as `medical-records-service` in Eureka, and is exposed through the gateway at `/api/medical-records/**`.

## Data ownership and authorization

- `PATIENT` can read only records belonging to their Patient Service profile.
- `DOCTOR` can create records only with their own Doctor Service profile ID, read records they created, and update only their records.
- `ADMIN` can create, read, update, and delete any record.
- One medical record is allowed per appointment.
- A new record is accepted only when Appointment Service confirms that the appointment matches the supplied patient and doctor and has status `IN_PROGRESS` or `COMPLETED`.

JWTs use the Auth Service contract: the subject is the auth user's UUID and the `email` and `role` claims are required. All services must use the same `JWT_SECRET`. For cross-service validation, this service issues a short-lived internal `ADMIN` token signed with that shared secret and retrieves the Patient Service profile by the profile UUID stored on the appointment. This is appropriate for this academic deployment; a production deployment should use service credentials or workload identity instead of a shared user-token secret.

## Configuration

Set these environment variables before starting the service:

```text
MEDICAL_RECORDS_DB_URL=jdbc:postgresql://localhost:5432/medflow_medical_records
MEDICAL_RECORDS_DB_USER=medflow_medical_records
MEDICAL_RECORDS_DB_PASSWORD=<your local password>
MEDICAL_RECORDS_SERVICE_PORT=8085
EUREKA_SERVER_URL=http://localhost:8761/eureka/
JWT_SECRET=<the same secret used by Auth, Patient, Doctor, and Appointment services>
```

Example database setup (choose your own local password):

```sql
CREATE USER medflow_medical_records WITH PASSWORD '<your local password>';
CREATE DATABASE medflow_medical_records OWNER medflow_medical_records;
```

## API

```text
POST   /api/medical-records
GET    /api/medical-records/me
GET    /api/medical-records/patient/{patientId}
GET    /api/medical-records/doctor/me
GET    /api/medical-records/appointment/{appointmentId}
GET    /api/medical-records/{id}
PUT    /api/medical-records/{id}
DELETE /api/medical-records/{id}
GET    /actuator/health
```

IDs supplied in create requests are profile IDs from Patient and Doctor services, while the appointment ID comes from Appointment Service. The doctor must send the Bearer token obtained from Auth Service.

## Build and run

From the repository root:

```powershell
mvn -pl backend/medical-records-service test
mvn -pl backend/medical-records-service -DskipTests package
mvn -pl backend/medical-records-service spring-boot:run
```

Start Eureka, Auth, Patient, Doctor, and Appointment services before testing clinical record creation. Confirm service health directly or through the gateway:

```powershell
curl.exe http://localhost:8085/actuator/health
curl.exe http://localhost:8080/actuator/health
```

Do not put passwords, JWTs, or the shared JWT secret in source control or command history. Use local environment variables and replace example placeholders locally.
