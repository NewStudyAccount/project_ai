package com.qjj.user.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.user.common.enums.UserErrorCodeEnum;
import com.qjj.user.common.exception.BizException;
import com.qjj.user.framework.core.IdGenerator;
import com.qjj.user.framework.core.UserContext;
import com.qjj.user.service.dto.MenuRequest;
import com.qjj.user.service.dto.RoleAssignRequest;
import com.qjj.user.service.dto.RoleMenuRequest;
import com.qjj.user.service.dto.RoleRequest;
import com.qjj.user.service.entity.SysMenu;
import com.qjj.user.service.entity.SysRole;
import com.qjj.user.service.entity.SysRoleMenu;
import com.qjj.user.service.entity.SysUserRole;
import com.qjj.user.service.enums.AuditActionEnum;
import com.qjj.user.service.enums.MenuTypeEnum;
import com.qjj.user.service.mapper.SysMenuMapper;
import com.qjj.user.service.mapper.SysRoleMapper;
import com.qjj.user.service.mapper.SysRoleMenuMapper;
import com.qjj.user.service.mapper.SysUserRoleMapper;
import com.qjj.user.service.vo.MenuVO;
import com.qjj.user.service.vo.RoleVO;
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

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;

    @Override
    public List<MenuVO> listMenuTree() {
        return buildTree(menuMapper.selectList(null));
    }

    @Override
    @Transactional
    public MenuVO createMenu(MenuRequest request) {
        validateMenu(request, null);
        SysMenu menu = applyMenu(new SysMenu(), request);
        menu.setId(idGenerator.nextId());
        menuMapper.insert(menu);
        auditService.record(AuditActionEnum.MENU_CREATE, null, "创建菜单 " + menu.getName());
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
        auditService.record(AuditActionEnum.MENU_UPDATE, null, "更新菜单 " + menu.getName());
        return toMenuVO(menu);
    }

    @Override
    @Transactional
    public void deleteMenu(String idValue) {
        long id = parseId(idValue);
        Long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(UserErrorCodeEnum.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        auditService.record(AuditActionEnum.MENU_DELETE, null, "删除菜单 " + id);
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
        role.setId(idGenerator.nextId());
        roleMapper.insert(role);
        auditService.record(AuditActionEnum.ROLE_CREATE, null, "创建角色 " + role.getRoleCode());
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
        auditService.record(AuditActionEnum.ROLE_UPDATE, null, "更新角色 " + role.getRoleCode());
        return toRoleVO(role);
    }

    @Override
    @Transactional
    public void deleteRole(String idValue) {
        long id = parseId(idValue);
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        auditService.record(AuditActionEnum.ROLE_DELETE, null, "删除角色 " + id);
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
        auditService.record(AuditActionEnum.ROLE_MENU, null, "角色菜单授权 " + roleId);
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
        auditService.record(AuditActionEnum.ROLE_ASSIGN, userId, "分配角色");
    }

    @Override
    @Transactional
    public void removeUserRoles(String userIdValue, RoleAssignRequest request) {
        long userId = parseId(userIdValue);
        for (String roleIdValue : request.getRoleIds()) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, parseId(roleIdValue)));
        }
        auditService.record(AuditActionEnum.ROLE_ASSIGN, userId, "回收角色");
    }

    @Override
    public List<MenuVO> myMenus() {
        return buildTree(myMenuList().stream()
                .filter(menu -> menu.getType() == MenuTypeEnum.DIRECTORY.getCode() || menu.getType() == MenuTypeEnum.MENU.getCode())
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
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        return menuIds.isEmpty() ? List.of() : menuMapper.selectBatchIds(menuIds);
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
            if (!request.getPermission().startsWith("user:")) {
                throw new BizException(UserErrorCodeEnum.MENU_PERMISSION_INVALID);
            }
            Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getPermission, request.getPermission())
                    .ne(currentId != null, SysMenu::getId, currentId));
            if (count != null && count > 0) {
                throw new BizException(UserErrorCodeEnum.MENU_PERMISSION_DUPLICATE);
            }
        }
    }

    private void validateRole(RoleRequest request, Long currentId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(currentId != null, SysRole::getId, currentId));
        if (count != null && count > 0) {
            throw new BizException(UserErrorCodeEnum.ROLE_CODE_EXISTS);
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
            throw new BizException(UserErrorCodeEnum.MENU_NODE_NOT_FOUND);
        }
        return menu;
    }

    private SysRole requireRole(long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(UserErrorCodeEnum.ROLE_NOT_FOUND);
        }
        return role;
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(UserErrorCodeEnum.USER_NOT_FOUND);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
