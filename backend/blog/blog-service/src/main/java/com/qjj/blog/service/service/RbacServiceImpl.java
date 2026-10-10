package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.core.UserContext;
import com.qjj.blog.service.dto.MenuRequest;
import com.qjj.blog.service.dto.RoleAssignRequest;
import com.qjj.blog.service.dto.RoleMenuRequest;
import com.qjj.blog.service.dto.RoleRequest;
import com.qjj.blog.service.entity.SysMenu;
import com.qjj.blog.service.entity.SysRole;
import com.qjj.blog.service.entity.SysRoleMenu;
import com.qjj.blog.service.entity.SysUserRole;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.enums.MenuTypeEnum;
import com.qjj.blog.service.mapper.SysMenuMapper;
import com.qjj.blog.service.mapper.SysRoleMapper;
import com.qjj.blog.service.mapper.SysRoleMenuMapper;
import com.qjj.blog.service.mapper.SysUserRoleMapper;
import com.qjj.blog.service.vo.MenuVO;
import com.qjj.blog.service.vo.RoleVO;
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

    /** 本系统内管理员角色编码；拥有该角色则授权本系统全部菜单/权限（含新建节点）。 */
    static final String ROLE_ADMIN = "admin";

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    /** 菜单/角色短号分配锁：max+1 与插入原子化（rbac-design.md §2.0）。 */
    private final Object rbacIdLock = new Object();

    /** 菜单深度上限（rbac-design.md §2.0.2）。 */
    private static final int MENU_MAX_DEPTH = 4;
    /** type=3 按钮号段 [90001, 100000)。 */
    private static final long BUTTON_ID_MIN = 90001L;
    private static final long BUTTON_ID_MAX_EXCLUSIVE = 100000L;
    /** type=4 接口点号段 [5001, 6000)。 */
    private static final long API_ID_MIN = 5001L;
    private static final long API_ID_MAX_EXCLUSIVE = 6000L;
    /** type=1/2 每个深度段宽度。 */
    private static final long MENU_DEPTH_SEGMENT = 1000L;
    /** sys_role 短号段 [9001, 10000)。 */
    private static final long ROLE_ID_MIN = 9001L;
    private static final long ROLE_ID_MAX_EXCLUSIVE = 10000L;

    @Override
    public List<MenuVO> listMenuTree() {
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
        auditService.record(AuditActionEnum.MENU_CREATE, "MENU", String.valueOf(menu.getId()), "创建菜单 " + menu.getName());
        return toMenuVO(menu);
    }

    @Override
    @Transactional
    public MenuVO updateMenu(String idValue, MenuRequest request) {
        long id = parseId(idValue);
        SysMenu menu = requireMenu(id);
        validateMenu(request, id);
        applyMenu(menu, request);
        menuMapper.updateById(menu);
        auditService.record(AuditActionEnum.MENU_UPDATE, "MENU", String.valueOf(menu.getId()), "更新菜单 " + menu.getName());
        return toMenuVO(menu);
    }

    @Override
    @Transactional
    public void deleteMenu(String idValue) {
        long id = parseId(idValue);
        Long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(BlogErrorCodeEnum.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        auditService.record(AuditActionEnum.MENU_DELETE, "MENU", String.valueOf(id), "删除菜单 " + id);
    }

    @Override
    public List<RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort));
        return roles.stream().map(this::toRoleVO).toList();
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
        auditService.record(AuditActionEnum.ROLE_CREATE, "ROLE", String.valueOf(role.getId()), "创建角色 " + role.getRoleCode());
        return toRoleVO(role);
    }

    @Override
    @Transactional
    public RoleVO updateRole(String idValue, RoleRequest request) {
        long id = parseId(idValue);
        SysRole role = requireRole(id);
        validateRole(request, id);
        applyRole(role, request);
        roleMapper.updateById(role);
        auditService.record(AuditActionEnum.ROLE_UPDATE, "ROLE", String.valueOf(role.getId()), "更新角色 " + role.getRoleCode());
        return toRoleVO(role);
    }

    @Override
    @Transactional
    public void deleteRole(String idValue) {
        long id = parseId(idValue);
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        auditService.record(AuditActionEnum.ROLE_DELETE, "ROLE", String.valueOf(id), "删除角色 " + id);
    }

    @Override
    @Transactional
    public void assignRoleMenus(String idValue, RoleMenuRequest request) {
        long roleId = parseId(idValue);
        requireRole(roleId);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (String menuId : request.getMenuIds()) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setId(idGenerator.nextId());
            relation.setRoleId(roleId);
            relation.setMenuId(parseId(menuId));
            roleMenuMapper.insert(relation);
        }
        auditService.record(AuditActionEnum.ROLE_MENU, "ROLE", String.valueOf(roleId), "角色菜单授权 " + roleId);
    }

    @Override
    @Transactional
    public void assignUserRoles(String userIdValue, RoleAssignRequest request) {
        long userId = parseId(userIdValue);
        for (String roleIdValue : request.getRoleIds()) {
            long roleId = parseId(roleIdValue);
            requireRole(roleId);
            Long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, roleId));
            if (exists == null || exists == 0) {
                SysUserRole relation = new SysUserRole();
                relation.setId(idGenerator.nextId());
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
        auditService.record(AuditActionEnum.ROLE_ASSIGN, "USER", String.valueOf(userId), "分配角色");
    }

    @Override
    @Transactional
    public void removeUserRoles(String userIdValue, RoleAssignRequest request) {
        long userId = parseId(userIdValue);
        for (String roleIdValue : request.getRoleIds()) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, parseId(roleIdValue)));
        }
        auditService.record(AuditActionEnum.ROLE_ASSIGN, "USER", String.valueOf(userId), "回收角色");
    }

    @Override
    public List<MenuVO> myMenus() {
        return buildTree(myMenuList().stream()
                .filter(menu -> menu.getType() != null
                        && (menu.getType().intValue() == MenuTypeEnum.DIRECTORY.getCode()
                                || menu.getType().intValue() == MenuTypeEnum.MENU.getCode()))
                .toList());
    }

    @Override
    public Set<String> myPermissions() {
        return myMenuList().stream()
                .map(SysMenu::getPermission)
                .filter(permission -> permission != null && !permission.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean hasPermission(String permission) {
        return myPermissions().contains(permission);
    }

    private List<SysMenu> myMenuList() {
        Long userId = UserContext.userIdOrSystem();
        if (userId == 0L) {
            return List.of();
        }
        if (hasAdminRole(userId)) {
            return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1));
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> granted = menuMapper.selectBatchIds(menuIds).stream()
                .filter(menu -> menu.getStatus() != null && menu.getStatus() == 1)
                .toList();
        return expandAncestors(granted);
    }

    /**
     * 在授权节点集合上补全祖先链（rbac-design.md §9 / §6.4），避免 /me/menus 断树。
     * 只返回 status=1 的节点。
     */
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

    /** 校验父节点存在性与 type=3/4 挂载约束（rbac-design.md §2.0.5 / §9）。 */
    private void validateMenuParent(MenuRequest request) {
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Integer type = request.getType();
        if (type != null && (type.intValue() == MenuTypeEnum.BUTTON.getCode()
                || type.intValue() == MenuTypeEnum.API.getCode())) {
            if (parentId == 0L) {
                throw new BizException(BlogErrorCodeEnum.MENU_PARENT_INVALID);
            }
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(BlogErrorCodeEnum.MENU_NODE_NOT_FOUND);
            }
            if (parent.getType() == null || parent.getType().intValue() != MenuTypeEnum.MENU.getCode()) {
                throw new BizException(BlogErrorCodeEnum.MENU_PARENT_INVALID);
            }
            return;
        }
        if (parentId != 0L && menuMapper.selectById(parentId) == null) {
            throw new BizException(BlogErrorCodeEnum.MENU_NODE_NOT_FOUND);
        }
        if (resolveNewDepth(parentId) > MENU_MAX_DEPTH) {
            throw new BizException(BlogErrorCodeEnum.MENU_DEPTH_EXCEEDED);
        }
    }

    /** 新节点深度：parent_id=0 → 1；否则父深度 + 1。 */
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
                throw new BizException(BlogErrorCodeEnum.MENU_NODE_NOT_FOUND);
            }
            if (parent.getParentId() == null || parent.getParentId() == 0L) {
                return depth;
            }
            depth++;
            cur = parent.getParentId();
        }
        return depth;
    }

    /** createMenu 主键：type=1/2 层级短号，type=3 按钮段，type=4 接口段（rbac-design.md §2.0.2 / §2.0.5）。 */
    private long allocateMenuId(MenuRequest request) {
        Integer type = request.getType();
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        int typeCode = type == null ? -1 : type.intValue();
        if (typeCode == MenuTypeEnum.DIRECTORY.getCode() || typeCode == MenuTypeEnum.MENU.getCode()) {
            int depth = resolveNewDepth(parentId);
            if (depth > MENU_MAX_DEPTH) {
                throw new BizException(BlogErrorCodeEnum.MENU_DEPTH_EXCEEDED);
            }
            long base = depth * MENU_DEPTH_SEGMENT;
            return nextInSegment(base, base + MENU_DEPTH_SEGMENT, base + 1);
        }
        if (typeCode == MenuTypeEnum.BUTTON.getCode()) {
            return nextInSegment(BUTTON_ID_MIN, BUTTON_ID_MAX_EXCLUSIVE, BUTTON_ID_MIN);
        }
        if (typeCode == MenuTypeEnum.API.getCode()) {
            return nextInSegment(API_ID_MIN, API_ID_MAX_EXCLUSIVE, API_ID_MIN);
        }
        throw new BizException(BlogErrorCodeEnum.MENU_PERMISSION_INVALID);
    }

    private long allocateRoleId() {
        List<SysRole> rows = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .ge(SysRole::getId, ROLE_ID_MIN)
                .lt(SysRole::getId, ROLE_ID_MAX_EXCLUSIVE)
                .select(SysRole::getId));
        long max = rows.stream().mapToLong(SysRole::getId).max().orElse(ROLE_ID_MIN - 1);
        long next = Math.max(max + 1, ROLE_ID_MIN);
        if (next >= ROLE_ID_MAX_EXCLUSIVE) {
            throw new BizException(BlogErrorCodeEnum.MENU_ID_EXHAUSTED);
        }
        return next;
    }

    /** 号段内 max+1；空段从 minStart 起。 */
    private long nextInSegment(long minInclusive, long maxExclusive, long minStart) {
        List<SysMenu> rows = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .ge(SysMenu::getId, minInclusive)
                .lt(SysMenu::getId, maxExclusive)
                .select(SysMenu::getId));
        long max = rows.stream().mapToLong(SysMenu::getId).max().orElse(minStart - 1);
        long next = max + 1;
        if (next < minStart) {
            next = minStart;
        }
        if (next >= maxExclusive) {
            throw new BizException(BlogErrorCodeEnum.MENU_ID_EXHAUSTED);
        }
        return next;
    }

    private boolean hasAdminRole(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .anyMatch(r -> ROLE_ADMIN.equalsIgnoreCase(r.getRoleCode()) && r.getStatus() != null && r.getStatus() == 1);
    }

    private List<MenuVO> buildTree(List<SysMenu> menus) {
        List<SysMenu> sorted = menus.stream().sorted(Comparator.comparing(SysMenu::getSort)).toList();
        Map<Long, MenuVO> nodes = new HashMap<>();
        sorted.forEach(menu -> nodes.put(menu.getId(), toMenuVO(menu)));
        List<MenuVO> roots = new ArrayList<>();
        sorted.forEach(menu -> {
            MenuVO node = nodes.get(menu.getId());
            if (menu.getParentId() == null || menu.getParentId() == 0L || !nodes.containsKey(menu.getParentId())) {
                roots.add(node);
            } else {
                nodes.get(menu.getParentId()).getChildren().add(node);
            }
        });
        return roots;
    }

    private void validateMenu(MenuRequest request, Long currentId) {
        if (request.getPermission() != null && !request.getPermission().isBlank()) {
            if (!request.getPermission().startsWith("blog:")) {
                throw new BizException(BlogErrorCodeEnum.MENU_PERMISSION_INVALID);
            }
            Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getPermission, request.getPermission())
                    .ne(currentId != null, SysMenu::getId, currentId));
            if (count != null && count > 0) {
                throw new BizException(BlogErrorCodeEnum.MENU_PERMISSION_DUPLICATE);
            }
        }
    }

    private void validateRole(RoleRequest request, Long currentId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(currentId != null, SysRole::getId, currentId));
        if (count != null && count > 0) {
            throw new BizException(BlogErrorCodeEnum.ROLE_CODE_EXISTS);
        }
    }

    private SysMenu applyMenu(SysMenu menu, MenuRequest request) {
        menu.setParentId(request.getParentId());
        menu.setType(request.getType());
        menu.setName(request.getName());
        menu.setPermission(defaultString(request.getPermission()));
        menu.setPath(defaultString(request.getPath()));
        menu.setComponent(defaultString(request.getComponent()));
        menu.setIcon(defaultString(request.getIcon()));
        menu.setHidden(request.getHidden());
        menu.setRequiresAuth(request.getRequiresAuth());
        menu.setSort(request.getSort());
        menu.setStatus(request.getStatus());
        return menu;
    }

    private SysRole applyRole(SysRole role, RoleRequest request) {
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(defaultString(request.getRoleName()));
        role.setDataScope(request.getDataScope());
        role.setSort(request.getSort());
        role.setStatus(request.getStatus());
        role.setRemark(defaultString(request.getRemark()));
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
                .stream().map(item -> String.valueOf(item.getMenuId())).toList());
        return vo;
    }

    private SysMenu requireMenu(long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(BlogErrorCodeEnum.MENU_NODE_NOT_FOUND);
        }
        return menu;
    }

    private SysRole requireRole(long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(BlogErrorCodeEnum.ROLE_NOT_FOUND);
        }
        return role;
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(BlogErrorCodeEnum.ROLE_NOT_FOUND);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
