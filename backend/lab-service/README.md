# Lab Service

Lab Service runs on port `8086`, registers in Eureka as `lab-service`, and owns the `medflow_lab` PostgreSQL database. It manages lab orders and one result per order.

`patientId` and `doctorId` are profile UUIDs. The JWT subject remains the Auth User UUID. Patient and doctor profile IDs are resolved through Patient and Doctor services; order creation also verifies that Appointment Service returns the same patient and doctor and that the appointment is `CONFIRMED`, `IN_PROGRESS`, or `COMPLETED`.

Patients can read their own data. Doctors can create and manage orders/results assigned to their Doctor profile. Admins can manage all records. Actuator health is public; all `/api/lab/**` routes require a valid shared-contract JWT.

## Configuration

```text
LAB_DB_URL=jdbc:postgresql://localhost:5432/medflow_lab
LAB_DB_USER=medflow_lab
LAB_DB_PASSWORD=<local password>
LAB_SERVICE_PORT=8086
EUREKA_SERVER_URL=http://localhost:8761/eureka/
JWT_SECRET=<same secret as Auth, Patient, Doctor, and Appointment services>
```

```sql
CREATE USER medflow_lab WITH PASSWORD '<local password>';
CREATE DATABASE medflow_lab OWNER medflow_lab;
```

## Endpoints

```text
POST   /api/lab/orders
GET    /api/lab/orders/me
GET    /api/lab/orders
GET    /api/lab/orders/{id}
PUT    /api/lab/orders/{id}
DELETE /api/lab/orders/{id}
POST   /api/lab/results
GET    /api/lab/results/me
GET    /api/lab/results
GET    /api/lab/results/order/{orderId}
GET    /api/lab/results/{id}
PUT    /api/lab/results/{id}
GET    /actuator/health
```

From the repository root, run `mvn -pl backend/lab-service test` and then `mvn -pl backend/lab-service spring-boot:run`. Start Eureka, Patient, Doctor, and Appointment services first. Do not store real passwords, JWTs, or secrets in source control.
