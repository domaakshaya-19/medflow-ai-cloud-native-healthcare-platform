package com.medflow.appointment.client;

import com.medflow.appointment.exception.ApiException;
import feign.FeignException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PatientReferenceService {
    private final PatientClient client;

    public PatientReferenceService(PatientClient client) {
        this.client = client;
    }

    public UUID profileIdForCurrentPatient(UUID authUserId, String authorization) {
        try {
            PatientClient.PatientReference patient = client.byAuthUser(authUserId, authorization);
            if (patient == null || patient.id() == null || !authUserId.equals(patient.userId()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Patient Service response");
            return patient.id();
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Patient profile not found");
        } catch (FeignException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Patient Service unavailable");
        }
    }
}
