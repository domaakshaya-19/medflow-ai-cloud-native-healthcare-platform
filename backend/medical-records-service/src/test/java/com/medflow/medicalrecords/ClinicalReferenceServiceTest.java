package com.medflow.medicalrecords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.medflow.medicalrecords.client.AppointmentClient;
import com.medflow.medicalrecords.client.ClinicalReferenceService;
import com.medflow.medicalrecords.client.DoctorClient;
import com.medflow.medicalrecords.client.PatientClient;
import com.medflow.medicalrecords.exception.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalReferenceServiceTest {
    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";
    private static final String CALLER_TOKEN = "Bearer caller-token";

    @Test
    void validatesAppointmentUsingPatientProfileIdInsteadOfAuthUserLookup() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientProfileId = UUID.randomUUID();
        UUID patientAuthUserId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        FakePatientClient patients = new FakePatientClient(
                new PatientClient.PatientReference(patientProfileId, patientAuthUserId));
        ClinicalReferenceService references = service(patients, appointment(
                appointmentId, patientProfileId, doctorProfileId, "IN_PROGRESS"));

        references.validateAppointment(
                appointmentId, patientProfileId, doctorProfileId, CALLER_TOKEN);

        assertEquals(patientProfileId, patients.requestedProfileId);
        assertEquals(0, patients.byAuthUserCalls);
    }

    @Test
    void rejectsPatientProfileMismatch() {
        UUID appointmentId = UUID.randomUUID();
        UUID appointmentPatientId = UUID.randomUUID();
        UUID requestedPatientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        ClinicalReferenceService references = service(
                new FakePatientClient(new PatientClient.PatientReference(appointmentPatientId, UUID.randomUUID())),
                appointment(appointmentId, appointmentPatientId, doctorId, "COMPLETED"));

        ApiException error = assertThrows(ApiException.class, () -> references.validateAppointment(
                appointmentId, requestedPatientId, doctorId, CALLER_TOKEN));

        assertEquals(400, error.status().value());
    }

    @Test
    void rejectsDoctorProfileMismatch() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID appointmentDoctorId = UUID.randomUUID();
        ClinicalReferenceService references = service(
                new FakePatientClient(new PatientClient.PatientReference(patientId, UUID.randomUUID())),
                appointment(appointmentId, patientId, appointmentDoctorId, "COMPLETED"));

        ApiException error = assertThrows(ApiException.class, () -> references.validateAppointment(
                appointmentId, patientId, UUID.randomUUID(), CALLER_TOKEN));

        assertEquals(400, error.status().value());
    }

    @Test
    void rejectsAppointmentOutsideAllowedClinicalStates() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        ClinicalReferenceService references = service(
                new FakePatientClient(new PatientClient.PatientReference(patientId, UUID.randomUUID())),
                appointment(appointmentId, patientId, doctorId, "CONFIRMED"));

        ApiException error = assertThrows(ApiException.class, () -> references.validateAppointment(
                appointmentId, patientId, doctorId, CALLER_TOKEN));

        assertEquals(409, error.status().value());
    }

    private AppointmentClient.AppointmentReference appointment(
            UUID id, UUID patientId, UUID doctorId, String status) {
        return new AppointmentClient.AppointmentReference(
                id, patientId, doctorId, LocalDate.now(), status);
    }

    private ClinicalReferenceService service(
            FakePatientClient patients, AppointmentClient.AppointmentReference appointment) {
        AppointmentClient appointments = (id, authorization) -> appointment;
        DoctorClient doctors = authorization -> null;
        return new ClinicalReferenceService(patients, doctors, appointments, SECRET);
    }

    private static final class FakePatientClient implements PatientClient {
        private final PatientReference response;
        private UUID requestedProfileId;
        private int byAuthUserCalls;

        private FakePatientClient(PatientReference response) {
            this.response = response;
        }

        @Override
        public PatientReference byAuthUser(UUID authUserId, String authorization) {
            byAuthUserCalls++;
            return null;
        }

        @Override
        public PatientReference byId(UUID patientId, String authorization) {
            requestedProfileId = patientId;
            return response;
        }
    }
}
