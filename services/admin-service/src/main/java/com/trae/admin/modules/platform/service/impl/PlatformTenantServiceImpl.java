package com.trae.admin.modules.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.platform.dto.TenantDto;
import com.trae.admin.modules.platform.dto.TenantQueryDto;
import com.trae.admin.modules.platform.entity.PlatformTenant;
import com.trae.admin.modules.platform.mapper.PlatformTenantMapper;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.TenantVo;
import com.trae.admin.modules.tenant.entity.TenantPermission;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantPermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantRolePermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final PlatformTenantMapper tenantMapper;
    private final TenantPermissionMapper tenantPermissionMapper;
    private final TenantRolePermissionMapper tenantRolePermissionMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;
    private final RestTemplate restTemplate;

    @Value("${internal.user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    @Override
    public Page<TenantVo> page(TenantQueryDto query) {
        LambdaQueryWrapper<PlatformTenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTenantName())) {
            wrapper.like(PlatformTenant::getTenantName, query.getTenantName());
        }
        if (StringUtils.hasText(query.getTenantCode())) {
            wrapper.like(PlatformTenant::getTenantCode, query.getTenantCode());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PlatformTenant::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(PlatformTenant::getCreateTime);

        Page<PlatformTenant> entityPage = tenantMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        Page<TenantVo> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public TenantVo get(Long id) {
        PlatformTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) throw new BusinessException("租户不存在");
        return toVo(tenant);
    }

    @Override
    @Transactional
    public void save(TenantDto dto) {
        Long count = tenantMapper.selectCount(new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, dto.getTenantCode()));
        if (count > 0) throw new BusinessException("租户编码已存在");

        PlatformTenant tenant = new PlatformTenant();
        BeanUtils.copyProperties(dto, tenant);
        tenant.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        tenant.setDataVersion(1);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(tenant);

        // 可选：初始化管理员（创建 app_user + 建立 tenant_user 关系）
        initTenantAdminIfNeeded(dto, tenant.getId());
    }

    @Override
    @Transactional
    public void update(TenantDto dto) {
        PlatformTenant existing = tenantMapper.selectById(dto.getId());
        if (existing == null) throw new BusinessException("租户不存在");

        // 编码变更时检查唯一性
        if (!existing.getTenantCode().equals(dto.getTenantCode())) {
            Long count = tenantMapper.selectCount(new LambdaQueryWrapper<PlatformTenant>()
                    .eq(PlatformTenant::getTenantCode, dto.getTenantCode())
                    .ne(PlatformTenant::getId, dto.getId()));
            if (count > 0) throw new BusinessException("租户编码已存在");
        }

        BeanUtils.copyProperties(dto, existing);
        existing.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (tenantMapper.selectById(id) == null) throw new BusinessException("租户不存在");
        tenantMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        PlatformTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) throw new BusinessException("租户不存在");
        tenant.setStatus(status);
        // 状态变更时递增 dataVersion 使已有 JWT 失效
        tenant.setDataVersion(tenant.getDataVersion() + 1);
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
    }

    @Override
    public List<TenantVo> listAll() {
        return tenantMapper.selectList(new LambdaQueryWrapper<PlatformTenant>()
                        .eq(PlatformTenant::getStatus, 1)
                        .orderByAsc(PlatformTenant::getTenantName))
                .stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public List<Long> getPermissionIds(Long tenantId) {
        return tenantPermissionMapper.selectPermissionIdsByTenantId(tenantId);
    }

    @Override
    @Transactional
    public void updatePermissions(Long tenantId, List<Long> permissionIds) {
        // 删除旧授权
        tenantPermissionMapper.delete(new LambdaQueryWrapper<TenantPermission>()
                .eq(TenantPermission::getTenantId, tenantId));
        // 插入新授权
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissionIds.forEach(permId -> {
                TenantPermission tp = new TenantPermission();
                tp.setTenantId(tenantId);
                tp.setPermissionId(permId);
                tp.setCreateTime(LocalDateTime.now());
                tenantPermissionMapper.insert(tp);
            });
        }
    }

    @Override
    public boolean checkCodeAvailable(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        LambdaQueryWrapper<PlatformTenant> wrapper = new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, code);
        if (excludeId != null) {
            wrapper.ne(PlatformTenant::getId, excludeId);
        }
        return tenantMapper.selectCount(wrapper) == 0;
    }

    @Override
    public Map<String, Object> getStats(Long tenantId) {
        PlatformTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        List<TenantUserRole> rows = tenantUserRoleMapper.selectList(
                new LambdaQueryWrapper<TenantUserRole>()
                        .eq(TenantUserRole::getTenantId, tenantId)
                        .select(TenantUserRole::getUserId, TenantUserRole::getRoleId));
        long memberCount = rows.stream().map(TenantUserRole::getUserId).filter(Objects::nonNull).distinct().count();
        long roleCount = rows.stream().map(TenantUserRole::getRoleId).filter(Objects::nonNull).distinct().count();
        Map<String, Object> stats = new HashMap<>();
        stats.put("memberCount", memberCount);
        stats.put("roleCount", roleCount);
        stats.put("todayActiveCount", 0);
        return stats;
    }

    @Override
    public List<Map<String, Object>> listMembers(Long tenantId) {
        PlatformTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        List<Long> userIds = tenantUserRoleMapper.selectList(
                        new LambdaQueryWrapper<TenantUserRole>()
                                .eq(TenantUserRole::getTenantId, tenantId)
                                .select(TenantUserRole::getUserId))
                .stream()
                .map(TenantUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/batch?ids=" +
                            userIds.stream().map(String::valueOf).collect(Collectors.joining(",")),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return resp.getBody() != null ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("listMembers failed tenantId={}", tenantId, e);
            return Collections.emptyList();
        }
    }

    private TenantVo toVo(PlatformTenant t) {
        TenantVo vo = new TenantVo();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }

    private void initTenantAdminIfNeeded(TenantDto dto, Long tenantId) {
        if (dto.getAdminUser() == null || !StringUtils.hasText(dto.getAdminUser().getMobile())) {
            return;
        }
        if (!StringUtils.hasText(dto.getAdminUser().getPassword())) {
            throw new BusinessException("初始化管理员失败：初始管理员密码不能为空");
        }
        try {
            Map<String, Object> ensureReq = new HashMap<>();
            ensureReq.put("mobile", dto.getAdminUser().getMobile());
            ensureReq.put("nickname", StringUtils.hasText(dto.getAdminUser().getNickname())
                    ? dto.getAdminUser().getNickname()
                    : dto.getAdminUser().getMobile());
            ensureReq.put("password", dto.getAdminUser().getPassword());

            ResponseEntity<Map<String, Object>> ensureResp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/ensure",
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(ensureReq),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> user = ensureResp.getBody();
            if (user == null || user.get("id") == null) {
                throw new BusinessException("初始化管理员失败：用户创建失败");
            }
            Long userId = Long.valueOf(user.get("id").toString());

            Map<String, Object> relationReq = new HashMap<>();
            relationReq.put("userId", userId);
            relationReq.put("tenantId", tenantId);
            relationReq.put("isAdmin", 1);
            restTemplate.exchange(
                    userServiceUrl + "/api/internal/tenant-users",
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(relationReq),
                    Void.class
            );

            // 保障租户存在内置超管角色，并将初始管理员绑定为超管
            Long superRoleId = tenantUserRoleMapper.selectDefaultSuperRoleId(tenantId);
            if (superRoleId == null) {
                tenantUserRoleMapper.insertDefaultSuperRole(tenantId);
                superRoleId = tenantUserRoleMapper.selectDefaultSuperRoleId(tenantId);
            }
            if (superRoleId == null) {
                throw new BusinessException("初始化管理员失败：创建租户超管角色失败");
            }

            tenantRolePermissionMapper.grantAllTenantPermissionsToRole(tenantId, superRoleId);

            if (tenantUserRoleMapper.countUserRoleBinding(userId, tenantId, superRoleId) == 0) {
                TenantUserRole binding = new TenantUserRole();
                binding.setUserId(userId);
                binding.setTenantId(tenantId);
                binding.setRoleId(superRoleId);
                tenantUserRoleMapper.insert(binding);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("init tenant admin failed tenantId={}, mobile={}",
                    tenantId,
                    dto.getAdminUser() != null ? dto.getAdminUser().getMobile() : null,
                    e);
            throw new BusinessException("初始化管理员失败");
        }
    }
}
