package com.medflow.lab.service;
import com.medflow.lab.dto.*;
import com.medflow.lab.entity.*;
import com.medflow.lab.exception.ApiException;
import com.medflow.lab.repository.LabResultRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class LabResultService {
    private final LabResultRepository repository;private final LabOrderService orders;
    public LabResultService(LabResultRepository repository,LabOrderService orders){this.repository=repository;this.orders=orders;}
    @Transactional public LabResultResponse create(LabResultCreateRequest request,UUID currentDoctor,boolean admin){LabOrder order=orders.order(request.orderId());if(!admin&&!order.doctorId.equals(currentDoctor))throw forbidden();if(order.status==LabOrderStatus.CANCELLED)throw new ApiException(HttpStatus.CONFLICT,"Cannot add a result to a cancelled lab order");if(repository.existsByOrderId(request.orderId()))throw new ApiException(HttpStatus.CONFLICT,"Lab result already exists for this order");LabResult result=new LabResult();result.orderId=order.id;result.patientId=order.patientId;result.doctorId=order.doctorId;copy(result,request.resultSummary(),request.details(),request.referenceRange(),request.abnormal(),request.resultDate());return view(repository.saveAndFlush(result));}
    @Transactional(readOnly=true) public LabResult result(UUID id){return repository.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Lab result not found"));}
    @Transactional(readOnly=true) public LabResultResponse get(UUID id){return view(result(id));}
    @Transactional(readOnly=true) public LabResultResponse byOrder(UUID id){return view(repository.findByOrderId(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Lab result not found")));}
    @Transactional(readOnly=true) public List<LabResultResponse> patient(UUID id){return repository.findByPatientIdOrderByResultDateDesc(id).stream().map(LabResultService::view).toList();}
    @Transactional(readOnly=true) public List<LabResultResponse> doctor(UUID id){return repository.findByDoctorIdOrderByResultDateDesc(id).stream().map(LabResultService::view).toList();}
    @Transactional(readOnly=true) public List<LabResultResponse> all(){return repository.findAll().stream().map(LabResultService::view).toList();}
    @Transactional public LabResultResponse update(UUID id,LabResultUpdateRequest request){LabResult r=result(id);if(request.resultSummary()!=null){if(request.resultSummary().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"resultSummary must not be blank");r.resultSummary=request.resultSummary().trim();}if(request.details()!=null)r.details=request.details();if(request.referenceRange()!=null)r.referenceRange=request.referenceRange();if(request.abnormal()!=null)r.abnormal=request.abnormal();if(request.resultDate()!=null)r.resultDate=request.resultDate();return view(repository.saveAndFlush(r));}
    private static void copy(LabResult r,String summary,String details,String range,boolean abnormal,Instant date){r.resultSummary=summary.trim();r.details=details;r.referenceRange=range;r.abnormal=abnormal;r.resultDate=date;}
    private static LabResultResponse view(LabResult r){return new LabResultResponse(r.id,r.orderId,r.patientId,r.doctorId,r.resultSummary,r.details,r.referenceRange,r.abnormal,r.resultDate,r.createdAt,r.updatedAt);}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
}
