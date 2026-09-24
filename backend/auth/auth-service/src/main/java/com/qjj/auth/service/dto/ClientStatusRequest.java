package com.qjj.auth.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClientStatusRequest {
    @NotNull
    private Integer enabled;
}
