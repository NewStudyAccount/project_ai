package com.qjj.auth.service.service;

import com.qjj.auth.service.dto.IdsRequest;
import com.qjj.auth.service.dto.MenuRequest;
import com.qjj.auth.service.dto.RoleRequest;
import com.qjj.auth.service.vo.MenuVO;
import com.qjj.auth.service.vo.RoleVO;

import java.util.List;
import java.util.Set;

public interface RbacService {
    List<MenuVO> menus();
    MenuVO createMenu(MenuRequest request);
    MenuVO updateMenu(String id, MenuRequest request);
    void deleteMenu(String id);
    List<RoleVO> roles();
    RoleVO createRole(RoleRequest request);
    RoleVO updateRole(String id, RoleRequest request);
    void deleteRole(String id);
    void assignRoleMenus(String roleId, IdsRequest request);
    void assignUserRoles(String userId, IdsRequest request);
    List<MenuVO> myMenus();
    Set<String> myPermissions();
    Set<String> permissionsForUser(Long userId);
}
