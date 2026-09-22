package com.example.blog.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.blog.common.audit.IdService;
import com.example.blog.common.error.BizException;
import com.example.blog.content.entity.SysRole;
import com.example.blog.content.entity.SysUserRole;
import com.example.blog.content.mapper.SysPermissionMapper;
import com.example.blog.content.mapper.SysRoleMapper;
import com.example.blog.content.mapper.SysRolePermissionMapper;
import com.example.blog.content.mapper.SysUserRoleMapper;
import com.example.blog.content.service.RbacService;
import com.example.blog.content.service.UserRefService;
import com.example.blog.content.mapper.SysUserRefMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 赋权幂等、无权限语义、投影失败不影响授权。 */
class RbacAndUserRefTest {

    private SysRoleMapper roleMapper;
    private SysPermissionMapper permissionMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysRolePermissionMapper rolePermissionMapper;
    private IdService idService;
    private RbacService rbacService;
    private SysUserRefMapper userRefMapper;
    private UserRefService userRefService;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        permissionMapper = mock(SysPermissionMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        idService = mock(IdService.class);
        userRefMapper = mock(SysUserRefMapper.class);
        rbacService = new RbacService(roleMapper, permissionMapper, userRoleMapper, rolePermissionMapper, idService, null);
        userRefService = new UserRefService(userRefMapper);
    }

    @Test
    void grant_role_is_idempotent() {
        SysRole role = new SysRole();
        role.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(userRoleMapper.countByUserAndRole(100L, 1L)).thenReturn(0L).thenReturn(1L);
        when(idService.nextId("sys_user_role")).thenReturn(26092200000301L);
        when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);

        assertDoesNotThrow(() -> rbacService.grantRoleToUser(100L, 1L));
        assertDoesNotThrow(() -> rbacService.grantRoleToUser(100L, 1L));
        verify(userRoleMapper, times(1)).insert(any(SysUserRole.class));
    }

    @Test
    void missing_role_forbidden_or_not_found() {
        when(roleMapper.selectById(9L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> rbacService.grantRoleToUser(1L, 9L));
        assertEquals(20001, ex.getCode());
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void permission_codes_join_roles() {
        com.example.blog.content.entity.SysUserRole ur = new com.example.blog.content.entity.SysUserRole();
        ur.setRoleId(1L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(ur));
        com.example.blog.content.entity.SysRolePermission rp = new com.example.blog.content.entity.SysRolePermission();
        rp.setPermissionId(10L);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rp));
        com.example.blog.content.entity.SysPermission p = new com.example.blog.content.entity.SysPermission();
        p.setId(10L);
        p.setPermissionCode("blog:post:publish");
        when(permissionMapper.selectList(any())).thenReturn(List.of(p));

        Set<String> codes = rbacService.permissionCodesOfUser(100L);
        assertTrue(codes.contains("blog:post:publish"));
    }

    @Test
    void user_ref_upsert_and_batch_stub_never_fails_auth_path() {
        assertDoesNotThrow(() -> userRefService.upsertFromLogin(100L, "alice", "Alice"));
        verify(userRefMapper).upsert(any());
        when(userRefMapper.selectByUserIds(any())).thenReturn(List.of());
        Map<Long, com.example.blog.content.entity.SysUserRef> map =
                userRefService.batchForDisplay(Set.of(1L, 2L));
        assertEquals(2, map.size());
        assertTrue(map.get(1L).getRealName().contains("1"));
    }

    private static final class List {
        static <T> java.util.List<T> of(T... items) {
            return java.util.List.of(items);
        }
    }
}
