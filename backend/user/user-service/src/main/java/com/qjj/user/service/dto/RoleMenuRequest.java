package com.qjj.user.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleMenuRequest {

    private List<String> menuIds = List.of();
}
