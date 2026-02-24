package com.trae.admin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_name FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);
    
    @Select("SELECT r.id FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
