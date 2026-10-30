package com.medflow.lab.dto;
import com.medflow.lab.entity.LabOrderStatus;
import jakarta.validation.constraints.Size;
public record LabOrderUpdateRequest(@Size(max=250) String testName,@Size(max=2000) String instructions,
        LabOrderStatus status) {}
