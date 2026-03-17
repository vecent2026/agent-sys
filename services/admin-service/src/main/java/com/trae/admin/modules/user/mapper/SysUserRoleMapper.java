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
    @Select("SELECT role_id FROM platform_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID删除角色关联
     */
    @Delete("DELETE FROM platform_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) > 0 " +
            "FROM platform_user_role ur " +
            "INNER JOIN platform_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.is_deleted = 0 AND r.is_super = 1")
    boolean existsSuperRole(@Param("userId") Long userId);
}
