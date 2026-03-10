package com.trae.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.dto.AppUserTagDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.AppUserTag;
import com.trae.user.entity.AppUserTagCategory;
import com.trae.user.entity.AppUserTagRelation;
import com.trae.user.mapper.AppUserMapper;
import com.trae.user.mapper.AppUserTagMapper;
import com.trae.user.service.AppUserTagCategoryService;
import com.trae.user.service.AppUserTagRelationService;
import com.trae.user.service.AppUserTagService;
import com.trae.user.vo.AppUserTagVO;
import com.trae.user.vo.AppUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppUserTagServiceImpl extends ServiceImpl<AppUserTagMapper, AppUserTag> implements AppUserTagService {

    private final AppUserTagCategoryService categoryService;
    private final AppUserTagRelationService tagRelationService;
    private final AppUserMapper appUserMapper;

    @Override
    public Page<AppUserTagVO> getTagPage(Integer page, Integer size, String name, Long categoryId, Integer status) {
        Page<AppUserTag> tagPage = new Page<>(page, size);
        LambdaQueryWrapper<AppUserTag> wrapper = new LambdaQueryWrapper<>();
        
        if (StrUtil.isNotBlank(name)) {
            wrapper.like(AppUserTag::getName, name);
        }
        if (categoryId != null) {
            wrapper.eq(AppUserTag::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(AppUserTag::getStatus, status);
        }
        
        wrapper.orderByDesc(AppUserTag::getUserCount);
        
        Page<AppUserTag> result = this.page(tagPage, wrapper);
        
        // 获取分类信息，包括名称和颜色
        List<AppUserTagCategory> categories = categoryService.list();
        Map<Long, AppUserTagCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(AppUserTagCategory::getId, c -> c));
        
        Page<AppUserTagVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<AppUserTagVO> voList = result.getRecords().stream().map(tag -> {
            AppUserTagVO vo = new AppUserTagVO();
            BeanUtils.copyProperties(tag, vo);
            AppUserTagCategory category = categoryMap.get(tag.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
                // 使用分类的颜色
                vo.setColor(category.getColor());
            }
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }

    @Override
    public void createTag(AppUserTagDTO tagDTO) {
        AppUserTag tag = new AppUserTag();
        BeanUtils.copyProperties(tagDTO, tag);
        // 从分类获取颜色
        AppUserTagCategory category = categoryService.getById(tagDTO.getCategoryId());
        if (category != null && category.getColor() != null) {
            tag.setColor(category.getColor());
        } else {
            tag.setColor("blue");
        }
        tag.setUserCount(0);
        this.save(tag);
    }

    @Override
    public void updateTag(Long id, AppUserTagDTO tagDTO) {
        AppUserTag tag = this.getById(id);
        if (tag != null) {
            tag.setCategoryId(tagDTO.getCategoryId());
            tag.setName(tagDTO.getName());
            tag.setDescription(tagDTO.getDescription());
            // 从分类获取颜色
            AppUserTagCategory category = categoryService.getById(tagDTO.getCategoryId());
            if (category != null && category.getColor() != null) {
                tag.setColor(category.getColor());
            }
            this.updateById(tag);
        }
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        tagRelationService.lambdaUpdate()
                .eq(AppUserTagRelation::getTagId, id)
                .remove();
        this.removeById(id);
    }

    @Override
    public void updateTagUserCount(Long tagId) {
        long count = tagRelationService.lambdaQuery()
                .eq(AppUserTagRelation::getTagId, tagId)
                .count();
        AppUserTag tag = this.getById(tagId);
        if (tag != null) {
            tag.setUserCount((int) count);
            this.updateById(tag);
        }
    }

    @Override
    @Transactional
    public void batchUpdateTagUserCount(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        
        Set<Long> uniqueTagIds = tagIds.stream().collect(Collectors.toSet());
        
        List<AppUserTagRelation> relations = tagRelationService.lambdaQuery()
                .in(AppUserTagRelation::getTagId, uniqueTagIds)
                .list();
        
        Map<Long, Long> countMap = relations.stream()
                .collect(Collectors.groupingBy(AppUserTagRelation::getTagId, Collectors.counting()));
        
        for (Long tagId : uniqueTagIds) {
            AppUserTag tag = this.getById(tagId);
            if (tag != null) {
                tag.setUserCount(countMap.getOrDefault(tagId, 0L).intValue());
                this.updateById(tag);
            }
        }
    }

    @Override
    public void updateTagStatus(Long id, Integer status) {
        AppUserTag tag = new AppUserTag();
        tag.setId(id);
        tag.setStatus(status);
        this.updateById(tag);
    }

    @Override
    public Page<AppUserVO> getTagUsers(Long tagId, Integer page, Integer size) {
        List<Long> userIds = tagRelationService.lambdaQuery()
                .eq(AppUserTagRelation::getTagId, tagId)
                .list()
                .stream()
                .map(AppUserTagRelation::getUserId)
                .collect(Collectors.toList());
        
        Page<AppUserVO> voPage = new Page<>(page, size, userIds.size());
        
        if (!userIds.isEmpty()) {
            // 分页处理
            int start = (page - 1) * size;
            int end = Math.min(start + size, userIds.size());
            List<Long> pageUserIds = userIds.subList(start, end);
            
            // 查询用户信息
            List<AppUser> users = appUserMapper.selectBatchIds(pageUserIds);
            
            // 转换为VO
            List<AppUserVO> voList = users.stream().map(user -> {
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
                return vo;
            }).collect(Collectors.toList());
            
            voPage.setRecords(voList);
        }
        
        return voPage;
    }
}
