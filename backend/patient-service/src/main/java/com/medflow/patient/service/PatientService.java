package com.medflow.patient.service;

import com.medflow.patient.dto.*;
import com.medflow.patient.entity.Patient;
import com.medflow.patient.exception.ApiException;
import com.medflow.patient.repository.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PatientService {
 private final PatientRepository repository;
 public PatientService(PatientRepository repository){this.repository=repository;}
 @Transactional public PatientResponse createPatient(UUID userId,PatientCreateRequest r){
  if(repository.existsByUserId(userId)) throw new ApiException(HttpStatus.CONFLICT,"Patient profile already exists");
  Patient p=new Patient();p.userId=userId;copy(p,r.firstName(),r.lastName(),r.dateOfBirth(),r.gender(),r.phone(),r.email(),r.address(),r.bloodGroup(),r.emergencyContactName(),r.emergencyContactPhone(),r.allergies(),r.medicalHistorySummary());return view(repository.saveAndFlush(p));
 }
 @Transactional(readOnly=true) public PatientResponse getPatientById(UUID id){return view(find(id));}
 @Transactional(readOnly=true) public PatientResponse getPatientByUserId(UUID userId){return view(repository.findByUserId(userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Patient profile not found")));}
 @Transactional public PatientResponse updatePatient(UUID id,PatientUpdateRequest r){Patient p=find(id);copy(p,r.firstName(),r.lastName(),r.dateOfBirth(),r.gender(),r.phone(),r.email(),r.address(),r.bloodGroup(),r.emergencyContactName(),r.emergencyContactPhone(),r.allergies(),r.medicalHistorySummary());return view(repository.saveAndFlush(p));}
 @Transactional public void deletePatient(UUID id){repository.delete(find(id));}
 @Transactional(readOnly=true) public List<PatientResponse> getAllPatients(){return repository.findAll().stream().map(PatientService::view).toList();}
 @Transactional(readOnly=true) public UUID owner(UUID id){return find(id).userId;}
 private Patient find(UUID id){return repository.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Patient profile not found"));}
 private static void copy(Patient p,String first,String last,java.time.LocalDate dob,String gender,String phone,String email,String address,String blood,String contact,String contactPhone,String allergies,String history){p.firstName=first.trim();p.lastName=last.trim();p.dateOfBirth=dob;p.gender=gender;p.phone=phone;p.email=email.trim();p.address=address;p.bloodGroup=blood;p.emergencyContactName=contact;p.emergencyContactPhone=contactPhone;p.allergies=allergies;p.medicalHistorySummary=history;}
 private static PatientResponse view(Patient p){return new PatientResponse(p.id,p.userId,p.firstName,p.lastName,p.dateOfBirth,p.gender,p.phone,p.email,p.address,p.bloodGroup,p.emergencyContactName,p.emergencyContactPhone,p.allergies,p.medicalHistorySummary,p.createdAt,p.updatedAt);}
}
