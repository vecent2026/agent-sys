package com.trae.admin.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.tenant.entity.TenantRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TenantRoleMapper extends BaseMapper<TenantRole> {

    @Select("SELECT r.id, COUNT(DISTINCT tur.user_id) AS user_count " +
            "FROM tenant_role r " +
            "LEFT JOIN tenant_user_role tur ON r.id = tur.role_id AND r.tenant_id = tur.tenant_id " +
            "WHERE r.tenant_id = #{tenantId} AND r.is_deleted = 0 " +
            "GROUP BY r.id")
    List<Map<String, Object>> selectRoleUserCount(@Param("tenantId") Long tenantId);
}
