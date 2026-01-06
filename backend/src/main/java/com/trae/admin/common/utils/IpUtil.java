package com.trae.admin.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP工具类
 */
public class IpUtil {
    
    private IpUtil() {
        // Utility class, not instantiable
    }
    
    /**
     * 获取真实IP地址，将IPv6转换为IPv4
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Convert IPv6 to IPv4 if needed
        if (ip != null && ip.startsWith("0:0:0:0:0:0:0:1")) {
            ip = "127.0.0.1";
        }
        
        // If multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}