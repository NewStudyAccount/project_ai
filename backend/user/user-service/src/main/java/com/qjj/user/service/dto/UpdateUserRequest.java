package com.qjj.user.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateUserRequest {

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

    @Min(0)
    @Max(2)
    private Integer gender;

    private LocalDateTime birthday;

    @Size(max = 255)
    private String address;

    @Size(max = 1024)
    private String extraJson;
}
