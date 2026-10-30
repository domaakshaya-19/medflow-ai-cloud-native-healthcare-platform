package com.medflow.lab.controller;
import com.medflow.lab.client.ClinicalReferenceService;
import com.medflow.lab.dto.*;
import com.medflow.lab.entity.*;
import com.medflow.lab.exception.ApiException;
import com.medflow.lab.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/lab")
public class LabController {
    private final LabOrderService orders;private final LabResultService results;private final ClinicalReferenceService references;
    public LabController(LabOrderService orders,LabResultService results,ClinicalReferenceService references){this.orders=orders;this.results=results;this.references=references;}
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) public LabOrderResponse createOrder(@Valid @RequestBody LabOrderCreateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){if(role(auth,"ADMIN"))return orders.create(request,null,null,true,authorization);if(role(auth,"PATIENT"))return orders.create(request,patient(auth,authorization),null,false,authorization);require(auth,"DOCTOR");return orders.create(request,null,doctor(auth,authorization),false,authorization);}
    @GetMapping("/orders/me") public List<LabOrderResponse> myOrders(Authentication auth,@RequestHeader("Authorization")String authorization){require(auth,"PATIENT");return orders.patient(patient(auth,authorization));}
    @GetMapping("/orders") public List<LabOrderResponse> orders(Authentication auth,@RequestHeader(value="Authorization",required=false)String authorization){if(role(auth,"ADMIN"))return orders.all();require(auth,"DOCTOR");return orders.doctor(doctor(auth,authorization));}
    @GetMapping("/orders/{id}") public LabOrderResponse order(@PathVariable UUID id,Authentication auth,@RequestHeader("Authorization")String authorization){LabOrder o=orders.order(id);check(o.patientId,o.doctorId,auth,authorization);return LabOrderService.view(o);}
    @PutMapping("/orders/{id}") public LabOrderResponse updateOrder(@PathVariable UUID id,@Valid @RequestBody LabOrderUpdateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){LabOrder o=orders.order(id);manage(o.doctorId,auth,authorization);return orders.update(id,request);}
    @DeleteMapping("/orders/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteOrder(@PathVariable UUID id,Authentication auth){require(auth,"ADMIN");orders.delete(id);}
    @PostMapping("/results") @ResponseStatus(HttpStatus.CREATED) public LabResultResponse createResult(@Valid @RequestBody LabResultCreateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){if(role(auth,"ADMIN"))return results.create(request,null,true);require(auth,"DOCTOR");return results.create(request,doctor(auth,authorization),false);}
    @GetMapping("/results/me") public List<LabResultResponse> myResults(Authentication auth,@RequestHeader("Authorization")String authorization){require(auth,"PATIENT");return results.patient(patient(auth,authorization));}
    @GetMapping("/results") public List<LabResultResponse> results(Authentication auth,@RequestHeader(value="Authorization",required=false)String authorization){if(role(auth,"ADMIN"))return results.all();require(auth,"DOCTOR");return results.doctor(doctor(auth,authorization));}
    @GetMapping("/results/order/{orderId}") public LabResultResponse resultByOrder(@PathVariable UUID orderId,Authentication auth,@RequestHeader("Authorization")String authorization){LabResult r=results.result(results.byOrder(orderId).id());check(r.patientId,r.doctorId,auth,authorization);return results.get(r.id);}
    @GetMapping("/results/{id}") public LabResultResponse result(@PathVariable UUID id,Authentication auth,@RequestHeader("Authorization")String authorization){LabResult r=results.result(id);check(r.patientId,r.doctorId,auth,authorization);return results.get(id);}
    @PutMapping("/results/{id}") public LabResultResponse updateResult(@PathVariable UUID id,@Valid @RequestBody LabResultUpdateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){LabResult r=results.result(id);manage(r.doctorId,auth,authorization);return results.update(id,request);}
    private void check(UUID patientId,UUID doctorId,Authentication auth,String authorization){if(role(auth,"ADMIN"))return;if(role(auth,"PATIENT")&&patientId.equals(patient(auth,authorization)))return;if(role(auth,"DOCTOR")&&doctorId.equals(doctor(auth,authorization)))return;throw forbidden();}
    private void manage(UUID doctorId,Authentication auth,String authorization){if(role(auth,"ADMIN"))return;require(auth,"DOCTOR");if(!doctorId.equals(doctor(auth,authorization)))throw forbidden();}
    private UUID patient(Authentication auth,String header){return references.patientProfileForCurrentUser((UUID)auth.getPrincipal(),header);}
    private UUID doctor(Authentication auth,String header){return references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),header);}
    private static boolean role(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
    private static void require(Authentication auth,String role){if(!role(auth,role))throw forbidden();}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
}
