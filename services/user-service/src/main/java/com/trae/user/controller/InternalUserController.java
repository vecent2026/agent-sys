package com.trae.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.common.result.Result;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.TenantUser;
import com.trae.user.mapper.AppUserMapper;
import com.trae.user.mapper.TenantUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 服务间内部 API（由 Spring Security 白名单放行，需网关层 IP 保护）
 * 路径前缀：/api/internal
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final AppUserMapper appUserMapper;
    private final TenantUserMapper tenantUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 通过手机号查询 app_user
     */
    @GetMapping("/users/by-mobile")
    public Map<String, Object> getUserByMobile(@RequestParam String mobile) {
        AppUser user = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>().eq(AppUser::getMobile, mobile));
        if (user == null) return Collections.emptyMap();
        return toMap(user);
    }

    /**
     * 验证用户凭证（手机号+密码），成功返回用户信息
     */
    @PostMapping("/users/verify")
    public Map<String, Object> verifyUser(@RequestBody Map<String, String> body) {
        String mobile = body.get("mobile");
        String password = body.get("password");

        AppUser user = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getMobile, mobile)
                        .eq(AppUser::getIsDeleted, 0));
        if (user == null) {
            return Collections.emptyMap(); // admin-service 会将空 map 视为认证失败
        }
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Collections.emptyMap();
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Collections.emptyMap();
        }
        return toMap(user);
    }

    /**
     * 批量查询 app_user（by id 列表）
     */
    @GetMapping("/users/batch")
    public List<Map<String, Object>> getUsersBatch(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (idList.isEmpty()) return Collections.emptyList();
        return appUserMapper.selectBatchIds(idList).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    /**
     * 幂等创建 app_user（按 mobile 查或创建）
     */
    @PostMapping("/users/ensure")
    public Map<String, Object> ensureUser(@RequestBody Map<String, Object> body) {
        String mobile = (String) body.get("mobile");
        AppUser existing = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>().eq(AppUser::getMobile, mobile));
        if (existing != null) return toMap(existing);

        AppUser newUser = new AppUser();
        newUser.setMobile(mobile);
        newUser.setNickname((String) body.getOrDefault("nickname", mobile));
        newUser.setStatus(1);
        newUser.setIsDeleted(0);
        newUser.setRegisterTime(LocalDateTime.now());
        if (body.get("password") != null) {
            newUser.setPassword(passwordEncoder.encode((String) body.get("password")));
        }
        appUserMapper.insert(newUser);
        return toMap(newUser);
    }

    /**
     * 查询用户所属租户 ID 列表
     */
    @GetMapping("/users/{userId}/tenants")
    public List<Long> getUserTenants(@PathVariable Long userId) {
        return tenantUserMapper.selectList(
                new LambdaQueryWrapper<TenantUser>()
                        .eq(TenantUser::getUserId, userId)
                        .select(TenantUser::getTenantId))
                .stream()
                .map(TenantUser::getTenantId)
                .collect(Collectors.toList());
    }

    /**
     * 写入 tenant_user 关系（幂等）
     */
    @PostMapping("/tenant-users")
    public Result<Void> addTenantUser(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long tenantId = Long.valueOf(body.get("tenantId").toString());
        Integer isAdmin = body.get("isAdmin") != null ? Integer.valueOf(body.get("isAdmin").toString()) : 0;

        long exists = tenantUserMapper.selectCount(
                new LambdaQueryWrapper<TenantUser>()
                        .eq(TenantUser::getUserId, userId)
                        .eq(TenantUser::getTenantId, tenantId));
        if (exists == 0) {
            TenantUser tu = new TenantUser();
            tu.setUserId(userId);
            tu.setTenantId(tenantId);
            tu.setIsAdmin(isAdmin);
            tu.setJoinTime(LocalDateTime.now());
            tenantUserMapper.insert(tu);
        }
        return Result.success();
    }

    /**
     * 删除 tenant_user 关系
     */
    @DeleteMapping("/tenant-users")
    public Result<Void> removeTenantUser(@RequestParam Long userId, @RequestParam Long tenantId) {
        tenantUserMapper.delete(
                new LambdaQueryWrapper<TenantUser>()
                        .eq(TenantUser::getUserId, userId)
                        .eq(TenantUser::getTenantId, tenantId));
        return Result.success();
    }

    // ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(AppUser user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("mobile", user.getMobile());
        m.put("nickname", user.getNickname());
        m.put("avatar", user.getAvatar());
        m.put("email", user.getEmail());
        m.put("status", user.getStatus());
        m.put("gender", user.getGender());
        m.put("birthday", user.getBirthday());
        m.put("registerTime", user.getRegisterTime());
        return m;
    }
}
