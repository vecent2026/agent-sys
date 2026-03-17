package com.trae.admin.modules.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.rbac.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper接口（平台权限：platform_permission 表）
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询平台用户的所有权限（通过 platform_user_role + platform_role_permission）
     */
    @Select("SELECT p.* FROM platform_permission p " +
            "INNER JOIN platform_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN platform_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.is_deleted = 0")
    List<SysPermission> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询所有平台权限（超级管理员使用）
     */
    @Select("SELECT * FROM platform_permission WHERE is_deleted = 0")
    List<SysPermission> selectAllPermissions();
}
