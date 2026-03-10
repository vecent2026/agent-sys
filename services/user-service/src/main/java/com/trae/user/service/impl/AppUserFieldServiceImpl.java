package com.trae.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.common.exception.BusinessException;
import com.trae.user.dto.AppUserFieldDTO;
import com.trae.user.entity.AppUserField;
import com.trae.user.mapper.AppUserFieldMapper;
import com.trae.user.service.AppUserFieldService;
import com.trae.user.service.AppUserFieldValueService;
import com.trae.user.vo.AppUserFieldVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppUserFieldServiceImpl extends ServiceImpl<AppUserFieldMapper, AppUserField> implements AppUserFieldService {

    private final AppUserFieldValueService fieldValueService;

    private static final Set<String> RESERVED_KEYS = new HashSet<>(Arrays.asList(
            "id", "nickname", "avatar", "mobile", "email", "gender", "birthday",
            "register_source", "status", "register_time", "last_login_time", "last_login_ip",
            "create_time", "update_time"
    ));

    @Override
    public Page<AppUserFieldVO> getFieldPage(Integer page, Integer size, String name, String type, Integer status) {
        Page<AppUserField> fieldPage = new Page<>(page, size);
        LambdaQueryWrapper<AppUserField> wrapper = new LambdaQueryWrapper<>();
        
        if (StrUtil.isNotBlank(name)) {
            wrapper.like(AppUserField::getFieldName, name);
        }
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(AppUserField::getFieldType, type);
        }
        if (status != null) {
            wrapper.eq(AppUserField::getStatus, status);
        }
        
        wrapper.orderByAsc(AppUserField::getSort);
        
        Page<AppUserField> result = this.page(fieldPage, wrapper);
        
        Page<AppUserFieldVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<AppUserFieldVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }

    @Override
    public List<AppUserFieldVO> getEnabledFields() {
        return this.lambdaQuery()
                .eq(AppUserField::getStatus, 1)
                .orderByAsc(AppUserField::getSort)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void createField(AppUserFieldDTO fieldDTO) {
        validateFieldKey(fieldDTO.getFieldKey());
        
        Integer maxSort = this.lambdaQuery()
                .select(AppUserField::getSort)
                .orderByDesc(AppUserField::getSort)
                .last("LIMIT 1")
                .one()
                .getSort();
        
        AppUserField field = new AppUserField();
        BeanUtils.copyProperties(fieldDTO, field);
        field.setIsDefault(0);
        if (field.getSort() == null) {
            field.setSort(maxSort + 1);
        }
        this.save(field);
    }

    @Override
    public void updateField(Long id, AppUserFieldDTO fieldDTO) {
        AppUserField existing = this.getById(id);
        if (existing != null && existing.getIsDefault() == 1) {
            existing.setSort(fieldDTO.getSort());
            this.updateById(existing);
        } else {
            AppUserField field = new AppUserField();
            field.setId(id);
            BeanUtils.copyProperties(fieldDTO, field);
            this.updateById(field);
        }
    }

    @Override
    @Transactional
    public void deleteField(Long id) {
        AppUserField field = this.getById(id);
        if (field != null && field.getIsDefault() == 1) {
            throw new BusinessException("默认字段不可删除");
        }
        
        fieldValueService.deleteByFieldId(id);
        this.removeById(id);
    }

    @Override
    public void updateFieldStatus(Long id, Integer status) {
        AppUserField field = this.getById(id);
        if (field != null && field.getIsDefault() == 1 && status == 0) {
            throw new BusinessException("默认字段不可禁用");
        }
        
        AppUserField updateField = new AppUserField();
        updateField.setId(id);
        updateField.setStatus(status);
        this.updateById(updateField);
    }

    @Override
    @Transactional
    public void sortFields(List<Long> fieldIds) {
        for (int i = 0; i < fieldIds.size(); i++) {
            AppUserField field = new AppUserField();
            field.setId(fieldIds.get(i));
            field.setSort(i + 1);
            this.updateById(field);
        }
    }

    private void validateFieldKey(String fieldKey) {
        if (RESERVED_KEYS.contains(fieldKey.toLowerCase())) {
            throw new BusinessException("字段标识不能与系统保留字段冲突: " + fieldKey);
        }
    }

    private AppUserFieldVO toVO(AppUserField field) {
        AppUserFieldVO vo = new AppUserFieldVO();
        BeanUtils.copyProperties(field, vo);
        return vo;
    }
}
