package com.trae.admin.modules.tenant.controller;

import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
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
@Slf4j
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

        List<Map<String, Object>> relations = listTenantUserRelations(tenantId);
        List<Long> userIds = relations.stream()
                .map(rel -> toLong(rel.get("userId")))
                .filter(Objects::nonNull)
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

            Map<Long, Map<String, Object>> relMap = relations.stream()
                    .filter(rel -> rel.get("userId") != null)
                    .collect(Collectors.toMap(
                            rel -> toLong(rel.get("userId")),
                            rel -> rel,
                            (a, b) -> a
                    ));

            // 附加角色信息
            for (Map<String, Object> user : users) {
                Long userId = Long.valueOf(user.get("id").toString());
                List<Long> roleIds = tenantUserRoleMapper.selectRoleIdsByUserAndTenant(userId, tenantId);
                user.put("roleIds", roleIds);
                Map<String, Object> rel = relMap.get(userId);
                user.put("status", rel != null && rel.get("status") != null
                        ? Integer.valueOf(rel.get("status").toString()) : 1);
                user.put("joinTime", rel != null ? rel.get("joinTime") : null);
            }
            return Result.success(users);
        } catch (Exception e) {
            log.warn("listMembers failed tenantId={}", tenantId, e);
            return Result.success(Collections.emptyList());
        }
    }

    @Operation(summary = "查询租户成员详情")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('tenant:member:list')")
    public Result<Map<String, Object>> getMember(@PathVariable Long userId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        Map<String, Object> relation = getTenantUserRelation(userId, tenantId);
        if (relation.isEmpty()) {
            throw new BusinessException("成员不存在");
        }

        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/batch?ids=" + userId,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> users = resp.getBody();
            if (users == null || users.isEmpty()) {
                throw new BusinessException("成员不存在");
            }
            Map<String, Object> user = users.get(0);
            user.put("roleIds", tenantUserRoleMapper.selectRoleIdsByUserAndTenant(userId, tenantId));
            user.put("status", relation.getOrDefault("status", 1));
            user.put("joinTime", relation.get("joinTime"));
            return Result.success(user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取成员详情失败");
        }
    }

    @Operation(summary = "新增租户成员")
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:member:add')")
    public Result<Void> addMember(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        Long userId = toLong(body.get("userId"));
        if (userId == null) {
            String mobile = body.get("mobile") != null ? body.get("mobile").toString() : null;
            if (mobile == null || mobile.isBlank()) {
                throw new BusinessException("userId 或 mobile 不能为空");
            }
            Map<String, Object> ensureReq = new HashMap<>();
            ensureReq.put("mobile", mobile);
            ensureReq.put("nickname", body.getOrDefault("nickname", mobile));
            if (body.get("password") != null) {
                ensureReq.put("password", body.get("password"));
            }
            try {
                ResponseEntity<Map<String, Object>> ensureResp = restTemplate.exchange(
                        userServiceUrl + "/api/internal/users/ensure",
                        HttpMethod.POST,
                        new HttpEntity<>(ensureReq),
                        new ParameterizedTypeReference<Map<String, Object>>() {});
                Map<String, Object> ensured = ensureResp.getBody();
                userId = ensured != null ? toLong(ensured.get("id")) : null;
            } catch (Exception e) {
                throw new BusinessException("新增成员失败：创建用户失败");
            }
        }

        if (userId == null) {
            throw new BusinessException("新增成员失败：用户无效");
        }

        Map<String, Object> relationReq = new HashMap<>();
        relationReq.put("userId", userId);
        relationReq.put("tenantId", tenantId);
        relationReq.put("isAdmin", body.getOrDefault("isAdmin", 0));
        try {
            restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users",
                    HttpMethod.POST,
                    new HttpEntity<>(relationReq),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("新增成员失败：写入成员关系失败");
        }

        List<Long> roleIds = parseRoleIds(body.get("roleIds"));
        if (!roleIds.isEmpty()) {
            tenantUserRoleMapper.deleteByUserAndTenant(userId, tenantId);
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

    @Operation(summary = "为成员分配角色")
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('tenant:member:role')")
    public Result<Void> assignRoles(@PathVariable Long userId,
                                    @RequestBody Map<String, List<Long>> body) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        List<Long> roleIds = body.get("roleIds") != null ? body.get("roleIds") : Collections.emptyList();
        List<Long> superRoleIds = tenantUserRoleMapper.selectSuperRoleIdsByTenant(tenantId);
        boolean hadSuperRole = tenantUserRoleMapper.existsSuperRoleByUserAndTenant(userId, tenantId);
        boolean willKeepSuperRole = roleIds.stream().anyMatch(new HashSet<>(superRoleIds)::contains);

        if (hadSuperRole && !willKeepSuperRole) {
            long superAdminCount = tenantUserRoleMapper.countSuperAdminsByTenant(tenantId);
            if (superAdminCount <= 1) {
                throw new BusinessException("当前租户仅剩最后一名超管，不能移除其超管角色。请先为其他成员分配超管角色。");
            }
        }

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

    @Operation(summary = "更新成员状态（1=启用 0=禁用）")
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('tenant:member:disable')")
    public Result<Void> updateMemberStatus(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");

        Integer status = body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : null;
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("status 仅支持 0 或 1");
        }

        if (status == 0) {
            boolean isSuperAdmin = tenantUserRoleMapper.existsSuperRoleByUserAndTenant(userId, tenantId);
            if (isSuperAdmin) {
                long superAdminCount = tenantUserRoleMapper.countSuperAdminsByTenant(tenantId);
                if (superAdminCount <= 1) {
                    throw new BusinessException("当前租户仅剩最后一名超管，无法禁用。请先新增或授权其他超管后再操作。");
                }
            }
        }

        Map<String, Object> req = new HashMap<>();
        req.put("userId", userId);
        req.put("tenantId", tenantId);
        req.put("status", status);
        try {
            restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users/status",
                    HttpMethod.PUT,
                    new HttpEntity<>(req),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("更新成员状态失败");
        }
        return Result.success();
    }

    @Operation(summary = "移除成员（解除所有角色关联）")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('tenant:member:remove')")
    public Result<Void> removeMember(@PathVariable Long userId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("租户上下文缺失");
        boolean isSuperAdmin = tenantUserRoleMapper.existsSuperRoleByUserAndTenant(userId, tenantId);
        if (isSuperAdmin) {
            long superAdminCount = tenantUserRoleMapper.countSuperAdminsByTenant(tenantId);
            if (superAdminCount <= 1) {
                throw new BusinessException("当前租户仅剩最后一名超管，无法删除。请先新增或授权其他超管后再操作。");
            }
        }
        try {
            restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users?userId=" + userId + "&tenantId=" + tenantId,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("移除成员失败：删除成员关系失败");
        }
        tenantUserRoleMapper.deleteByUserAndTenant(userId, tenantId);
        return Result.success();
    }

    private List<Map<String, Object>> listTenantUserRelations(Long tenantId) {
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users?tenantId=" + tenantId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return resp.getBody() != null ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("listTenantUserRelations failed tenantId={}", tenantId, e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> getTenantUserRelation(Long userId, Long tenantId) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users/" + userId + "?tenantId=" + tenantId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return resp.getBody() != null ? resp.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseRoleIds(Object roleIdsObj) {
        if (!(roleIdsObj instanceof List<?> rawList)) {
            return Collections.emptyList();
        }
        List<Long> roleIds = new ArrayList<>();
        for (Object o : rawList) {
            Long rid = toLong(o);
            if (rid != null) roleIds.add(rid);
        }
        return roleIds;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        try {
            return Long.valueOf(val.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
