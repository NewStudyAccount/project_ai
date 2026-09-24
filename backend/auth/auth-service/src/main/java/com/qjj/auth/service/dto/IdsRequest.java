package com.qjj.auth.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class IdsRequest {
    @NotNull
    private List<String> ids;
}
