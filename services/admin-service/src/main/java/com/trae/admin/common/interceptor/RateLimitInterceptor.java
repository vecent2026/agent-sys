package com.trae.admin.common.interceptor;

import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.utils.IpUtil;
import com.trae.admin.common.utils.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisUtil redisUtil;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    @Override
    public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String clientIp = IpUtil.getIpAddress(request);
        String key = RATE_LIMIT_PREFIX + uri + ":" + clientIp;

        // Login endpoint limit: 5 requests per minute
        if ("/api/auth/login".equals(uri)
                || "/api/platform/auth/login".equals(uri)
                || "/api/tenant/auth/login".equals(uri)) {
            Long count = redisUtil.increment(key);
            if (count == 1) {
                redisUtil.set(key, count.toString(), 1, TimeUnit.MINUTES);
            }
            if (count > 5) {
                log.warn("Rate limit exceeded for IP: {}, URI: {}", clientIp, uri);
                throw new BusinessException("请求过于频繁，请稍后再试");
            }
        }

        return true;
    }
}
