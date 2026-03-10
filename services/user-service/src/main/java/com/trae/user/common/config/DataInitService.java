package com.trae.user.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUserField;
import com.trae.user.mapper.AppUserFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitService implements CommandLineRunner {

    private final AppUserFieldMapper appUserFieldMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        try {
            long count = appUserFieldMapper.selectCount(new LambdaQueryWrapper<AppUserField>()
                    .eq(AppUserField::getIsDefault, 1));
            
            if (count == 0) {
                log.info("Initializing default user fields...");
                initDefaultFields();
                log.info("Default user fields initialization completed.");
            } else {
                log.info("Default user fields already exist, skipping initialization.");
            }
        } catch (Exception e) {
            log.error("Default user fields initialization failed", e);
        }
    }

    private void initDefaultFields() {
        List<AppUserField> defaultFields = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        defaultFields.add(createField("昵称", "nickname", "TEXT", 
                "{\"maxLength\": 50}", 1, 1, 1, 1, now));
        defaultFields.add(createField("头像", "avatar", "LINK", 
                "{\"linkType\": \"image\"}", 0, 1, 1, 2, now));
        defaultFields.add(createField("手机号", "mobile", "TEXT", 
                "{\"maxLength\": 20}", 0, 1, 1, 101, now));
        defaultFields.add(createField("邮箱", "email", "TEXT", 
                "{\"maxLength\": 100}", 0, 1, 1, 102, now));
        defaultFields.add(createField("性别", "gender", "RADIO", 
                "{\"options\": [{\"label\": \"未知\", \"value\": \"0\"}, {\"label\": \"男\", \"value\": \"1\"}, {\"label\": \"女\", \"value\": \"2\"}]}", 
                0, 1, 1, 103, now));
        defaultFields.add(createField("生日", "birthday", "DATE", 
                "{}", 0, 1, 1, 104, now));
        defaultFields.add(createField("注册来源", "register_source", "TEXT", 
                "{\"maxLength\": 20}", 1, 1, 1, 105, now));
        defaultFields.add(createField("注册时间", "register_time", "DATE", 
                "{}", 1, 1, 1, 105, now)); // Technically 105.5 wouldn't fit in Integer, we will set it to 105 and it will order by id for identical sorts
        defaultFields.add(createField("最后登录时间", "last_login_time", "DATE", 
                "{}", 0, 1, 1, 106, now));
        defaultFields.add(createField("最后登录IP", "last_login_ip", "TEXT", 
                "{\"maxLength\": 50}", 0, 1, 1, 107, now));
        defaultFields.add(createField("账号状态", "status", "RADIO", 
                "{\"options\": [{\"label\": \"正常\", \"value\": \"1\"}, {\"label\": \"禁用\", \"value\": \"0\"}, {\"label\": \"注销\", \"value\": \"2\"}]}", 
                1, 1, 1, 108, now));

        defaultFields.forEach(appUserFieldMapper::insert);
    }

    private AppUserField createField(String fieldName, String fieldKey, String fieldType,
                                     String config, Integer isRequired, Integer isDefault,
                                     Integer status, Integer sort, LocalDateTime now) {
        AppUserField field = new AppUserField();
        field.setFieldName(fieldName);
        field.setFieldKey(fieldKey);
        field.setFieldType(fieldType);
        field.setConfig(config);
        field.setIsRequired(isRequired);
        field.setIsDefault(isDefault);
        field.setStatus(status);
        field.setSort(sort);
        field.setCreateTime(now);
        field.setUpdateTime(now);
        return field;
    }
}
