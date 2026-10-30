package com.medflow.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medflow.appointment.client.PatientClient;
import com.medflow.appointment.client.PatientReferenceService;
import com.medflow.appointment.exception.ApiException;
import feign.FeignException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientReferenceServiceTest {
    private static final String AUTHORIZATION = "Bearer caller-token";

    @Test
    void resolvesProfileIdFromAuthenticatedUserAndForwardsAuthorization() {
        PatientClient client = mock(PatientClient.class);
        UUID authUserId = UUID.randomUUID();
        UUID patientProfileId = UUID.randomUUID();
        when(client.byAuthUser(authUserId, AUTHORIZATION))
                .thenReturn(new PatientClient.PatientReference(patientProfileId, authUserId));

        UUID resolved = new PatientReferenceService(client)
                .profileIdForCurrentPatient(authUserId, AUTHORIZATION);

        assertEquals(patientProfileId, resolved);
        verify(client).byAuthUser(authUserId, AUTHORIZATION);
    }

    @Test
    void rejectsMismatchedAuthUserInPatientResponse() {
        PatientClient client = mock(PatientClient.class);
        when(client.byAuthUser(any(), anyString()))
                .thenReturn(new PatientClient.PatientReference(UUID.randomUUID(), UUID.randomUUID()));

        ApiException error = assertThrows(ApiException.class, () -> new PatientReferenceService(client)
                .profileIdForCurrentPatient(UUID.randomUUID(), AUTHORIZATION));

        assertEquals(502, error.status().value());
    }

    @Test
    void translatesMissingPatientProfileToNotFound() {
        PatientClient client = mock(PatientClient.class);
        when(client.byAuthUser(any(), anyString())).thenThrow(mock(FeignException.NotFound.class));

        ApiException error = assertThrows(ApiException.class, () -> new PatientReferenceService(client)
                .profileIdForCurrentPatient(UUID.randomUUID(), AUTHORIZATION));

        assertEquals(404, error.status().value());
    }

    @Test
    void translatesPatientServiceFailureToBadGateway() {
        PatientClient client = mock(PatientClient.class);
        when(client.byAuthUser(any(), anyString())).thenThrow(mock(FeignException.class));

        ApiException error = assertThrows(ApiException.class, () -> new PatientReferenceService(client)
                .profileIdForCurrentPatient(UUID.randomUUID(), AUTHORIZATION));

        assertEquals(502, error.status().value());
    }
}
