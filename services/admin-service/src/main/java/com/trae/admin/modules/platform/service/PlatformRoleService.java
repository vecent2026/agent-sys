package com.trae.admin.modules.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.platform.vo.PlatformRoleVo;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;

import java.util.List;

public interface PlatformRoleService {
    Page<PlatformRoleVo> page(RoleQueryDto queryDto);
    List<PlatformRoleVo> listAll();
    PlatformRoleVo get(Long id);
    List<Long> getRolePermissionIds(Long roleId);
    void assignPermissions(Long roleId, List<Long> permissionIds);
    void save(RoleDto roleDto);
    void update(Long id, RoleDto roleDto);
    void delete(List<Long> ids);
}
