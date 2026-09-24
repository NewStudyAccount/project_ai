package com.qjj.user.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "用户名仅支持字母、数字及 . _ -")
    private String username;

    @Size(max = 64)
    private String realName;

    @Size(max = 64)
    private String nickname;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    @Size(max = 255)
    private String avatar;

    @Size(max = 255)
    private String remark;
}
