package com.trae.admin.modules.rbac.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.rbac.vo.RoleVo;

import java.util.List;

/**
 * 角色服务接口
 */
public interface RoleService {

    /**
     * 分页查询角色
     */
    Page<RoleVo> page(RoleQueryDto queryDto);

    /**
     * 获取所有角色
     */
    List<RoleVo> listAll();

    /**
     * 获取角色详情
     */
    RoleVo get(Long id);

    /**
     * 获取角色权限ID列表
     */
    List<Long> getRolePermissionIds(Long roleId);

    /**
     * 分配角色权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 新增角色
     */
    void save(RoleDto roleDto);

    /**
     * 修改角色
     */
    void update(RoleDto roleDto);

    /**
     * 删除角色
     */
    void delete(List<Long> ids);
}
