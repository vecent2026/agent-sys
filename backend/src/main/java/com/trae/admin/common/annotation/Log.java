package com.trae.admin.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    
    /**
     * 模块
     */
    String module() default "";

    /**
     * 动作
     */
    String action() default "";
    
    /**
     * 是否开启日志记录
     */
    boolean enabled() default true;
}
