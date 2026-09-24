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

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;

    @Override
    public List<MenuVO> menus() {
        return buildTree(menuMapper.selectList(null));
    }

    @Override
    @Transactional
    public MenuVO createMenu(MenuRequest request) {
        validateMenu(request, null);
        SysMenu menu = applyMenu(new SysMenu(), request);
        menu.setId(idGenerator.nextId());
        menuMapper.insert(menu);
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
        role.setId(idGenerator.nextId());
        roleMapper.insert(role);
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
        return buildTree(myMenuList().stream().filter(m -> m.getType() == 1 || m.getType() == 2).toList());
    }

    @Override
    public Set<String> myPermissions() {
        return myMenuList().stream().map(SysMenu::getPermission).filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> permissionsForUser(Long userId) {
        if (userId == null || userId == 0L) return Set.of();
        if (hasAdminRole(userId)) {
            return permissionOf(enabledMenus());
        }
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return Set.of();
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) return Set.of();
        return permissionOf(menuMapper.selectBatchIds(menuIds));
    }

    private List<SysMenu> myMenuList() {
        Long userId = AuthContext.userIdOrSystem();
        if (userId == 0L) return List.of();
        if (hasAdminRole(userId)) {
            return enabledMenus();
        }
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return List.of();
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        return menuIds.isEmpty() ? List.of() : menuMapper.selectBatchIds(menuIds);
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
