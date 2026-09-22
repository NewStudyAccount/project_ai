package com.example.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.entity.LoginAttempt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginAttemptMapper extends BaseMapper<LoginAttempt> {
}
