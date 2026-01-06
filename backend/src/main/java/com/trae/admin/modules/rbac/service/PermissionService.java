package com.trae.admin.modules.rbac.service;

import com.trae.admin.modules.rbac.dto.PermissionDto;
import com.trae.admin.modules.rbac.vo.PermissionVo;

import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService {

    /**
     * 获取权限树
     */
    List<PermissionVo> listTree();

    /**
     * 获取所有权限列表
     */
    List<PermissionVo> listAll(String name);

    /**
     * 新增权限
     */
    void save(PermissionDto permissionDto);

    /**
     * 修改权限
     */
    void update(PermissionDto permissionDto);

    /**
     * 删除权限
     */
    void delete(Long id);
}
