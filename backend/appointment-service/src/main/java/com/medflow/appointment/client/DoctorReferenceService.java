package com.medflow.appointment.client;

import com.medflow.appointment.exception.ApiException;
import feign.FeignException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DoctorReferenceService {
    private final DoctorClient client;

    public DoctorReferenceService(DoctorClient client) {
        this.client = client;
    }

    public void requireDoctor(UUID doctorId, String authorization) {
        try {
            DoctorClient.DoctorDirectoryReference doctor = client.directoryEntry(doctorId, authorization);
            if (doctor == null || !doctorId.equals(doctor.id()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Doctor Service response");
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Doctor not found");
        } catch (FeignException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Doctor Service unavailable");
        }
    }

    public UUID profileIdForCurrentDoctor(String authorization) {
        try {
            DoctorClient.DoctorReference doctor = client.me(authorization);
            if (doctor == null || doctor.id() == null)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Doctor Service response");
            return doctor.id();
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found");
        } catch (FeignException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Doctor Service unavailable");
        }
    }

}
