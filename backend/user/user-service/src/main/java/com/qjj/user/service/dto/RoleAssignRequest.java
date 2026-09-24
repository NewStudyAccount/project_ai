package com.qjj.user.service.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RoleAssignRequest {

    @NotEmpty
    private List<String> roleIds;
}
