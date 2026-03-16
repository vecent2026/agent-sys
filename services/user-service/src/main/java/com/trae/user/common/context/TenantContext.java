package com.trae.user.common.context;

/**
 * 租户上下文（ThreadLocal）
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
