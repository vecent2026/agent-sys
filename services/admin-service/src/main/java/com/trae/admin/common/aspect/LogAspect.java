package com.trae.admin.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.security.CustomUserDetails;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.IpUtil;
import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LogAspect {

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Pointcut("@annotation(com.trae.admin.common.annotation.Log)")
    public void logPointcut() {
    }

    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result = null;
        String errorMsg = null;
        
        try {
            result = point.proceed();
        } catch (Throwable e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long time = System.currentTimeMillis() - beginTime;
            saveLog(point, time, result, errorMsg);
        }
        return result;
    }

    void saveLog(ProceedingJoinPoint point, long time, Object result, String errorMsg) {
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            Log logAnnotation = method.getAnnotation(Log.class);
            
            // Check if log is enabled
            if (logAnnotation == null || !logAnnotation.enabled()) {
                return;
            }

            SysLogDocument sysLog = new SysLogDocument();
            sysLog.setId(UUID.randomUUID().toString());
            sysLog.setTraceId(MDC.get("traceId"));
            sysLog.setCreateTime(new Date());
            
            if (logAnnotation != null) {
                sysLog.setModule(logAnnotation.module());
                sysLog.setAction(logAnnotation.action());
                
                // Try to resolve dynamic action/module name from permission configuration
                try {
                    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                    if (preAuthorize != null) {
                        String expression = preAuthorize.value();
                        // Match hasAuthority('xxx')
                        Pattern pattern = Pattern.compile("hasAuthority\\('([^']+)'\\)");
                        Matcher matcher = pattern.matcher(expression);
                        if (matcher.find()) {
                            String permissionKey = matcher.group(1);
                            SysPermission permission = sysPermissionMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                                    .eq(SysPermission::getPermissionKey, permissionKey)
                            );
                            
                            if (permission != null) {
                                // Use permission name as action
                                sysLog.setAction(permission.getName());
                                
                                // If parent exists, use parent name as module
                                if (permission.getParentId() != null && permission.getParentId() != 0) {
                                    SysPermission parent = sysPermissionMapper.selectById(permission.getParentId());
                                    if (parent != null) {
                                        sysLog.setModule(parent.getName());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore dynamic resolution errors, fallback to annotation values
                    log.warn("Failed to resolve dynamic log names: {}", e.getMessage());
                }
            }

            // Get request attributes
            Claims jwtClaims = null;
            Boolean isPlatform = null;
            Long tenantId = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                // Request info
                HttpServletRequest request = attributes.getRequest();
                String methodType = request.getMethod();
                
                // Only log POST/PUT/DELETE requests for business operations
                // Skip GET requests for business operations, but still log login events
                if (!logAnnotation.module().equals("系统认证") && !logAnnotation.action().equals("用户登录") && 
                    !methodType.equals("POST") && !methodType.equals("PUT") && !methodType.equals("DELETE")) {
                    return;
                }

                sysLog.setIp(IpUtil.getIpAddress(request));

                try {
                    String authorizationHeader = request.getHeader("Authorization");
                    if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                        jwtClaims = jwtUtil.extractAllClaims(authorizationHeader.substring(7));
                        isPlatform = jwtUtil.isPlatformToken(jwtClaims);
                        tenantId = jwtUtil.getTenantId(jwtClaims);
                    }
                } catch (Exception ignore) {
                    // ignore bad/missing token for logging fallback
                }

                if (isPlatform == null) {
                    String uri = request.getRequestURI();
                    if (uri != null) {
                        if (uri.startsWith("/api/platform/")) {
                            isPlatform = true;
                        } else if (uri.startsWith("/api/tenant/")) {
                            isPlatform = false;
                        }
                    }
                }
            } else {
                // No request attributes, this might be a login event or other special case
                // Set default IP for login events without request
                sysLog.setIp("unknown");
            }

            // User info
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                try {
                    if (principal instanceof CustomUserDetails userDetails) {
                        sysLog.setUserId(userDetails.getUserId());
                        sysLog.setUsername(userDetails.getUsername());
                    } else if (principal instanceof String principalName && !"anonymousUser".equals(principalName)) {
                        sysLog.setUsername(principalName);
                    }
                } catch (Exception ignore) {
                    // Ignore
                }
            }

            if (jwtClaims != null) {
                if (sysLog.getUserId() == null) {
                    sysLog.setUserId(jwtUtil.getUserId(jwtClaims));
                }
                if (!StringUtils.hasText(sysLog.getUsername())) {
                    sysLog.setUsername(jwtClaims.getSubject());
                }
                if (isPlatform == null) {
                    isPlatform = jwtUtil.isPlatformToken(jwtClaims);
                }
                if (tenantId == null) {
                    tenantId = jwtUtil.getTenantId(jwtClaims);
                }
            }

            if (tenantId == null) {
                tenantId = TenantContext.getTenantId();
            }
            if (isPlatform == null) {
                isPlatform = tenantId == null;
            }
            sysLog.setIsPlatform(isPlatform);
            sysLog.setTenantId(Boolean.TRUE.equals(isPlatform) ? null : tenantId);

            // Params
            Object[] args = point.getArgs();
            try {
                String params = objectMapper.writeValueAsString(args);
                params = desensitize(params);
                sysLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
            } catch (Exception e) {
                // Ignore
            }

            // Result and Status
            if (errorMsg != null) {
                sysLog.setErrorMsg(errorMsg);
                sysLog.setStatus("FAIL");
            } else {
                sysLog.setStatus("SUCCESS");
                try {
                    String resultStr = objectMapper.writeValueAsString(result);
                    resultStr = desensitize(resultStr);
                    sysLog.setResult(resultStr.length() > 2000 ? resultStr.substring(0, 2000) : resultStr);
                } catch (Exception e) {
                    // Ignore
                }
            }

            sysLog.setCostTime(time);
            
            // Send to Kafka
            kafkaTemplate.send("sys-log-topic", sysLog).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.error("Failed to send log to Kafka: {}", ex.getMessage());
                } else {
                    log.info("Log sent to Kafka successfully: {}", sysLog.getId());
                }
            });
            
        } catch (Exception e) {
            log.error("Save log failed", e);
        }
    }



    String desensitize(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // Desensitize password field
        content = content.replaceAll("\"password\":\"[^\"]+\"", "\"password\":\"******\"");

        // Desensitize token field
        content = content.replaceAll("\"token\":\"[^\"]+\"", "\"token\":\"******\"");

        // Desensitize accessToken field
        content = content.replaceAll("\"accessToken\":\"[^\"]+\"", "\"accessToken\":\"******\"");

        // Desensitize refreshToken field
        content = content.replaceAll("\"refreshToken\":\"[^\"]+\"", "\"refreshToken\":\"******\"");

        return content;
    }
}
