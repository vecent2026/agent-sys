package com.trae.admin.modules.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户成员管理控制器
 * 路径：/api/tenant/members
 */
@Tag(name = "租户成员管理", description = "租户成员查询、角色分配、移除")
@RestController
@RequestMapping("/api/tenant/members")
@RequiredArgsConstructor
public class TenantMemberController {

    private final TenantUserRoleMapper tenantUserRoleMapper;
    private final RestTemplate restTemplate;

    @Value("${internal.user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    @Operation(summary = "查询租户成员列表")
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:member:list')")
    public Result<List<Map<String, Object>>> listMembers() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        // 查询该租户所有成员（user_id 去重）
        List<Long> userIds = tenantUserRoleMapper.selectList(
                        new LambdaQueryWrapper<TenantUserRole>()
                                .eq(TenantUserRole::getTenantId, tenantId)
                                .select(TenantUserRole::getUserId))
                .stream()
                .map(TenantUserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return Result.success(Collections.emptyList());

        // 调用 user-service 批量查询用户信息
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/batch?ids=" +
                            userIds.stream().map(Object::toString).collect(Collectors.joining(",")),
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> users = resp.getBody();
            if (users == null) return Result.success(Collections.emptyList());

            // 附加角色信息
            for (Map<String, Object> user : users) {
                Long userId = Long.valueOf(user.get("id").toString());
                List<Long> roleIds = tenantUserRoleMapper.selectRoleIdsByUserAndTenant(userId, tenantId);
                user.put("roleIds", roleIds);
            }
            return Result.success(users);
        } catch (Exception e) {
            return Result.success(Collections.emptyList());
        }
    }

    @Operation(summary = "为成员分配角色")
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('tenant:member:role')")
    public Result<Void> assignRoles(@PathVariable Long userId,
                                    @RequestBody Map<String, List<Long>> body) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        List<Long> roleIds = body.get("roleIds");

        // 删除旧角色关联
        tenantUserRoleMapper.deleteByUserAndTenant(userId, tenantId);

        // 插入新角色关联
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                TenantUserRole tur = new TenantUserRole();
                tur.setUserId(userId);
                tur.setTenantId(tenantId);
                tur.setRoleId(roleId);
                tenantUserRoleMapper.insert(tur);
            }
        }
        return Result.success();
    }

    @Operation(summary = "移除成员（解除所有角色关联）")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('tenant:member:remove')")
    public Result<Void> removeMember(@PathVariable Long userId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");
        tenantUserRoleMapper.deleteByUserAndTenant(userId, tenantId);
        return Result.success();
    }
}
