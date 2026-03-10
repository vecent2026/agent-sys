package com.trae.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.excel.EasyExcel;
import com.trae.user.cache.ImportCacheService;
import com.trae.user.common.exception.BusinessException;
import com.trae.user.dto.FilterConditionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.trae.user.dto.AppUserQueryDTO;
import com.trae.user.dto.BatchTagDTO;
import com.trae.user.dto.UserImportDTO;
import com.trae.user.dto.UserStatusDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.AppUserField;
import com.trae.user.entity.AppUserFieldValue;
import com.trae.user.entity.AppUserTag;
import com.trae.user.entity.AppUserTagRelation;
import com.trae.user.executor.ImportTaskExecutor;

import com.trae.user.mapper.AppUserMapper;
import com.trae.user.service.*;
import com.trae.user.vo.AppUserVO;
import com.trae.user.vo.UserFieldValueVO;
import com.trae.user.model.ExportUserModel;
import com.trae.user.model.ImportTemplateModel;
import com.trae.user.model.ValidateResultModel;
import com.trae.user.vo.UserImportProgressVO;
import com.trae.user.vo.UserImportValidateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {

    private final AppUserTagService tagService;
    private final AppUserTagRelationService tagRelationService;
    private final AppUserFieldService fieldService;
    private final AppUserFieldValueService fieldValueService;
    private final ImportCacheService cacheService;
    private final ImportTaskExecutor importTaskExecutor;

    private static final int MAX_IMPORT_COUNT = 20000;
    private static final int MAX_EXPORT_COUNT = 50000;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int BATCH_QUERY_SIZE = 1000;
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private void applyFilters(LambdaQueryWrapper<AppUser> wrapper, AppUserQueryDTO queryDTO) {
        if (StrUtil.isBlank(queryDTO.getFilters())) {
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            List<FilterConditionDTO> filters = objectMapper.readValue(queryDTO.getFilters(), new TypeReference<List<FilterConditionDTO>>() {});
            
            if (filters.isEmpty()) {
                return;
            }

            boolean isOr = "OR".equalsIgnoreCase(queryDTO.getFilterLogic());

            if (isOr) {
                wrapper.and(w -> {
                    for (FilterConditionDTO filter : filters) {
                        w.or(subW -> applySingleFilter(subW, filter));
                    }
                });
            } else {
                for (FilterConditionDTO filter : filters) {
                    wrapper.and(w -> applySingleFilter(w, filter));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse filters", e);
        }
    }

    private void applySingleFilter(LambdaQueryWrapper<AppUser> wrapper, FilterConditionDTO filter) {
        String field = filter.getField();
        String op = filter.getOperator();
        Object val = filter.getValue();
        
        if ("nickname".equals(field)) {
            applyStringFilter(wrapper, AppUser::getNickname, op, val);
        } else if ("mobile".equals(field)) {
            applyStringFilter(wrapper, AppUser::getMobile, op, val);
        } else if ("email".equals(field)) {
            applyStringFilter(wrapper, AppUser::getEmail, op, val);
        } else if ("gender".equals(field)) {
            applyEnumFilter(wrapper, AppUser::getGender, op, val);
        } else if ("status".equals(field)) {
            applyEnumFilter(wrapper, AppUser::getStatus, op, val);
        } else if ("registerSource".equals(field) || "source".equals(field)) {
            applyStringFilter(wrapper, AppUser::getRegisterSource, op, val);
        } else if ("registerDate".equals(field) || "register_time".equals(field)) {
            applyDateFilter(wrapper, AppUser::getRegisterTime, op, val, filter.getType());
        } else if ("last_login_time".equals(field)) {
            applyDateFilter(wrapper, AppUser::getLastLoginTime, op, val, filter.getType());
        } else if ("birthday".equals(field)) {
            applyLocalDateFilter(wrapper, AppUser::getBirthday, op, val);
        } else {
            applyCustomFieldFilter(wrapper, field, op, val);
        }
    }

    private void applyStringFilter(LambdaQueryWrapper<AppUser> wrapper, SFunction<AppUser, String> column, String op, Object val) {
        if ("empty".equals(op) || "为空".equals(op)) {
            wrapper.isNull(column).or().eq(column, "");
            return;
        }
        if ("not_empty".equals(op) || "不为空".equals(op)) {
            wrapper.isNotNull(column).ne(column, "");
            return;
        }
        if (val == null) return;
        String value = val.toString();
        if ("equals".equals(op) || "等于".equals(op)) {
            wrapper.eq(column, value);
        } else if ("not_equals".equals(op) || "不等于".equals(op)) {
            wrapper.ne(column, value);
        } else if ("contains".equals(op) || "包含".equals(op)) {
            wrapper.like(column, value);
        } else if ("not_contains".equals(op) || "不包含".equals(op)) {
            wrapper.notLike(column, value);
        }
    }

    private void applyEnumFilter(LambdaQueryWrapper<AppUser> wrapper, SFunction<AppUser, Integer> column, String op, Object val) {
        if ("empty".equals(op) || "为空".equals(op)) {
            wrapper.isNull(column);
            return;
        }
        if ("not_empty".equals(op) || "不为空".equals(op)) {
            wrapper.isNotNull(column);
            return;
        }
        if (val == null) return;
        Integer value = null;
        try {
            value = Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            if ("正常".equals(val)) value = 1;
            else if ("禁用".equals(val)) value = 0;
            else if ("注销".equals(val)) value = 2;
            else if ("男".equals(val)) value = 1;
            else if ("女".equals(val)) value = 2;
            else if ("未知".equals(val)) value = 0;
        }
        if (value == null) return;

        if ("equals".equals(op) || "等于".equals(op)) {
            wrapper.eq(column, value);
        } else if ("not_equals".equals(op) || "不等于".equals(op)) {
            wrapper.ne(column, value);
        }
    }

    private void applyLocalDateFilter(LambdaQueryWrapper<AppUser> wrapper, SFunction<AppUser, LocalDate> column, String op, Object val) {
        if ("empty".equals(op) || "为空".equals(op)) {
            wrapper.isNull(column);
            return;
        }
        if ("not_empty".equals(op) || "不为空".equals(op)) {
            wrapper.isNotNull(column);
            return;
        }
        if (val == null) return;
        
        LocalDate dateValue = null;
        try {
            String dateStr = val.toString().trim();
            if (dateStr.length() > 10) {
                dateStr = dateStr.substring(0, 10);
            }
            dateValue = LocalDate.parse(dateStr);
        } catch (Exception e) {
            // ignore
        }
        if (dateValue == null) return;

        if ("equals".equals(op) || "等于".equals(op)) {
            wrapper.eq(column, dateValue);
        } else if ("gt".equals(op) || "晚于".equals(op)) {
            wrapper.gt(column, dateValue);
        } else if ("lt".equals(op) || "早于".equals(op)) {
            wrapper.lt(column, dateValue);
        }
    }

    private void applyDateFilter(LambdaQueryWrapper<AppUser> wrapper, SFunction<AppUser, LocalDateTime> column, String op, Object val, String type) {
        if ("empty".equals(op) || "为空".equals(op)) {
            wrapper.isNull(column);
            return;
        }
        if ("not_empty".equals(op) || "不为空".equals(op)) {
            wrapper.isNotNull(column);
            return;
        }
        if (val == null) return;
        
        LocalDateTime dateValue = null;
        try {
            dateValue = LocalDateTime.parse(val.toString().replace(" ", "T"));
        } catch (Exception e) {
            try {
                dateValue = LocalDate.parse(val.toString()).atStartOfDay();
            } catch (Exception ex) {
                // ignore
            }
        }
        if (dateValue == null) return;

        if ("equals".equals(op) || "等于".equals(op)) {
            wrapper.ge(column, dateValue.toLocalDate().atStartOfDay())
                   .lt(column, dateValue.toLocalDate().plusDays(1).atStartOfDay());
        } else if ("gt".equals(op) || "晚于".equals(op)) {
            wrapper.gt(column, dateValue);
        } else if ("lt".equals(op) || "早于".equals(op)) {
            wrapper.lt(column, dateValue);
        }
    }

    private void applyCustomFieldFilter(LambdaQueryWrapper<AppUser> wrapper, String fieldKey, String op, Object val) {
        AppUserField field = fieldService.lambdaQuery().eq(AppUserField::getFieldKey, fieldKey).one();
        if (field == null) return;

        LambdaQueryWrapper<AppUserFieldValue> fvWrapper = new LambdaQueryWrapper<>();
        fvWrapper.eq(AppUserFieldValue::getFieldId, field.getId());
        
        if ("empty".equals(op) || "为空".equals(op)) {
            fvWrapper.isNull(AppUserFieldValue::getFieldValue).or().eq(AppUserFieldValue::getFieldValue, "");
            List<Long> userIdsWithEmpty = fieldValueService.list(fvWrapper).stream()
                    .map(AppUserFieldValue::getUserId)
                    .collect(Collectors.toList());
            
            if (!userIdsWithEmpty.isEmpty()) {
                wrapper.in(AppUser::getId, userIdsWithEmpty);
            } else {
                wrapper.eq(AppUser::getId, -1L);
            }
            return;
        }
        
        if ("not_empty".equals(op) || "不为空".equals(op)) {
            fvWrapper.isNotNull(AppUserFieldValue::getFieldValue).ne(AppUserFieldValue::getFieldValue, "");
        } else if (val != null) {
            String value = val.toString();
            if ("equals".equals(op) || "等于".equals(op)) {
                fvWrapper.eq(AppUserFieldValue::getFieldValue, value);
            } else if ("not_equals".equals(op) || "不等于".equals(op)) {
                fvWrapper.ne(AppUserFieldValue::getFieldValue, value);
            } else if ("contains".equals(op) || "包含".equals(op)) {
                fvWrapper.like(AppUserFieldValue::getFieldValue, value);
            } else if ("not_contains".equals(op) || "不包含".equals(op)) {
                fvWrapper.notLike(AppUserFieldValue::getFieldValue, value);
            } else if ("gt".equals(op) || "晚于".equals(op)) {
                fvWrapper.gt(AppUserFieldValue::getFieldValue, value);
            } else if ("lt".equals(op) || "早于".equals(op)) {
                fvWrapper.lt(AppUserFieldValue::getFieldValue, value);
            }
        }

        List<Long> userIds = fieldValueService.list(fvWrapper).stream()
                .map(AppUserFieldValue::getUserId)
                .collect(Collectors.toList());
        
        if (userIds.isEmpty()) {
            wrapper.eq(AppUser::getId, -1L);
        } else if (userIds.size() > 5000) {
            throw new BusinessException("该筛选条件下包含过多用户（超过5000），为避免影响系统性能，请增加其他过滤条件后再试。");
        } else {
            wrapper.in(AppUser::getId, userIds);
        }
    }

    private LambdaQueryWrapper<AppUser> buildQueryWrapper(AppUserQueryDTO queryDTO) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        
        // New logic for generic filters
        applyFilters(wrapper, queryDTO);
        
        boolean hasFilters = StrUtil.isNotBlank(queryDTO.getFilters());
        boolean isOr = "OR".equalsIgnoreCase(queryDTO.getFilterLogic());
        
        if (hasFilters && isOr) {
            return wrapper;
        }
        
        if (StrUtil.isNotBlank(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(AppUser::getNickname, queryDTO.getKeyword())
                    .or().like(AppUser::getMobile, queryDTO.getKeyword()));
        }
        if (StrUtil.isNotBlank(queryDTO.getRegisterSource())) {
            wrapper.eq(AppUser::getRegisterSource, queryDTO.getRegisterSource());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(AppUser::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getRegisterStartTime() != null) {
            wrapper.ge(AppUser::getRegisterTime, queryDTO.getRegisterStartTime());
        }
        if (queryDTO.getRegisterEndTime() != null) {
            wrapper.le(AppUser::getRegisterTime, queryDTO.getRegisterEndTime());
        }
        
        if (StrUtil.isNotBlank(queryDTO.getTagIds())) {
            List<Long> tagIdList = Arrays.stream(queryDTO.getTagIds().split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            
            if (!tagIdList.isEmpty()) {
                List<Long> userIdsByTag = tagRelationService.lambdaQuery()
                        .in(AppUserTagRelation::getTagId, tagIdList)
                        .list()
                        .stream()
                        .map(AppUserTagRelation::getUserId)
                        .distinct()
                        .collect(Collectors.toList());
                
                if (userIdsByTag.isEmpty()) {
                    wrapper.eq(AppUser::getId, -1L);
                } else {
                    wrapper.in(AppUser::getId, userIdsByTag);
                }
            }
        }
        
        return wrapper;
    }

    @Override
    public Page<AppUserVO> getUserPage(AppUserQueryDTO queryDTO) {
        Page<AppUser> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<AppUser> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(AppUser::getLastLoginTime);
        
        Page<AppUser> userPage = this.page(page, wrapper);
        
        List<AppUserField> allFields = fieldService.lambdaQuery()
                .eq(AppUserField::getStatus, 1)
                .eq(AppUserField::getIsDefault, 0)
                .orderByAsc(AppUserField::getSort)
                .list();
        
        List<Long> userIds = userPage.getRecords().stream()
                .map(AppUser::getId)
                .collect(Collectors.toList());
        
        final Map<Long, List<AppUserFieldValue>> fieldValueMap;
        if (!userIds.isEmpty()) {
            List<AppUserFieldValue> allFieldValues = fieldValueService.lambdaQuery()
                    .in(AppUserFieldValue::getUserId, userIds)
                    .list();
            fieldValueMap = allFieldValues.stream()
                    .collect(Collectors.groupingBy(AppUserFieldValue::getUserId));
        } else {
            fieldValueMap = new HashMap<>();
        }
        
        final Map<Long, List<Long>> userTagIdMap;
        if (!userIds.isEmpty()) {
            List<AppUserTagRelation> tagRelations = tagRelationService.lambdaQuery()
                    .in(AppUserTagRelation::getUserId, userIds)
                    .list();
            userTagIdMap = tagRelations.stream()
                    .collect(Collectors.groupingBy(
                            AppUserTagRelation::getUserId,
                            Collectors.mapping(AppUserTagRelation::getTagId, Collectors.toList())
                    ));
        } else {
            userTagIdMap = new HashMap<>();
        }
        
        final Map<Long, AppUserTag> tagMap;
        if (!userTagIdMap.isEmpty()) {
            Set<Long> allTagIds = userTagIdMap.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            tagMap = tagService.listByIds(allTagIds).stream()
                    .collect(Collectors.toMap(AppUserTag::getId, t -> t));
        } else {
            tagMap = new HashMap<>();
        }
        
        Page<AppUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<AppUserVO> voList = userPage.getRecords().stream().map(user -> {
            AppUserVO vo = new AppUserVO();
            vo.setId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setMobile(maskMobile(user.getMobile()));
            vo.setEmail(maskEmail(user.getEmail()));
            vo.setGender(user.getGender());
            vo.setBirthday(user.getBirthday() != null ? user.getBirthday().toString() : null);
            vo.setRegisterSource(user.getRegisterSource());
            vo.setStatus(user.getStatus());
            vo.setRegisterTime(user.getRegisterTime());
            vo.setLastLoginTime(user.getLastLoginTime());
            vo.setLastLoginIp(user.getLastLoginIp());
            
            List<Long> tagIds = userTagIdMap.getOrDefault(user.getId(), new ArrayList<>());
            if (!tagIds.isEmpty()) {
                List<AppUserVO.TagInfo> tagInfos = tagIds.stream()
                        .map(tagMap::get)
                        .filter(java.util.Objects::nonNull)
                        .map(tag -> {
                            AppUserVO.TagInfo tagInfo = new AppUserVO.TagInfo();
                            tagInfo.setId(tag.getId());
                            tagInfo.setName(tag.getName());
                            tagInfo.setColor(tag.getColor());
                            return tagInfo;
                        }).collect(Collectors.toList());
                vo.setTags(tagInfos);
            }
            
            List<AppUserFieldValue> userFieldValues = fieldValueMap.getOrDefault(user.getId(), new ArrayList<>());
            List<AppUserVO.FieldValueInfo> fieldValueInfos = allFields.stream().map(field -> {
                AppUserVO.FieldValueInfo info = new AppUserVO.FieldValueInfo();
                info.setFieldId(field.getId());
                info.setFieldKey(field.getFieldKey());
                info.setFieldName(field.getFieldName());
                info.setFieldType(field.getFieldType());
                
                AppUserFieldValue v = null;
                for (AppUserFieldValue val : userFieldValues) {
                    if (val.getFieldId().equals(field.getId())) {
                        v = val;
                        break;
                    }
                }
                
                if (v != null) {
                    info.setFieldValue(v.getFieldValue());
                    if ("RADIO".equals(field.getFieldType()) || "CHECKBOX".equals(field.getFieldType())) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode configNode = objectMapper.readTree(field.getConfig().toString());
                            com.fasterxml.jackson.databind.JsonNode options = configNode.get("options");
                            if (options != null && options.isArray()) {
                                StringBuilder labels = new StringBuilder();
                                for (com.fasterxml.jackson.databind.JsonNode option : options) {
                                    String label = option.get("label").asText();
                                    String value = option.get("value").asText();
                                    if (v.getFieldValue() != null && v.getFieldValue().contains(value)) {
                                        if (labels.length() > 0) labels.append(",");
                                        labels.append(label);
                                    }
                                }
                                info.setFieldValueLabel(labels.toString());
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
                
                return info;
            }).collect(Collectors.toList());
            vo.setFieldValues(fieldValueInfos);
            
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }

    @Override
    public AppUserVO getUserDetail(Long id) {
        AppUser user = this.getById(id);
        if (user == null) {
            return null;
        }
        
        AppUserVO vo = new AppUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday() != null ? user.getBirthday().toString() : null);
        vo.setRegisterSource(user.getRegisterSource());
        vo.setStatus(user.getStatus());
        vo.setRegisterTime(user.getRegisterTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        
        List<Long> tagIds = tagRelationService.getTagIdsByUserId(id);
        if (!tagIds.isEmpty()) {
            List<AppUserTag> tags = tagService.listByIds(tagIds);
            List<AppUserVO.TagInfo> tagInfos = tags.stream().map(tag -> {
                AppUserVO.TagInfo tagInfo = new AppUserVO.TagInfo();
                tagInfo.setId(tag.getId());
                tagInfo.setName(tag.getName());
                tagInfo.setColor(tag.getColor());
                return tagInfo;
            }).collect(Collectors.toList());
            vo.setTags(tagInfos);
        }
        
        return vo;
    }

    @Override
    public void updateUserStatus(Long id, UserStatusDTO statusDTO) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(statusDTO.getStatus());
        this.updateById(user);
    }

    @Override
    @Transactional
    public void assignUserTags(Long userId, List<Long> tagIds) {
        List<Long> oldTagIds = tagRelationService.getTagIdsByUserId(userId);
        
        tagRelationService.lambdaUpdate().eq(AppUserTagRelation::getUserId, userId).remove();
        
        if (tagIds != null && !tagIds.isEmpty()) {
            List<AppUserTagRelation> relations = tagIds.stream().map(tagId -> {
                AppUserTagRelation relation = new AppUserTagRelation();
                relation.setUserId(userId);
                relation.setTagId(tagId);
                return relation;
            }).collect(Collectors.toList());
            tagRelationService.saveBatch(relations);
        }
        
        Set<Long> allTagIds = new HashSet<>();
        allTagIds.addAll(oldTagIds);
        if (tagIds != null) {
            allTagIds.addAll(tagIds);
        }
        tagService.batchUpdateTagUserCount(allTagIds);
    }

    @Override
    @Transactional
    public void batchAddTags(BatchTagDTO batchTagDTO) {
        if (batchTagDTO.getUserIds() == null || batchTagDTO.getUserIds().isEmpty() || 
            batchTagDTO.getTagIds() == null || batchTagDTO.getTagIds().isEmpty()) {
            return;
        }

        List<AppUserTagRelation> existingRelations = tagRelationService.lambdaQuery()
                .in(AppUserTagRelation::getUserId, batchTagDTO.getUserIds())
                .in(AppUserTagRelation::getTagId, batchTagDTO.getTagIds())
                .list();

        Map<Long, Set<Long>> existingUserTagMap = existingRelations.stream()
                .collect(Collectors.groupingBy(AppUserTagRelation::getUserId,
                        Collectors.mapping(AppUserTagRelation::getTagId, Collectors.toSet())));

        List<AppUserTagRelation> newRelations = new ArrayList<>();
        for (Long userId : batchTagDTO.getUserIds()) {
            Set<Long> existingTagIds = existingUserTagMap.getOrDefault(userId, Collections.emptySet());
            for (Long tagId : batchTagDTO.getTagIds()) {
                if (!existingTagIds.contains(tagId)) {
                    AppUserTagRelation relation = new AppUserTagRelation();
                    relation.setUserId(userId);
                    relation.setTagId(tagId);
                    newRelations.add(relation);
                }
            }
        }

        if (!newRelations.isEmpty()) {
            tagRelationService.saveBatch(newRelations);
            tagService.batchUpdateTagUserCount(batchTagDTO.getTagIds());
        }
    }

    @Override
    @Transactional
    public void batchRemoveTags(BatchTagDTO batchTagDTO) {
        tagRelationService.batchDeleteByUserIdsAndTagIds(batchTagDTO.getUserIds(), batchTagDTO.getTagIds());
        tagService.batchUpdateTagUserCount(batchTagDTO.getTagIds());
    }

    @Override
    public List<UserFieldValueVO> getUserFieldValues(Long userId) {
        List<AppUserField> fields = fieldService.lambdaQuery()
                .eq(AppUserField::getStatus, 1)
                .orderByAsc(AppUserField::getSort)
                .list();
        
        List<AppUserFieldValue> values = fieldValueService.getByUserId(userId);
        
        return fields.stream().map(field -> {
            UserFieldValueVO vo = new UserFieldValueVO();
            vo.setFieldId(field.getId());
            vo.setFieldName(field.getFieldName());
            vo.setFieldKey(field.getFieldKey());
            vo.setFieldType(field.getFieldType());
            
            values.stream()
                    .filter(v -> v.getFieldId().equals(field.getId()))
                    .findFirst()
                    .ifPresent(v -> vo.setFieldValue(v.getFieldValue()));
            
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateUserFieldValues(Long userId, List<UserFieldValueVO> fieldValues) {
        for (UserFieldValueVO fv : fieldValues) {
            String valueStr = fv.getFieldValue() != null ? fv.getFieldValue().toString() : null;
            fieldValueService.saveOrUpdateFieldValue(userId, fv.getFieldId(), valueStr);
        }
    }

    @Override
    public void exportUsers(AppUserQueryDTO queryDTO, HttpServletResponse response) {
        try {
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("用户列表", StandardCharsets.UTF_8.name()) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            LambdaQueryWrapper<AppUser> wrapper = buildQueryWrapper(queryDTO);
            wrapper.orderByDesc(AppUser::getRegisterTime);
            wrapper.last("LIMIT " + MAX_EXPORT_COUNT);

            List<AppUser> users = this.list(wrapper);

            List<ExportUserModel> exportModels = users.stream().map(user -> {
                ExportUserModel model = new ExportUserModel();
                model.setId(user.getId());
                model.setNickname(user.getNickname());
                model.setMobile(maskMobile(user.getMobile()));
                model.setEmail(maskEmail(user.getEmail()));
                model.setGender(user.getGender() == 1 ? "男" : user.getGender() == 2 ? "女" : "未知");
                model.setRegisterSource(user.getRegisterSource());
                model.setStatus(user.getStatus() == 1 ? "正常" : user.getStatus() == 0 ? "禁用" : "注销");
                model.setRegisterTime(user.getRegisterTime() != null ? java.util.Date.from(user.getRegisterTime().atZone(java.time.ZoneId.systemDefault()).toInstant()) : null);
                model.setLastLoginTime(user.getLastLoginTime() != null ? java.util.Date.from(user.getLastLoginTime().atZone(java.time.ZoneId.systemDefault()).toInstant()) : null);
                model.setLastLoginIp(user.getLastLoginIp());

                List<Long> tagIds = tagRelationService.getTagIdsByUserId(user.getId());
                if (!tagIds.isEmpty()) {
                    List<AppUserTag> tags = tagService.listByIds(tagIds);
                    String tagNames = tags.stream().map(AppUserTag::getName).collect(Collectors.joining(", "));
                    model.setTags(tagNames);
                }

                return model;
            }).collect(Collectors.toList());

            try (jakarta.servlet.ServletOutputStream out = response.getOutputStream()) {
                EasyExcel.write(out, ExportUserModel.class)
                        .sheet("用户列表")
                        .doWrite(exportModels);
            }
        } catch (IOException e) {
            log.error("导出用户列表失败", e);
            throw new BusinessException("导出用户列表失败");
        }
    }

    private String maskMobile(String mobile) {
        if (StrUtil.isBlank(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String maskEmail(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        return email.substring(0, 2) + "****" + email.substring(atIndex);
    }

    // ExportUserModel removed and extracted to top-level model

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("用户导入模板", StandardCharsets.UTF_8.name()) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            List<ImportTemplateModel> templateData = new ArrayList<>();
            ImportTemplateModel example = new ImportTemplateModel();
            example.setNickname("张三");
            example.setMobile("13800138000");
            example.setEmail("zhangsan@example.com");
            example.setGender("男");
            example.setBirthday("1990-01-01");
            example.setStatus("正常");
            example.setTags("VIP,活跃用户");
            templateData.add(example);

            try (jakarta.servlet.ServletOutputStream out = response.getOutputStream()) {
                EasyExcel.write(out, ImportTemplateModel.class)
                        .sheet("用户导入模板")
                        .doWrite(templateData);
            }
        } catch (IOException e) {
            log.error("下载导入模板失败", e);
            throw new BusinessException("下载导入模板失败");
        }
    }

    @Override
    public UserImportValidateResultVO validateImportData(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("请选择要导入的文件");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.endsWith(".xlsx")) {
                throw new BusinessException("文件格式错误，仅支持.xlsx格式");
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException("文件过大，最大支持 10MB");
            }

            com.trae.user.listener.HeaderValidationListener headerListener = 
                new com.trae.user.listener.HeaderValidationListener();
            
            EasyExcel.read(file.getInputStream(), UserImportDTO.class, headerListener).sheet().doRead();

            if (!headerListener.isHeaderValid()) {
                throw new BusinessException(headerListener.getHeaderErrorMsg());
            }

            List<UserImportDTO> dataList = headerListener.getDataList();

            if (dataList.isEmpty()) {
                throw new BusinessException("Excel文件中没有数据，请填写数据后再上传");
            }

            if (dataList.size() > MAX_IMPORT_COUNT) {
                throw new BusinessException("数据量超过限制，最多支持" + MAX_IMPORT_COUNT + "条数据");
            }

            Set<String> existingMobiles = getExistingMobiles(dataList);
            Set<String> existingEmails = getExistingEmails(dataList);
            Set<String> existingTagNames = getExistingTagNames();

            int validCount = 0;
            int invalidCount = 0;

            for (UserImportDTO dto : dataList) {
                List<String> errors = new ArrayList<>();

                if (StrUtil.isBlank(dto.getMobile())) {
                    errors.add("手机号不能为空");
                } else if (!MOBILE_PATTERN.matcher(dto.getMobile().trim()).matches()) {
                    errors.add("手机号格式不正确");
                } else if (existingMobiles.contains(dto.getMobile().trim())) {
                    errors.add("手机号已存在");
                }

                if (StrUtil.isNotBlank(dto.getEmail())) {
                    if (!EMAIL_PATTERN.matcher(dto.getEmail().trim()).matches()) {
                        errors.add("邮箱格式不正确");
                    } else if (existingEmails.contains(dto.getEmail().trim().toLowerCase())) {
                        errors.add("邮箱已存在");
                    }
                }

                if (StrUtil.isNotBlank(dto.getNickname()) && dto.getNickname().trim().length() > 20) {
                    errors.add("昵称长度不能超过20字符");
                }

                if (StrUtil.isNotBlank(dto.getGender())) {
                    String gender = dto.getGender().trim();
                    if (!gender.equals("男") && !gender.equals("女") && !gender.equals("未知")) {
                        errors.add("性别值不正确，应为：男/女/未知");
                    }
                }

                if (StrUtil.isNotBlank(dto.getBirthday())) {
                    try {
                        LocalDate.parse(dto.getBirthday().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    } catch (Exception e) {
                        errors.add("生日格式不正确，应为：yyyy-MM-dd");
                    }
                }

                if (StrUtil.isNotBlank(dto.getStatus())) {
                    String status = dto.getStatus().trim();
                    if (!status.equals("正常") && !status.equals("禁用")) {
                        errors.add("状态值不正确，应为：正常/禁用");
                    }
                }

                if (StrUtil.isNotBlank(dto.getTags())) {
                    String[] tags = dto.getTags().split(",");
                    for (String tag : tags) {
                        String trimmedTag = tag.trim();
                        if (!existingTagNames.contains(trimmedTag)) {
                            errors.add("标签[" + trimmedTag + "]不存在");
                        }
                    }
                }

                if (errors.isEmpty()) {
                    dto.setValid(true);
                    dto.setErrorMsg(null);
                    validCount++;
                } else {
                    dto.setValid(false);
                    dto.setErrorMsg(String.join("; ", errors));
                    invalidCount++;
                }
            }

            String taskId = "import_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            cacheService.cacheValidateResult(taskId, dataList);

            UserImportValidateResultVO result = new UserImportValidateResultVO();
            result.setTaskId(taskId);
            result.setTotal(dataList.size());
            result.setValidCount(validCount);
            result.setInvalidCount(invalidCount);
            result.setCanProceed(validCount > 0);

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new BusinessException("解析Excel文件失败，请检查文件格式是否正确");
        }
    }

    @Override
    public void downloadValidateResult(String taskId, HttpServletResponse response) {
        List<UserImportDTO> dataList = cacheService.getValidateResult(taskId);
        if (dataList == null) {
            throw new BusinessException("校验结果已过期，请重新上传文件");
        }

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("校验结果", StandardCharsets.UTF_8.name()) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            List<ValidateResultModel> resultModels = dataList.stream().map(dto -> {
                ValidateResultModel model = new ValidateResultModel();
                model.setNickname(dto.getNickname());
                model.setMobile(dto.getMobile());
                model.setEmail(dto.getEmail());
                model.setGender(dto.getGender());
                model.setBirthday(dto.getBirthday());
                model.setStatus(dto.getStatus());
                model.setTags(dto.getTags());
                model.setErrorMsg(dto.isValid() ? "" : dto.getErrorMsg());
                return model;
            }).collect(Collectors.toList());

            EasyExcel.write(response.getOutputStream(), ValidateResultModel.class)
                    .sheet("校验结果")
                    .doWrite(resultModels);
        } catch (IOException e) {
            log.error("下载校验结果失败", e);
            throw new BusinessException("下载校验结果失败");
        }
    }

    @Override
    public String executeImport(String taskId) {
        List<UserImportDTO> dataList = cacheService.getValidateResult(taskId);
        if (dataList == null) {
            throw new BusinessException("校验结果已过期，请重新上传文件");
        }

        List<UserImportDTO> validDataList = dataList.stream()
                .filter(UserImportDTO::isValid)
                .collect(Collectors.toList());

        if (validDataList.isEmpty()) {
            throw new BusinessException("没有有效数据可导入");
        }

        String importTaskId = "import_exec_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        UserImportProgressVO progress = new UserImportProgressVO();
        progress.setStatus(UserImportProgressVO.STATUS_PENDING);
        progress.setTotal(validDataList.size());
        progress.setProcessed(0);
        progress.setSuccess(0);
        progress.setFailed(0);
        progress.setProgress(0);
        progress.setErrors(new ArrayList<>());
        cacheService.cacheImportProgress(importTaskId, progress);

        importTaskExecutor.executeImport(taskId, importTaskId, validDataList);

        return importTaskId;
    }

    @Override
    public UserImportProgressVO getImportProgress(String importTaskId) {
        UserImportProgressVO progress = cacheService.getImportProgress(importTaskId);
        if (progress == null) {
            throw new BusinessException("导入任务不存在或已过期");
        }
        return progress;
    }

    private Set<String> getExistingMobiles(List<UserImportDTO> dataList) {
        List<String> mobiles = dataList.stream()
                .filter(dto -> StrUtil.isNotBlank(dto.getMobile()))
                .map(dto -> dto.getMobile().trim())
                .collect(Collectors.toList());

        if (mobiles.isEmpty()) {
            return Collections.emptySet();
        }

        List<AppUser> existingUsers = this.lambdaQuery()
                .in(AppUser::getMobile, mobiles)
                .list();

        Set<String> result = new HashSet<>();
        result.addAll(existingUsers.stream().map(AppUser::getMobile).collect(Collectors.toSet()));

        Map<String, Long> mobileCount = mobiles.stream()
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()));
        for (Map.Entry<String, Long> entry : mobileCount.entrySet()) {
            if (entry.getValue() > 1) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    private Set<String> getExistingEmails(List<UserImportDTO> dataList) {
        List<String> emails = dataList.stream()
                .filter(dto -> StrUtil.isNotBlank(dto.getEmail()))
                .map(dto -> dto.getEmail().trim().toLowerCase())
                .collect(Collectors.toList());

        if (emails.isEmpty()) {
            return Collections.emptySet();
        }

        List<AppUser> existingUsers = this.lambdaQuery()
                .in(AppUser::getEmail, emails)
                .isNotNull(AppUser::getEmail)
                .list();

        Set<String> result = new HashSet<>();
        result.addAll(existingUsers.stream()
                .map(u -> u.getEmail().toLowerCase())
                .collect(Collectors.toSet()));

        Map<String, Long> emailCount = emails.stream()
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        for (Map.Entry<String, Long> entry : emailCount.entrySet()) {
            if (entry.getValue() > 1) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    private Set<String> getExistingTagNames() {
        List<AppUserTag> tags = tagService.list();
        return tags.stream()
                .map(tag -> tag.getName().trim())
                .collect(Collectors.toSet());
    }

    // ImportTemplateModel and ValidateResultModel removed and extracted to top-level models
}
