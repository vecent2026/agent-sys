package com.trae.admin.modules.tenant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.rbac.vo.PermissionVo;
import com.trae.admin.modules.rbac.vo.RoleVo;

import java.util.List;

public interface TenantRoleService {

    Page<RoleVo> page(RoleQueryDto queryDto);

    List<RoleVo> listAll();

    RoleVo get(Long id);

    List<Long> getRolePermissionIds(Long roleId);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    void save(RoleDto roleDto);

    void update(Long id, RoleDto roleDto);

    void delete(List<Long> ids);

    List<PermissionVo> listAvailablePermissions();
}
