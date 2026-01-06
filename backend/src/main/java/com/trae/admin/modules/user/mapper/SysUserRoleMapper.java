package com.trae.admin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 用户角色关联Mapper接口
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    
    /**
     * 根据用户ID查询角色ID列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID删除角色关联
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
