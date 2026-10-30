# Prescription Service

Spring Boot service for MedFlow AI prescriptions and prescription medicines.

## Runtime

- Service name: `prescription-service`
- Default port: `8087`
- Database: PostgreSQL `medflow_prescription`
- Discovery: Eureka
- Authentication: the shared MedFlow JWT contract (`sub` is the Auth user UUID, with `email` and `role` claims)

Set `PRESCRIPTION_DB_URL`, `PRESCRIPTION_DB_USER`, `PRESCRIPTION_DB_PASSWORD`, `JWT_SECRET`, and optionally `PRESCRIPTION_SERVICE_PORT` and `EUREKA_SERVER_URL` before starting the service.

```powershell
mvn -pl backend/prescription-service spring-boot:run
```

The public health check is `GET http://localhost:8087/actuator/health`. All prescription endpoints require a valid JWT. Doctors create and manage prescriptions for their own doctor profile, patients have read-only access to their own profile records, and administrators can manage all records.

## Verification

```powershell
mvn -pl backend/prescription-service test
mvn -DskipTests compile
```
