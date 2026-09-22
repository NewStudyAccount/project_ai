package com.example.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.entity.AuthGrant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthGrantMapper extends BaseMapper<AuthGrant> {
}
