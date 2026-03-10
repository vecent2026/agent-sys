package com.trae.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.entity.AppUserFieldValue;
import com.trae.user.mapper.AppUserFieldValueMapper;
import com.trae.user.service.AppUserFieldValueService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserFieldValueServiceImpl extends ServiceImpl<AppUserFieldValueMapper, AppUserFieldValue> implements AppUserFieldValueService {

    @Override
    public List<AppUserFieldValue> getByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(AppUserFieldValue::getUserId, userId)
                .list();
    }

    @Override
    public void saveOrUpdateFieldValue(Long userId, Long fieldId, String fieldValue) {
        AppUserFieldValue existing = this.lambdaQuery()
                .eq(AppUserFieldValue::getUserId, userId)
                .eq(AppUserFieldValue::getFieldId, fieldId)
                .one();
        
        if (existing != null) {
            existing.setFieldValue(fieldValue);
            this.updateById(existing);
        } else {
            AppUserFieldValue newValue = new AppUserFieldValue();
            newValue.setUserId(userId);
            newValue.setFieldId(fieldId);
            newValue.setFieldValue(fieldValue);
            this.save(newValue);
        }
    }

    @Override
    public void deleteByFieldId(Long fieldId) {
        this.lambdaUpdate()
                .eq(AppUserFieldValue::getFieldId, fieldId)
                .remove();
    }
}
