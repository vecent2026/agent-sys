package com.trae.admin.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", String.class, getCurrentUsername());
        this.strictInsertFill(metaObject, "updateBy", String.class, getCurrentUsername());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");
        Object updateTime = metaObject.getValue("updateTime");
        if (updateTime == null) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        } else {
            metaObject.setValue("updateTime", LocalDateTime.now());
        }
        
        Object updateBy = metaObject.getValue("updateBy");
        if (updateBy == null) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, getCurrentUsername());
        } else {
            metaObject.setValue("updateBy", getCurrentUsername());
        }
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("获取当前用户失败: {}", e.getMessage());
        }
        return "system";
    }
}
