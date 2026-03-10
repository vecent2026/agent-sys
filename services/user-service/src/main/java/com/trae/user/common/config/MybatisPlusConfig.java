package com.trae.user.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 分页插件配置
     *
     * 说明：
     * - 不配置 PaginationInnerInterceptor 时，MyBatis-Plus 的 selectPage 虽然接受 Page 参数，
     *   但不会自动在 SQL 中追加 LIMIT/OFFSET，导致一次性查出所有数据。
     * - 这里显式为 MySQL 注册分页拦截器，并设置：超出最大页时回到第一页，以及单页最大条数上限。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // page > 最大页时是否回到首页，避免前端传入过大页码导致空数据
        paginationInnerInterceptor.setOverflow(true);
        // 单页最大条数限制，防止误传超大 size 导致一次性查全表（按需求可调整或置为 null）
        paginationInnerInterceptor.setMaxLimit(1000L);

        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }
}
