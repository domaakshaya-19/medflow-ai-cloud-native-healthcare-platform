package com.medflow.medicalrecords.service;

import com.medflow.medicalrecords.client.ClinicalReferenceService;
import com.medflow.medicalrecords.dto.*;
import com.medflow.medicalrecords.entity.MedicalRecord;
import com.medflow.medicalrecords.exception.ApiException;
import com.medflow.medicalrecords.repository.MedicalRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {
    private final MedicalRecordRepository repository;
    private final ClinicalReferenceService references;
    public MedicalRecordService(MedicalRecordRepository repository, ClinicalReferenceService references) {
        this.repository = repository; this.references = references;
    }

    @Transactional
    public MedicalRecordResponse create(MedicalRecordCreateRequest request, UUID authenticatedDoctorId,
            boolean admin, String authorization) {
        if (!admin && !request.doctorId().equals(authenticatedDoctorId))
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        if (repository.existsByAppointmentId(request.appointmentId()))
            throw new ApiException(HttpStatus.CONFLICT, "Medical record already exists for this appointment");
        references.validateAppointment(request.appointmentId(), request.patientId(), request.doctorId(), authorization);
        MedicalRecord record = new MedicalRecord();
        record.patientId=request.patientId(); record.doctorId=request.doctorId(); record.appointmentId=request.appointmentId();
        record.diagnosis=request.diagnosis().trim(); record.symptoms=request.symptoms();
        record.treatmentPlan=request.treatmentPlan(); record.medications=request.medications(); record.notes=request.notes();
        record.visitDate=request.visitDate();
        return view(repository.saveAndFlush(record));
    }

    @Transactional(readOnly=true) public MedicalRecord record(UUID id){return repository.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Medical record not found"));}
    @Transactional(readOnly=true) public MedicalRecordResponse get(UUID id){return view(record(id));}
    @Transactional(readOnly=true) public List<MedicalRecordResponse> patientRecords(UUID patientId){return repository.findByPatientIdOrderByVisitDateDescCreatedAtDesc(patientId).stream().map(MedicalRecordService::view).toList();}
    @Transactional(readOnly=true) public List<MedicalRecordResponse> patientRecordsForDoctor(UUID patientId,UUID doctorId){return repository.findByPatientIdOrderByVisitDateDescCreatedAtDesc(patientId).stream().filter(r->r.doctorId.equals(doctorId)).map(MedicalRecordService::view).toList();}
    @Transactional(readOnly=true) public List<MedicalRecordResponse> doctorRecords(UUID doctorId){return repository.findByDoctorIdOrderByVisitDateDescCreatedAtDesc(doctorId).stream().map(MedicalRecordService::view).toList();}
    @Transactional(readOnly=true) public MedicalRecordResponse appointmentRecord(UUID appointmentId){return view(repository.findByAppointmentId(appointmentId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Medical record not found")));}
    @Transactional(readOnly=true) public List<MedicalRecordResponse> all(){return repository.findAll().stream().map(MedicalRecordService::view).toList();}

    @Transactional
    public MedicalRecordResponse update(UUID id,MedicalRecordUpdateRequest request){MedicalRecord r=record(id);if(request.diagnosis()!=null){if(request.diagnosis().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"diagnosis must not be blank");r.diagnosis=request.diagnosis().trim();}if(request.symptoms()!=null)r.symptoms=request.symptoms();if(request.treatmentPlan()!=null)r.treatmentPlan=request.treatmentPlan();if(request.medications()!=null)r.medications=request.medications();if(request.notes()!=null)r.notes=request.notes();return view(repository.saveAndFlush(r));}
    @Transactional public void delete(UUID id){repository.delete(record(id));}

    private static MedicalRecordResponse view(MedicalRecord r){return new MedicalRecordResponse(r.id,r.patientId,r.doctorId,r.appointmentId,r.diagnosis,r.symptoms,r.treatmentPlan,r.medications,r.notes,r.visitDate,r.createdAt,r.updatedAt);}
}
