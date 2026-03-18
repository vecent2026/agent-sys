package com.trae.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.TenantLoginBody;
import com.trae.admin.modules.auth.dto.TenantSelectBody;
import com.trae.admin.modules.auth.dto.TenantSwitchBody;
import com.trae.admin.modules.auth.service.TenantAuthService;
import com.trae.admin.modules.platform.entity.PlatformTenant;
import com.trae.admin.modules.platform.mapper.PlatformTenantMapper;
import com.trae.admin.modules.tenant.mapper.TenantRolePermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 租户端认证服务实现
 *
 * <p>登录流程：
 * 1. 调用 user-service internal API 验证手机号+密码
 * 2. 查询用户所属租户列表（tenant_user_role）
 * 3. 单租户：直接签发 tenant JWT；多租户：签发 preToken + 返回租户列表
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAuthServiceImpl implements TenantAuthService {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final PlatformTenantMapper platformTenantMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;
    private final TenantRolePermissionMapper tenantRolePermissionMapper;
    private final RestTemplate restTemplate;

    @Value("${internal.user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    // ──────────────────────────────────────────────────────
    //  登录
    // ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> login(TenantLoginBody loginBody) {
        // 1. 调用 user-service 验证凭证，获取 app_user 信息
        Map<String, Object> appUser = verifyUserCredentials(loginBody.getMobile(), loginBody.getPassword());
        Long userId = toLong(appUser.get("id"));
        String mobile = (String) appUser.get("mobile");

        // 2. 查询用户所属租户（通过 user-service internal API 查 tenant_user 成员关系）
        List<Long> tenantIds = getUserTenants(userId);

        if (tenantIds.isEmpty()) {
            throw new BusinessException("该用户未加入任何租户");
        }

        if (tenantIds.size() == 1) {
            // 单租户：直接签发 JWT
            return buildTenantTokenResponse(userId, mobile, tenantIds.get(0));
        }

        // 多租户：签发 preToken，返回租户列表供前端选择
        List<Map<String, Object>> tenantList = buildTenantInfoList(tenantIds);
        String preToken = jwtUtil.createPreToken(userId, mobile, tenantList);

        // preToken jti 存 Redis（一次性使用，5min TTL）
        Claims preClaims = jwtUtil.extractAllClaims(preToken);
        String jti = jwtUtil.getJti(preClaims);
        redisUtil.set("pre_token:" + jti, "1", 5, TimeUnit.MINUTES);

        Map<String, Object> result = new HashMap<>();
        result.put("preToken", preToken);
        result.put("tenants", tenantList);
        return result;
    }

    // ──────────────────────────────────────────────────────
    //  租户选择
    // ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> selectTenant(TenantSelectBody body) {
        String preToken = body.getPreToken();
        Long tenantId = body.getTenantId();

        if (!StringUtils.hasText(preToken) || !jwtUtil.validateToken(preToken)) {
            throw new BusinessException("preToken 无效或已过期");
        }
        Claims claims = jwtUtil.extractAllClaims(preToken);
        if (!jwtUtil.isPreToken(claims)) {
            throw new BusinessException("非 preToken");
        }

        // 一次性校验：检查 jti 是否还有效
        String jti = jwtUtil.getJti(claims);
        String redisKey = "pre_token:" + jti;
        if (!redisUtil.hasKey(redisKey)) {
            throw new BusinessException("preToken 已使用，请重新登录");
        }
        // 消费掉
        redisUtil.delete(redisKey);

        Long userId = jwtUtil.getUserId(claims);
        String mobile = jwtUtil.getMobile(claims);

        // 校验用户是否属于该租户：以 user-service 的 tenant_user 成员关系为准（与登录时判断标准一致）
        // 不能仅依赖 tenant_user_role，因为用户可能尚未被分配角色但已是合法成员
        List<Long> memberTenants = getUserTenants(userId);
        if (!memberTenants.contains(tenantId)) {
            throw new BusinessException("用户不属于该租户");
        }

        return buildTenantTokenResponse(userId, mobile, tenantId);
    }

    // ──────────────────────────────────────────────────────
    //  租户切换
    // ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> switchTenant(TenantSwitchBody body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("未认证");
        }
        Long currentTenantId = TenantContext.getTenantId();
        Long newTenantId = body.getTenantId();

        // 从 SecurityContext 中取 mobile（subject）
        String mobile = auth.getName();

        // 查找 userId（从当前 TenantContext 关联）
        Long userId = findUserIdByMobileAndTenant(mobile, currentTenantId);
        if (userId == null) {
            throw new BusinessException("当前用户信息异常");
        }

        // 验证新租户成员资格
        List<Long> roleIds = tenantUserRoleMapper.selectRoleIdsByUserAndTenant(userId, newTenantId);
        if (roleIds.isEmpty()) {
            throw new BusinessException("用户不属于目标租户");
        }

        return buildTenantTokenResponse(userId, mobile, newTenantId);
    }

    // ──────────────────────────────────────────────────────
    //  刷新 Token
    // ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || !jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException("无效的 refreshToken");
        }
        Claims claims = jwtUtil.extractAllClaims(refreshToken);
        if (jwtUtil.isPlatformToken(claims)) {
            throw new BusinessException("非租户端 refreshToken");
        }

        Long userId = jwtUtil.getUserId(claims);
        Long tenantId = jwtUtil.getTenantId(claims);
        String mobile = jwtUtil.getMobile(claims);
        int tenantVersion = jwtUtil.getTenantVersion(claims);

        // 校验租户版本
        String versionKey = "tenant:version:" + tenantId;
        String storedVersion = redisUtil.get(versionKey);
        if (storedVersion != null && !storedVersion.equals(String.valueOf(tenantVersion))) {
            throw new BusinessException("租户已变更，请重新登录");
        }

        // 校验租户状态
        PlatformTenant tenant = platformTenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() == 0) {
            throw new BusinessException("租户不可用");
        }

        // 重新获取权限（保留超管身份）
        boolean isTenantAdmin = checkTenantAdmin(userId, tenantId);
        List<String> authorities = isTenantAdmin
                ? Collections.singletonList("TENANT_SUPER_ADMIN")
                : tenantRolePermissionMapper.selectUserPermissionKeys(userId, tenantId);
        authorities = sanitizeAuthorities(authorities);

        String newAccessToken = jwtUtil.createTenantToken(userId, mobile, tenantId, tenantVersion, isTenantAdmin, authorities);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }

    // ──────────────────────────────────────────────────────
    //  登出
    // ──────────────────────────────────────────────────────

    @Override
    public void logout(String bearerToken) {
        String token = stripBearer(bearerToken);
        if (!StringUtils.hasText(token)) return;
        try {
            long ttl = jwtUtil.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                Claims claims = jwtUtil.extractAllClaims(token);
                String jti = jwtUtil.getJti(claims);
                if (StringUtils.hasText(jti)) {
                    redisUtil.set("blacklist:" + jti, "1", ttl, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            log.warn("logout: token parse failed", e);
        }
        SecurityContextHolder.clearContext();
    }

    // ──────────────────────────────────────────────────────
    //  当前用户信息 / 权限
    // ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String mobile = auth.getName();
        Long tenantId = TenantContext.getTenantId();

        // 调 user-service 获取用户基本信息
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/by-mobile?mobile=" + mobile,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> userInfo = resp.getBody() != null ? resp.getBody() : new HashMap<>();
            userInfo.put("currentTenantId", tenantId);
            return userInfo;
        } catch (Exception e) {
            log.warn("getCurrentUser: user-service call failed", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("mobile", mobile);
            fallback.put("currentTenantId", tenantId);
            return fallback;
        }
    }

    @Override
    public List<String> getCurrentUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Collections.emptyList();
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return Collections.emptyList();

        // 通过 mobile 查 userId
        String mobile = auth.getName();
        Long userId = findUserIdByMobile(mobile);
        if (userId == null) return Collections.emptyList();

        return sanitizeAuthorities(tenantRolePermissionMapper.selectUserPermissionKeys(userId, tenantId));
    }

    // ──────────────────────────────────────────────────────
    //  内部工具
    // ──────────────────────────────────────────────────────

    /**
     * 构建 tenant JWT 响应（含 accessToken + refreshToken）
     */
    private Map<String, Object> buildTenantTokenResponse(Long userId, String mobile, Long tenantId) {
        // 获取租户版本
        PlatformTenant tenant = platformTenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() == 0) {
            throw new BusinessException("租户不可用");
        }
        int tenantVersion = tenant.getDataVersion() != null ? tenant.getDataVersion() : 0;

        // 写入 Redis 版本
        redisUtil.set("tenant:version:" + tenantId, String.valueOf(tenantVersion), 30, TimeUnit.DAYS);

        // 判断是否为租户超管
        boolean isTenantAdmin = checkTenantAdmin(userId, tenantId);

        // 超管直接获得所有权限，普通用户按角色分配
        List<String> authorities = isTenantAdmin
                ? Collections.singletonList("TENANT_SUPER_ADMIN")
                : tenantRolePermissionMapper.selectUserPermissionKeys(userId, tenantId);
        authorities = sanitizeAuthorities(authorities);

        String accessToken = jwtUtil.createTenantToken(userId, mobile, tenantId, tenantVersion, isTenantAdmin, authorities);
        String refreshToken = jwtUtil.createTenantRefreshToken(userId, mobile, tenantId, tenantVersion);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("tenantId", tenantId);
        result.put("tenantName", tenant.getTenantName());
        result.put("isTenantAdmin", isTenantAdmin);
        return result;
    }

    /**
     * 调用 user-service 判断用户是否为租户超管
     */
    private boolean checkTenantAdmin(Long userId, Long tenantId) {
        try {
            ResponseEntity<Boolean> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/" + userId + "/tenant-admin?tenantId=" + tenantId,
                    HttpMethod.GET, null,
                    Boolean.class);
            return Boolean.TRUE.equals(resp.getBody());
        } catch (Exception e) {
            log.warn("checkTenantAdmin failed userId={} tenantId={}", userId, tenantId, e);
            return false;
        }
    }

    /**
     * 构建租户信息列表（用于 preToken 中的 tenants 字段）
     */
    private List<Map<String, Object>> buildTenantInfoList(List<Long> tenantIds) {
        return tenantIds.stream().map(tid -> {
            PlatformTenant t = platformTenantMapper.selectById(tid);
            Map<String, Object> info = new HashMap<>();
            info.put("tenantId", tid);
            info.put("tenantName", t != null ? t.getTenantName() : "");
            info.put("tenantCode", t != null ? t.getTenantCode() : "");
            return info;
        }).collect(Collectors.toList());
    }

    /**
     * 调用 user-service internal API 验证用户凭证
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyUserCredentials(String mobile, String password) {
        try {
            Map<String, String> req = new HashMap<>();
            req.put("mobile", mobile);
            req.put("password", password);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/verify",
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(req),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body == null || body.get("id") == null) {
                throw new BusinessException("手机号或密码错误");
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("verifyUserCredentials failed", e);
            throw new BusinessException("手机号或密码错误");
        }
    }

    /**
     * 通过 user-service internal API 获取用户所属租户列表
     */
    @SuppressWarnings("unchecked")
    private List<Long> getUserTenants(Long userId) {
        try {
            ResponseEntity<List<Long>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/" + userId + "/tenants",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Long>>() {});
            List<Long> tenants = resp.getBody();
            return tenants != null ? tenants : Collections.emptyList();
        } catch (Exception e) {
            log.error("getUserTenants failed for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 通过 mobile 从 user-service 获取 userId
     */
    private Long findUserIdByMobile(String mobile) {
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userServiceUrl + "/api/internal/users/by-mobile?mobile=" + mobile,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            return body != null ? toLong(body.get("id")) : null;
        } catch (Exception e) {
            log.warn("findUserIdByMobile failed for mobile={}", mobile, e);
            return null;
        }
    }

    /**
     * 通过 mobile + tenantId 找 userId（从 tenant_user_role 反查）
     */
    private Long findUserIdByMobileAndTenant(String mobile, Long tenantId) {
        // 先通过 user-service 获取 userId
        return findUserIdByMobile(mobile);
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return Long.valueOf(val.toString());
    }

    private List<String> sanitizeAuthorities(List<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptyList();
        }
        return authorities.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private String stripBearer(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
