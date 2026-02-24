package com.trae.admin.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置类
 */
@Configuration
@MapperScan("com.trae.admin.**.mapper")
public class MybatisPlusConfig {
}