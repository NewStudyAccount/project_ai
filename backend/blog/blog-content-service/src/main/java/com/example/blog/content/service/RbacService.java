package com.example.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.blog.common.audit.CurrentUser;
import com.example.blog.common.audit.IdService;
import com.example.blog.common.auth.BlogConstants;
import com.example.blog.common.error.BizException;
import com.example.blog.common.page.PageQuery;
import com.example.blog.common.result.PageResult;
import com.example.blog.content.entity.SysPermission;
import com.example.blog.content.entity.SysRole;
import com.example.blog.content.entity.SysRolePermission;
import com.example.blog.content.entity.SysUserRole;
import com.example.blog.content.mapper.SysPermissionMapper;
import com.example.blog.content.mapper.SysRoleMapper;
import com.example.blog.content.mapper.SysRolePermissionMapper;
import com.example.blog.content.mapper.SysUserRoleMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本地 RBAC：角色 / 权限 / 用户-角色 / 角色-权限（system_code=blog）。 */
@Service
public class RbacService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final IdService idService;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public RbacService(
            SysRoleMapper roleMapper,
            SysPermissionMapper permissionMapper,
            SysUserRoleMapper userRoleMapper,
            SysRolePermissionMapper rolePermissionMapper,
            IdService idService,
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.idService = idService;
        this.redis = redis;
    }

    public PageResult<SysRole> pageRoles(PageQuery query) {
        long offset = (query.getCurrent() - 1) * query.getSize();
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getSystemCode, BlogConstants.SYSTEM_CODE)
                .orderByAsc(SysRole::getSort)
                .last("LIMIT " + offset + "," + query.getSize());
        List<SysRole> records = roleMapper.selectList(qw);
        Long total = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getSystemCode, BlogConstants.SYSTEM_CODE));
        return PageResult.of(records, total == null ? 0 : total, query.getSize(), query.getCurrent());
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getSystemCode, BlogConstants.SYSTEM_CODE)
                .orderByAsc(SysRole::getSort));
    }

    @Transactional
    public SysRole createRole(String roleCode, String roleName, String remark) {
        if (roleCode == null || roleCode.isBlank()) {
            throw BizException.badParam("roleCode 不能为空");
        }
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getSystemCode, BlogConstants.SYSTEM_CODE)
                .eq(SysRole::getRoleCode, roleCode));
        if (exists != null && exists > 0) {
            throw BizException.badParam("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setId(idService.nextId("sys_role"));
        role.setSystemCode(BlogConstants.SYSTEM_CODE);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName == null ? roleCode : roleName);
        role.setSort(0);
        role.setStatus(1);
        role.setDataScope(1);
        role.setRemark(remark == null ? "" : remark);
        role.setDeleted(0);
        roleMapper.insert(role);
        return role;
    }

    public PageResult<SysPermission> pagePermissions(PageQuery query) {
        long offset = (query.getCurrent() - 1) * query.getSize();
        LambdaQueryWrapper<SysPermission> qw = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getSystemCode, BlogConstants.SYSTEM_CODE)
                .orderByAsc(SysPermission::getSort)
                .last("LIMIT " + offset + "," + query.getSize());
        List<SysPermission> records = permissionMapper.selectList(qw);
        Long total = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getSystemCode, BlogConstants.SYSTEM_CODE));
        return PageResult.of(records, total == null ? 0 : total, query.getSize(), query.getCurrent());
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getSystemCode, BlogConstants.SYSTEM_CODE)
                .orderByAsc(SysPermission::getSort));
    }

    /** 赋权幂等：重复授予不报错、不重复插行。 */
    @Transactional
    public void grantRoleToUser(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            throw BizException.badParam("userId/roleId 不能为空");
        }
        if (roleMapper.selectById(roleId) == null) {
            throw BizException.notFound();
        }
        if (userRoleMapper.countByUserAndRole(userId, roleId) > 0) {
            evictPermCache(userId);
            return;
        }
        SysUserRole row = new SysUserRole();
        row.setId(idService.nextId("sys_user_role"));
        row.setUserId(userId);
        row.setRoleId(roleId);
        row.setSystemCode(BlogConstants.SYSTEM_CODE);
        row.setCreateTime(LocalDateTime.now());
        row.setCreateBy(CurrentUser.idOrNull() == null ? 0L : CurrentUser.idOrNull());
        row.setDeleted(0);
        userRoleMapper.insert(row);
        evictPermCache(userId);
    }

    @Transactional
    public void revokeRoleFromUser(Long userId, Long roleId) {
        userRoleMapper.deleteByUserAndRole(userId, roleId);
        evictPermCache(userId);
    }

    @Transactional
    public void bindPermissionToRole(Long roleId, Long permissionId) {
        if (roleMapper.selectById(roleId) == null || permissionMapper.selectById(permissionId) == null) {
            throw BizException.notFound();
        }
        if (rolePermissionMapper.countByRoleAndPermission(roleId, permissionId) > 0) {
            return;
        }
        SysRolePermission row = new SysRolePermission();
        row.setId(idService.nextId("sys_role_permission"));
        row.setRoleId(roleId);
        row.setPermissionId(permissionId);
        row.setSystemCode(BlogConstants.SYSTEM_CODE);
        row.setCreateTime(LocalDateTime.now());
        row.setCreateBy(CurrentUser.idOrNull() == null ? 0L : CurrentUser.idOrNull());
        row.setDeleted(0);
        rolePermissionMapper.insert(row);
        evictAllPermCache();
    }

    @Transactional
    public void unbindPermissionFromRole(Long roleId, Long permissionId) {
        rolePermissionMapper.deleteByRoleAndPermission(roleId, permissionId);
        evictAllPermCache();
    }

    /** 用户权限缓存失效（Redis）。 */
    private void evictPermCache(Long userId) {
        if (userId == null || redis == null) {
            return;
        }
        try {
            redis.delete("blog:perm:" + userId);
        } catch (Exception ignored) {
            // Redis 故障不阻断赋权
        }
    }

    private void evictAllPermCache() {
        if (redis == null) {
            return;
        }
        try {
            var keys = redis.keys("blog:perm:*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ignored) {
            // 尽力失效
        }
    }

    public List<Long> listRoleIdsByUser(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }

    public Set<String> permissionCodesOfUser(Long userId) {
        List<Long> roleIds = listRoleIdsByUser(userId);
        if (roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> permIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        if (permIds.isEmpty()) {
            return new HashSet<>();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getId, permIds))
                .stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toSet());
    }
}
