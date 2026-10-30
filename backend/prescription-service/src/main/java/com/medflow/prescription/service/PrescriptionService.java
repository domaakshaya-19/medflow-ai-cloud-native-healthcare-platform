package com.medflow.prescription.service;

import com.medflow.prescription.client.ClinicalReferenceService;
import com.medflow.prescription.dto.MedicineRequest;
import com.medflow.prescription.dto.MedicineResponse;
import com.medflow.prescription.dto.PrescriptionCreateRequest;
import com.medflow.prescription.dto.PrescriptionResponse;
import com.medflow.prescription.dto.PrescriptionUpdateRequest;
import com.medflow.prescription.entity.Prescription;
import com.medflow.prescription.entity.PrescriptionMedicine;
import com.medflow.prescription.entity.PrescriptionStatus;
import com.medflow.prescription.exception.ApiException;
import com.medflow.prescription.repository.PrescriptionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionService {
    private final PrescriptionRepository prescriptions;
    private final ClinicalReferenceService references;

    public PrescriptionService(PrescriptionRepository prescriptions, ClinicalReferenceService references) {
        this.prescriptions = prescriptions;
        this.references = references;
    }

    @Transactional
    public PrescriptionResponse create(PrescriptionCreateRequest request, UUID currentDoctorId,
                                       boolean admin, String authorization) {
        if (!admin && !request.doctorId().equals(currentDoctorId))
            throw new ApiException(HttpStatus.FORBIDDEN, "Doctors can create prescriptions only for their own profile");
        if (prescriptions.existsByAppointmentId(request.appointmentId()))
            throw new ApiException(HttpStatus.CONFLICT, "A prescription already exists for this appointment");
        references.validatePrescriptionReferences(request.appointmentId(), request.patientId(), request.doctorId(), authorization);

        Prescription prescription = new Prescription();
        prescription.patientId = request.patientId();
        prescription.doctorId = request.doctorId();
        prescription.appointmentId = request.appointmentId();
        prescription.prescriptionDate = LocalDate.now();
        prescription.diagnosis = request.diagnosis().trim();
        prescription.notes = trimToNull(request.notes());
        prescription.status = PrescriptionStatus.ACTIVE;
        prescription.replaceMedicines(request.medicines().stream().map(this::medicine).toList());
        try {
            return response(prescriptions.saveAndFlush(prescription));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "A prescription already exists for this appointment");
        }
    }

    @Transactional(readOnly = true)
    public Prescription getEntity(UUID id) {
        Prescription prescription = prescriptions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
        prescription.medicines.size();
        return prescription;
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse get(UUID id) { return response(getEntity(id)); }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> patient(UUID patientId) {
        return prescriptions.findByPatientIdOrderByPrescriptionDateDescCreatedAtDesc(patientId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> doctor(UUID doctorId) {
        return prescriptions.findByDoctorIdOrderByPrescriptionDateDescCreatedAtDesc(doctorId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> all() { return prescriptions.findAll().stream().map(this::response).toList(); }

    @Transactional(readOnly = true)
    public PrescriptionResponse appointment(UUID appointmentId) {
        return response(getByAppointment(appointmentId));
    }

    @Transactional(readOnly = true)
    public Prescription getByAppointment(UUID appointmentId) {
        Prescription prescription = prescriptions.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
        prescription.medicines.size();
        return prescription;
    }

    @Transactional
    public PrescriptionResponse update(UUID id, PrescriptionUpdateRequest request) {
        Prescription prescription = getEntity(id);
        if (prescription.status == PrescriptionStatus.CANCELLED)
            throw new ApiException(HttpStatus.CONFLICT, "Cancelled prescriptions cannot be modified or reactivated");
        if (request.diagnosis() != null) {
            if (request.diagnosis().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Diagnosis must not be blank");
            prescription.diagnosis = request.diagnosis().trim();
        }
        if (request.notes() != null) prescription.notes = trimToNull(request.notes());
        if (request.status() != null) prescription.status = request.status();
        if (request.medicines() != null)
            prescription.replaceMedicines(request.medicines().stream().map(this::medicine).toList());
        return response(prescription);
    }

    @Transactional
    public PrescriptionResponse cancel(UUID id) {
        Prescription prescription = getEntity(id);
        if (prescription.status == PrescriptionStatus.CANCELLED)
            throw new ApiException(HttpStatus.CONFLICT, "Prescription is already cancelled");
        prescription.status = PrescriptionStatus.CANCELLED;
        return response(prescription);
    }

    private PrescriptionMedicine medicine(MedicineRequest request) {
        PrescriptionMedicine medicine = new PrescriptionMedicine();
        medicine.medicineName = request.medicineName().trim();
        medicine.dosage = request.dosage().trim();
        medicine.frequency = request.frequency().trim();
        medicine.duration = request.duration().trim();
        medicine.instructions = trimToNull(request.instructions());
        return medicine;
    }

    private PrescriptionResponse response(Prescription prescription) {
        List<MedicineResponse> medicines = prescription.medicines.stream()
                .map(m -> new MedicineResponse(m.id, m.medicineName, m.dosage, m.frequency, m.duration, m.instructions)).toList();
        return new PrescriptionResponse(prescription.id, prescription.patientId, prescription.doctorId,
                prescription.appointmentId, prescription.prescriptionDate, prescription.diagnosis, prescription.notes,
                prescription.status, medicines, prescription.createdAt, prescription.updatedAt);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
