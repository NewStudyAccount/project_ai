package com.example.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.entity.OauthClient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OauthClientMapper extends BaseMapper<OauthClient> {
}
