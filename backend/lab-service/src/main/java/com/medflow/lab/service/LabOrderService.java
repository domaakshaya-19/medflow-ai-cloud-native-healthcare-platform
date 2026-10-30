package com.medflow.lab.service;
import com.medflow.lab.client.ClinicalReferenceService;
import com.medflow.lab.dto.*;
import com.medflow.lab.entity.*;
import com.medflow.lab.exception.ApiException;
import com.medflow.lab.repository.LabOrderRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class LabOrderService {
    private final LabOrderRepository repository;private final ClinicalReferenceService references;
    public LabOrderService(LabOrderRepository repository,ClinicalReferenceService references){this.repository=repository;this.references=references;}
    @Transactional public LabOrderResponse create(LabOrderCreateRequest request,UUID currentPatient,UUID currentDoctor,boolean admin,String authorization){if(!admin&&currentPatient!=null&&!request.patientId().equals(currentPatient))throw forbidden();if(!admin&&currentDoctor!=null&&!request.doctorId().equals(currentDoctor))throw forbidden();if(!admin&&currentPatient==null&&currentDoctor==null)throw forbidden();references.validateOrderReferences(request.appointmentId(),request.patientId(),request.doctorId(),authorization);LabOrder order=new LabOrder();order.patientId=request.patientId();order.doctorId=request.doctorId();order.appointmentId=request.appointmentId();order.testName=request.testName().trim();order.instructions=request.instructions();order.status=LabOrderStatus.ORDERED;return view(repository.saveAndFlush(order));}
    @Transactional(readOnly=true) public LabOrder order(UUID id){return repository.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Lab order not found"));}
    @Transactional(readOnly=true) public LabOrderResponse get(UUID id){return view(order(id));}
    @Transactional(readOnly=true) public List<LabOrderResponse> patient(UUID id){return repository.findByPatientIdOrderByOrderedAtDesc(id).stream().map(LabOrderService::view).toList();}
    @Transactional(readOnly=true) public List<LabOrderResponse> doctor(UUID id){return repository.findByDoctorIdOrderByOrderedAtDesc(id).stream().map(LabOrderService::view).toList();}
    @Transactional(readOnly=true) public List<LabOrderResponse> all(){return repository.findAll().stream().map(LabOrderService::view).toList();}
    @Transactional public LabOrderResponse update(UUID id,LabOrderUpdateRequest request){LabOrder o=order(id);if(request.testName()!=null){if(request.testName().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"testName must not be blank");o.testName=request.testName().trim();}if(request.instructions()!=null)o.instructions=request.instructions();if(request.status()!=null)o.status=request.status();return view(repository.saveAndFlush(o));}
    @Transactional public void delete(UUID id){repository.delete(order(id));}
    public static LabOrderResponse view(LabOrder o){return new LabOrderResponse(o.id,o.patientId,o.doctorId,o.appointmentId,o.testName,o.instructions,o.status,o.orderedAt,o.createdAt,o.updatedAt);}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
}
