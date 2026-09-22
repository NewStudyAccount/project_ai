package com.example.blog.content.mapper;

import com.example.blog.content.entity.SysUserRef;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserRefMapper {

    @Select("SELECT * FROM sys_user_ref WHERE user_id = #{userId}")
    SysUserRef selectByUserId(@Param("userId") Long userId);

    @Select("<script>SELECT * FROM sys_user_ref WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<SysUserRef> selectByUserIds(@Param("userIds") Collection<Long> userIds);

    int upsert(SysUserRef row);
}
