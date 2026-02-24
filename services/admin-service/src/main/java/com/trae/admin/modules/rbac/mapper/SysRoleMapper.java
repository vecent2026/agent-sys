package com.trae.admin.modules.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.rbac.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 角色Mapper接口
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.id, r.role_name, r.role_key, r.description, r.create_time, " +
            "COUNT(ur.user_id) as user_count " +
            "FROM sys_role r " +
            "LEFT JOIN sys_user_role ur ON r.id = ur.role_id " +
            "GROUP BY r.id")
    List<Map<String, Object>> selectRoleUserCount();
}
