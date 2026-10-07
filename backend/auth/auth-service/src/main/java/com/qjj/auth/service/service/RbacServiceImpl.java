package com.qjj.auth.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.auth.common.enums.AuthErrorCodeEnum;
import com.qjj.auth.common.exception.BizException;
import com.qjj.auth.framework.core.AuthContext;
import com.qjj.auth.framework.core.IdGenerator;
import com.qjj.auth.service.dto.IdsRequest;
import com.qjj.auth.service.dto.MenuRequest;
import com.qjj.auth.service.dto.RoleRequest;
import com.qjj.auth.service.entity.SysMenu;
import com.qjj.auth.service.entity.SysRole;
import com.qjj.auth.service.entity.SysRoleMenu;
import com.qjj.auth.service.entity.SysUserRole;
import com.qjj.auth.service.mapper.SysMenuMapper;
import com.qjj.auth.service.mapper.SysRoleMapper;
import com.qjj.auth.service.mapper.SysRoleMenuMapper;
import com.qjj.auth.service.mapper.SysUserRoleMapper;
import com.qjj.auth.service.vo.MenuVO;
import com.qjj.auth.service.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {
    /** 本系统内管理员角色编码；拥有该角色则视为授权本系统全部菜单/权限（含新建节点，无需逐条绑 sys_role_menu）。 */
    static final String ROLE_ADMIN = "admin";

    private static final int TYPE_DIRECTORY = 1;
    private static final int TYPE_MENU = 2;
    private static final int TYPE_BUTTON = 3;
    private static final int TYPE_API = 4;
    private static final int MENU_MAX_DEPTH = 4;
    private static final long MENU_DEPTH_SEGMENT = 1000L;
    private static final long BUTTON_ID_MIN = 90001L;
    private static final long BUTTON_ID_MAX_EXCLUSIVE = 100000L;
    private static final long API_ID_MIN = 5001L;
    private static final long API_ID_MAX_EXCLUSIVE = 6000L;
    private static final long ROLE_ID_MIN = 9001L;
    private static final long ROLE_ID_MAX_EXCLUSIVE = 10000L;

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    /** 菜单/角色短号分配锁：max+1 与插入原子化（rbac-design.md §2.0）。 */
    private final Object rbacIdLock = new Object();

    @Override
    public List<MenuVO> menus() {
        return buildTree(menuMapper.selectList(null));
    }

    @Override
    @Transactional
    public MenuVO createMenu(MenuRequest request) {
        validateMenu(request, null);
        validateMenuParent(request);
        SysMenu menu = applyMenu(new SysMenu(), request);
        synchronized (rbacIdLock) {
            menu.setId(allocateMenuId(request));
            menuMapper.insert(menu);
        }
        auditService.record("MENU_CREATE", "sys_menu", String.valueOf(menu.getId()), "创建菜单");
        return toMenuVO(menu);
    }

    @Override
    @Transactional
    public MenuVO updateMenu(String id, MenuRequest request) {
        SysMenu menu = menuMapper.selectById(Long.parseLong(id));
        if (menu == null) throw new BizException(AuthErrorCodeEnum.MENU_NODE_NOT_FOUND);
        validateMenu(request, menu.getId());
        applyMenu(menu, request);
        menuMapper.updateById(menu);
        auditService.record("MENU_UPDATE", "sys_menu", id, "更新菜单");
        return toMenuVO(menu);
    }

    @Override
    @Transactional
    public void deleteMenu(String id) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, Long.parseLong(id)));
        if (count != null && count > 0) throw new BizException(AuthErrorCodeEnum.MENU_HAS_CHILDREN);
        menuMapper.deleteById(Long.parseLong(id));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, Long.parseLong(id)));
        auditService.record("MENU_DELETE", "sys_menu", id, "删除菜单");
    }

    @Override
    public List<RoleVO> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort)).stream().map(this::toRoleVO).toList();
    }

    @Override
    @Transactional
    public RoleVO createRole(RoleRequest request) {
        validateRole(request, null);
        SysRole role = applyRole(new SysRole(), request);
        synchronized (rbacIdLock) {
            role.setId(allocateRoleId());
            roleMapper.insert(role);
        }
        auditService.record("ROLE_CREATE", "sys_role", String.valueOf(role.getId()), "创建角色");
        return toRoleVO(role);
    }

    @Override
    @Transactional
    public RoleVO updateRole(String id, RoleRequest request) {
        SysRole role = roleMapper.selectById(Long.parseLong(id));
        if (role == null) throw new BizException(AuthErrorCodeEnum.ROLE_NOT_FOUND);
        validateRole(request, role.getId());
        applyRole(role, request);
        roleMapper.updateById(role);
        auditService.record("ROLE_UPDATE", "sys_role", id, "更新角色");
        return toRoleVO(role);
    }

    @Override
    @Transactional
    public void deleteRole(String id) {
        roleMapper.deleteById(Long.parseLong(id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, Long.parseLong(id)));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, Long.parseLong(id)));
        auditService.record("ROLE_DELETE", "sys_role", id, "删除角色");
    }

    @Override
    @Transactional
    public void assignRoleMenus(String roleId, IdsRequest request) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, Long.parseLong(roleId)));
        for (String menuId : request.getIds()) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setId(idGenerator.nextId());
            relation.setRoleId(Long.parseLong(roleId));
            relation.setMenuId(Long.parseLong(menuId));
            roleMenuMapper.insert(relation);
        }
        auditService.record("ROLE_MENU_UPDATE", "sys_role", roleId, "更新角色菜单");
    }

    @Override
    @Transactional
    public void assignUserRoles(String userId, IdsRequest request) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, Long.parseLong(userId)));
        for (String roleId : request.getIds()) {
            SysUserRole relation = new SysUserRole();
            relation.setId(idGenerator.nextId());
            relation.setUserId(Long.parseLong(userId));
            relation.setRoleId(Long.parseLong(roleId));
            userRoleMapper.insert(relation);
        }
        auditService.record("ROLE_ASSIGN", "user", userId, "分配用户角色");
    }

    @Override
    public List<MenuVO> myMenus() {
        return buildTree(myMenuList().stream()
                .filter(m -> m.getType() != null
                        && (m.getType().intValue() == TYPE_DIRECTORY || m.getType().intValue() == TYPE_MENU))
                .toList());
    }

    @Override
    public Set<String> myPermissions() {
        return myMenuList().stream().map(SysMenu::getPermission).filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> permissionsForUser(Long userId) {
        if (userId == null || userId == 0L) return Set.of();
        return permissionOf(grantedEnabledMenus(userId));
    }

    private List<SysMenu> myMenuList() {
        Long userId = AuthContext.userIdOrSystem();
        if (userId == 0L) return List.of();
        return grantedEnabledMenus(userId);
    }

    /** 授权菜单（status=1）；非 admin 补祖先闭包；admin 全量启用节点。 */
    private List<SysMenu> grantedEnabledMenus(Long userId) {
        if (hasAdminRole(userId)) {
            return enabledMenus();
        }
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return List.of();
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) return List.of();
        List<SysMenu> granted = menuMapper.selectBatchIds(menuIds).stream()
                .filter(menu -> menu.getStatus() != null && menu.getStatus() == 1)
                .toList();
        return expandAncestors(granted);
    }

    /** 补全祖先链（仅 status=1），避免 /me/menus 断树（rbac-design.md §9）。 */
    private List<SysMenu> expandAncestors(List<SysMenu> granted) {
        Map<Long, SysMenu> collected = new HashMap<>();
        for (SysMenu menu : granted) {
            collected.put(menu.getId(), menu);
        }
        for (SysMenu menu : granted) {
            Long parentId = menu.getParentId();
            int guard = 0;
            while (parentId != null && parentId != 0L && !collected.containsKey(parentId) && guard++ < 16) {
                SysMenu parent = menuMapper.selectById(parentId);
                if (parent == null || parent.getStatus() == null || parent.getStatus() != 1) {
                    break;
                }
                collected.put(parent.getId(), parent);
                parentId = parent.getParentId();
            }
        }
        return List.copyOf(collected.values());
    }

    private List<Long> roleIdsOf(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    /** 管理员角色（role_code=admin 且启用）则本系统全量授权。 */
    private boolean hasAdminRole(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return false;
        return roleMapper.selectBatchIds(roleIds).stream()
                .anyMatch(r -> ROLE_ADMIN.equalsIgnoreCase(r.getRoleCode()) && r.getStatus() != null && r.getStatus() == 1);
    }

    private List<SysMenu> enabledMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1));
    }

    private Set<String> permissionOf(List<SysMenu> menus) {
        return menus.stream().map(SysMenu::getPermission)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<MenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuVO> nodes = menus.stream().sorted(Comparator.comparing(SysMenu::getSort))
                .collect(Collectors.toMap(SysMenu::getId, this::toMenuVO, (a, b) -> a, java.util.LinkedHashMap::new));
        List<MenuVO> roots = new ArrayList<>();
        menus.forEach(menu -> {
            MenuVO node = nodes.get(menu.getId());
            if (menu.getParentId() == null || menu.getParentId() == 0L || !nodes.containsKey(menu.getParentId())) roots.add(node);
            else nodes.get(menu.getParentId()).getChildren().add(node);
        });
        return roots;
    }

    private void validateMenu(MenuRequest request, Long currentId) {
        if (request.getPermission() != null && !request.getPermission().isBlank()) {
            if (!request.getPermission().startsWith("auth:")) throw new BizException(AuthErrorCodeEnum.MENU_PERMISSION_INVALID);
            Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPermission, request.getPermission())
                    .ne(currentId != null, SysMenu::getId, currentId));
            if (count != null && count > 0) throw new BizException(AuthErrorCodeEnum.MENU_PERMISSION_DUPLICATE);
        }
    }

    private void validateMenuParent(MenuRequest request) {
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Integer type = request.getType();
        int typeCode = type == null ? -1 : type.intValue();
        if (typeCode == TYPE_BUTTON || typeCode == TYPE_API) {
            if (parentId == 0L) {
                throw new BizException(AuthErrorCodeEnum.MENU_PARENT_INVALID);
            }
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(AuthErrorCodeEnum.MENU_NODE_NOT_FOUND);
            }
            if (parent.getType() == null || parent.getType().intValue() != TYPE_MENU) {
                throw new BizException(AuthErrorCodeEnum.MENU_PARENT_INVALID);
            }
            return;
        }
        if (parentId != 0L && menuMapper.selectById(parentId) == null) {
            throw new BizException(AuthErrorCodeEnum.MENU_NODE_NOT_FOUND);
        }
        if (resolveNewDepth(parentId) > MENU_MAX_DEPTH) {
            throw new BizException(AuthErrorCodeEnum.MENU_DEPTH_EXCEEDED);
        }
    }

    private int resolveNewDepth(long parentId) {
        if (parentId == 0L) {
            return 1;
        }
        int depth = 2;
        Long cur = parentId;
        int guard = 0;
        while (cur != null && cur != 0L && guard++ < 16) {
            SysMenu parent = menuMapper.selectById(cur);
            if (parent == null) {
                throw new BizException(AuthErrorCodeEnum.MENU_NODE_NOT_FOUND);
            }
            if (parent.getParentId() == null || parent.getParentId() == 0L) {
                return depth;
            }
            depth++;
            cur = parent.getParentId();
        }
        return depth;
    }

    private long allocateMenuId(MenuRequest request) {
        Integer type = request.getType();
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        int typeCode = type == null ? -1 : type.intValue();
        if (typeCode == TYPE_DIRECTORY || typeCode == TYPE_MENU) {
            int depth = resolveNewDepth(parentId);
            if (depth > MENU_MAX_DEPTH) {
                throw new BizException(AuthErrorCodeEnum.MENU_DEPTH_EXCEEDED);
            }
            long base = depth * MENU_DEPTH_SEGMENT;
            return nextMenuIdInSegment(base, base + MENU_DEPTH_SEGMENT, base + 1);
        }
        if (typeCode == TYPE_BUTTON) {
            return nextMenuIdInSegment(BUTTON_ID_MIN, BUTTON_ID_MAX_EXCLUSIVE, BUTTON_ID_MIN);
        }
        if (typeCode == TYPE_API) {
            return nextMenuIdInSegment(API_ID_MIN, API_ID_MAX_EXCLUSIVE, API_ID_MIN);
        }
        throw new BizException(AuthErrorCodeEnum.MENU_PERMISSION_INVALID);
    }

    private long allocateRoleId() {
        List<SysRole> rows = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .ge(SysRole::getId, ROLE_ID_MIN)
                .lt(SysRole::getId, ROLE_ID_MAX_EXCLUSIVE)
                .select(SysRole::getId));
        long max = rows.stream().mapToLong(SysRole::getId).max().orElse(ROLE_ID_MIN - 1);
        long next = Math.max(max + 1, ROLE_ID_MIN);
        if (next >= ROLE_ID_MAX_EXCLUSIVE) {
            throw new BizException(AuthErrorCodeEnum.MENU_ID_EXHAUSTED);
        }
        return next;
    }

    private long nextMenuIdInSegment(long minInclusive, long maxExclusive, long minStart) {
        List<SysMenu> rows = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .ge(SysMenu::getId, minInclusive)
                .lt(SysMenu::getId, maxExclusive)
                .select(SysMenu::getId));
        long max = rows.stream().mapToLong(SysMenu::getId).max().orElse(minStart - 1);
        long next = Math.max(max + 1, minStart);
        if (next >= maxExclusive) {
            throw new BizException(AuthErrorCodeEnum.MENU_ID_EXHAUSTED);
        }
        return next;
    }

    private void validateRole(RoleRequest request, Long currentId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(currentId != null, SysRole::getId, currentId));
        if (count != null && count > 0) throw new BizException(AuthErrorCodeEnum.ROLE_CODE_EXISTS);
    }

    private SysMenu applyMenu(SysMenu menu, MenuRequest request) {
        menu.setParentId(request.getParentId());
        menu.setType(request.getType());
        menu.setName(request.getName());
        menu.setPermission(request.getPermission());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setHidden(request.getHidden());
        menu.setRequiresAuth(request.getRequiresAuth());
        menu.setSort(request.getSort());
        menu.setStatus(request.getStatus());
        return menu;
    }

    private SysRole applyRole(SysRole role, RoleRequest request) {
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDataScope(request.getDataScope());
        role.setSort(request.getSort());
        role.setStatus(request.getStatus());
        role.setRemark(request.getRemark());
        return role;
    }

    private MenuVO toMenuVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(String.valueOf(menu.getId()));
        vo.setParentId(String.valueOf(menu.getParentId()));
        vo.setType(menu.getType());
        vo.setName(menu.getName());
        vo.setPermission(menu.getPermission());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setHidden(menu.getHidden());
        vo.setRequiresAuth(menu.getRequiresAuth());
        vo.setSort(menu.getSort());
        vo.setStatus(menu.getStatus());
        return vo;
    }

    private RoleVO toRoleVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(String.valueOf(role.getId()));
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setMenuIds(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()))
                .stream().map(r -> String.valueOf(r.getMenuId())).toList());
        return vo;
    }
}
