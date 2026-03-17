package com.trae.admin.modules.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.platform.entity.PlatformRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.List;

@Mapper
public interface PlatformRoleMapper extends BaseMapper<PlatformRole> {

    @Select("SELECT r.id, r.role_name, r.role_key, r.description, r.create_time, " +
            "COUNT(ur.user_id) as user_count " +
            "FROM platform_role r " +
            "LEFT JOIN platform_user_role ur ON r.id = ur.role_id " +
            "WHERE r.is_deleted = 0 " +
            "GROUP BY r.id")
    List<Map<String, Object>> selectRoleUserCount();
}
