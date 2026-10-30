package com.medflow.appointment;
import com.medflow.appointment.client.DoctorReferenceService;
import com.medflow.appointment.dto.*;
import com.medflow.appointment.entity.AppointmentStatus;
import com.medflow.appointment.exception.ApiException;
import com.medflow.appointment.repository.AppointmentRepository;
import com.medflow.appointment.service.AppointmentService;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:booking-locks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1") class BookingConcurrencyTest{
 @Autowired AppointmentService service;@Autowired AppointmentRepository repository;@MockitoBean DoctorReferenceService doctors;
 AppointmentCreateRequest request(UUID doctor,LocalTime time){return new AppointmentCreateRequest(doctor,LocalDate.now().plusDays(3),time,"Synthetic concurrency test",null);}
 @Test void twoPatientsCannotBookTheSameDoctorStartTime(){UUID doctor=UUID.randomUUID();service.create(UUID.randomUUID(),request(doctor,LocalTime.of(10,0)),"Bearer test-fixture");assertThrows(ApiException.class,()->service.create(UUID.randomUUID(),request(doctor,LocalTime.of(10,0)),"Bearer test-fixture"));}
 @Test void onePatientCannotBookTwoDoctorsAtTheSameStartTime(){UUID patient=UUID.randomUUID();service.create(patient,request(UUID.randomUUID(),LocalTime.of(13,0)),"Bearer fixture");assertThrows(ApiException.class,()->service.create(patient,request(UUID.randomUUID(),LocalTime.of(13,0)),"Bearer fixture"));}
 @Test void reschedulingCannotOccupyAnotherPatientsStartTime(){UUID doctor=UUID.randomUUID();var first=service.create(UUID.randomUUID(),request(doctor,LocalTime.of(10,0)),"Bearer fixture");var second=service.create(UUID.randomUUID(),request(doctor,LocalTime.of(11,0)),"Bearer fixture");assertThrows(ApiException.class,()->service.update(second.id(),new AppointmentUpdateRequest(null,LocalTime.of(10,0),null,null,null),"PATIENT"));assertEquals(LocalTime.of(11,0),service.get(second.id()).appointmentTime());}
 @Test void simultaneousRequestsHaveOneWinner()throws Exception{UUID doctor=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);try{Callable<Integer> action=()->{gate.await();try{service.create(UUID.randomUUID(),request(doctor,LocalTime.of(12,0)),"Bearer fixture");return 201;}catch(ApiException|org.springframework.dao.DataIntegrityViolationException e){return 409;}};var a=pool.submit(action);var b=pool.submit(action);gate.countDown();var results=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,results.stream().filter(s->s==201).count());assertEquals(1,repository.findByDoctorIdOrderByAppointmentDateAscQueueNumberAsc(doctor).size());}finally{pool.shutdownNow();}}
 @Test void availabilityExcludesOccupiedStartTimes(){UUID doctor=UUID.randomUUID();service.create(UUID.randomUUID(),request(doctor,LocalTime.of(10,0)),"Bearer fixture");var result=service.availability(doctor,LocalDate.now().plusDays(3),"Bearer fixture");assertFalse(((List<?>)result.get("availableStartTimes")).contains("10:00"));assertEquals(false,result.get("workingHoursConfigured"));}
}
