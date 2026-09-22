package com.example.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.content.entity.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Select("SELECT COUNT(*) FROM sys_role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    long countByRoleAndPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    int deleteByRoleAndPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
