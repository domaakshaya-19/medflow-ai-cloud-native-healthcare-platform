package com.medflow.appointment;

import com.medflow.appointment.client.DoctorClient;
import com.medflow.appointment.client.DoctorReferenceService;
import com.medflow.appointment.exception.ApiException;
import feign.FeignException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DoctorReferenceServiceTest {
    private static final String AUTHORIZATION = "Bearer caller-token";

    @Test void doctorLookupForwardsExactCallerTokenToDirectoryOnly() throws Exception {
        DoctorClient client = mock(DoctorClient.class);
        UUID doctor = UUID.randomUUID();
        when(client.directoryEntry(doctor, AUTHORIZATION)).thenReturn(new DoctorClient.DoctorDirectoryReference(doctor));
        new DoctorReferenceService(client).requireDoctor(doctor, AUTHORIZATION);
        verify(client).directoryEntry(doctor, AUTHORIZATION);
        verifyNoMoreInteractions(client);
        assertArrayEquals(new String[]{"/api/doctors/directory/{id}"}, DoctorClient.class
                .getMethod("directoryEntry", UUID.class, String.class).getAnnotation(GetMapping.class).value());
    }

    @Test void missingDoctorIsTranslatedToNotFound() {
        DoctorClient client = mock(DoctorClient.class);
        when(client.directoryEntry(any(), anyString())).thenThrow(mock(FeignException.NotFound.class));
        ApiException error = assertThrows(ApiException.class,
                () -> new DoctorReferenceService(client).requireDoctor(UUID.randomUUID(), AUTHORIZATION));
        assertEquals(404, error.status().value());
    }

    @Test void downstreamAuthorizationFailuresNeverRetryWithElevatedCredentials() {
        for (Class<? extends FeignException> failure : java.util.List.of(
                FeignException.Unauthorized.class, FeignException.Forbidden.class)) {
            DoctorClient client = mock(DoctorClient.class);
            UUID doctor = UUID.randomUUID();
            when(client.directoryEntry(doctor, AUTHORIZATION)).thenThrow(mock(failure));
            ApiException error = assertThrows(ApiException.class,
                    () -> new DoctorReferenceService(client).requireDoctor(doctor, AUTHORIZATION));
            assertEquals(502, error.status().value());
            assertEquals("Doctor Service unavailable", error.getMessage());
            assertFalse(error.getMessage().contains(AUTHORIZATION));
            verify(client).directoryEntry(doctor, AUTHORIZATION);
            verifyNoMoreInteractions(client);
        }
    }

    @Test void invalidDirectoryResponsesAreRejected() {
        DoctorClient client = mock(DoctorClient.class);
        UUID doctor = UUID.randomUUID();
        when(client.directoryEntry(doctor, AUTHORIZATION)).thenReturn(null,
                new DoctorClient.DoctorDirectoryReference(UUID.randomUUID()));
        DoctorReferenceService service = new DoctorReferenceService(client);
        assertEquals(502, assertThrows(ApiException.class,
                () -> service.requireDoctor(doctor, AUTHORIZATION)).status().value());
        assertEquals(502, assertThrows(ApiException.class,
                () -> service.requireDoctor(doctor, AUTHORIZATION)).status().value());
    }

    @Test void currentDoctorLookupStillForwardsOwnToken() {
        DoctorClient client = mock(DoctorClient.class);
        UUID doctor = UUID.randomUUID();
        when(client.me(AUTHORIZATION)).thenReturn(new DoctorClient.DoctorReference(doctor, UUID.randomUUID()));
        assertEquals(doctor, new DoctorReferenceService(client).profileIdForCurrentDoctor(AUTHORIZATION));
        verify(client).me(AUTHORIZATION);
        verifyNoMoreInteractions(client);
    }
}
