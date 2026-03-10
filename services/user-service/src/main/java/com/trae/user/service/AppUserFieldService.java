package com.trae.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.dto.AppUserFieldDTO;
import com.trae.user.entity.AppUserField;
import com.trae.user.vo.AppUserFieldVO;

import java.util.List;

public interface AppUserFieldService extends IService<AppUserField> {
    Page<AppUserFieldVO> getFieldPage(Integer page, Integer size, String name, String type, Integer status);
    List<AppUserFieldVO> getEnabledFields();
    void createField(AppUserFieldDTO fieldDTO);
    void updateField(Long id, AppUserFieldDTO fieldDTO);
    void deleteField(Long id);
    void updateFieldStatus(Long id, Integer status);
    void sortFields(List<Long> fieldIds);
}
