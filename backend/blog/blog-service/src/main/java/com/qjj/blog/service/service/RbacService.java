package com.qjj.blog.service.service;

import com.qjj.blog.service.dto.MenuRequest;
import com.qjj.blog.service.dto.RoleAssignRequest;
import com.qjj.blog.service.dto.RoleMenuRequest;
import com.qjj.blog.service.dto.RoleRequest;
import com.qjj.blog.service.vo.MenuVO;
import com.qjj.blog.service.vo.RoleVO;

import java.util.List;
import java.util.Set;

public interface RbacService {

    List<MenuVO> listMenuTree();

    MenuVO createMenu(MenuRequest request);

    MenuVO updateMenu(String id, MenuRequest request);

    void deleteMenu(String id);

    List<RoleVO> listRoles();

    RoleVO createRole(RoleRequest request);

    RoleVO updateRole(String id, RoleRequest request);

    void deleteRole(String id);

    void assignRoleMenus(String roleId, RoleMenuRequest request);

    void assignUserRoles(String userId, RoleAssignRequest request);

    void removeUserRoles(String userId, RoleAssignRequest request);

    List<MenuVO> myMenus();

    Set<String> myPermissions();

    boolean hasPermission(String permission);
}
