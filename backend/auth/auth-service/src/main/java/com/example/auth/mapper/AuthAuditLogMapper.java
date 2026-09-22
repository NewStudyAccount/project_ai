package com.example.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.entity.AuthAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthAuditLogMapper extends BaseMapper<AuthAuditLog> {
}
