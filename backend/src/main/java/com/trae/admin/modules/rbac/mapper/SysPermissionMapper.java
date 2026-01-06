package com.trae.admin.modules.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.rbac.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper接口
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.is_deleted = 0")
    List<SysPermission> selectPermissionsByUserId(@Param("userId") Long userId);
}
