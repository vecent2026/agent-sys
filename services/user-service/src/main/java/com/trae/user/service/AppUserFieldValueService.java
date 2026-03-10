package com.trae.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.entity.AppUserFieldValue;

import java.util.List;

public interface AppUserFieldValueService extends IService<AppUserFieldValue> {
    List<AppUserFieldValue> getByUserId(Long userId);
    void saveOrUpdateFieldValue(Long userId, Long fieldId, String fieldValue);
    void deleteByFieldId(Long fieldId);
}
