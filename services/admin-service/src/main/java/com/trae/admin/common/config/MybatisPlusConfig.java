package com.trae.admin.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.trae.admin.common.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 配置：
 * 1. TenantLineInnerInterceptor — 自动注入 tenant_id 条件（平台表加白名单跳过）
 * 2. PaginationInnerInterceptor — 分页支持
 */
@Configuration
@MapperScan("com.trae.admin.**.mapper")
public class MybatisPlusConfig {

    /**
     * 不需要 tenant_id 过滤的表（平台级别表 + 多租户关联表自身）
     */
    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
            "platform_user",
            "platform_tenant",
            "platform_permission",
            "platform_role",
            "platform_user_role",
            "platform_role_permission",
            "tenant_permission",
            "tenant_role_permission",
            "tenant_user_role",
            "operation_log"
    ));

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 租户隔离插件（优先级最高）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                // 平台端请求 tenantId 为 null，返回 -1 防止意外过滤
                return new LongValue(tenantId != null ? tenantId : -1L);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TABLES.contains(tableName);
            }
        }));

        // 2. 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
