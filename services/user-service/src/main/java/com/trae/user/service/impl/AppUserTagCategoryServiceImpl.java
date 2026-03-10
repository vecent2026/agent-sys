package com.trae.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.dto.AppUserTagCategoryDTO;
import com.trae.user.entity.AppUserTag;
import com.trae.user.entity.AppUserTagCategory;
import com.trae.user.mapper.AppUserTagCategoryMapper;
import com.trae.user.mapper.AppUserTagMapper;
import com.trae.user.service.AppUserTagCategoryService;
import com.trae.user.vo.AppUserTagCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppUserTagCategoryServiceImpl extends ServiceImpl<AppUserTagCategoryMapper, AppUserTagCategory> implements AppUserTagCategoryService {

    private final AppUserTagMapper tagMapper;

    @Override
    public List<AppUserTagCategoryVO> getCategoryList() {
        List<AppUserTagCategory> categories = this.lambdaQuery()
                .orderByAsc(AppUserTagCategory::getSort)
                .list();

        // 统计每个分类下的标签数量
        List<Long> categoryIds = categories.stream()
                .map(AppUserTagCategory::getId)
                .collect(Collectors.toList());

        final Map<Long, Long> tagCountMap;
        if (!categoryIds.isEmpty()) {
            tagCountMap = tagMapper.selectList(
                            new LambdaQueryWrapper<AppUserTag>()
                                    .in(AppUserTag::getCategoryId, categoryIds)
                    ).stream()
                    .collect(Collectors.groupingBy(AppUserTag::getCategoryId, Collectors.counting()));
        } else {
            tagCountMap = Map.of();
        }

        return categories.stream()
                .map(category -> {
                    AppUserTagCategoryVO vo = new AppUserTagCategoryVO();
                    BeanUtils.copyProperties(category, vo);
                    vo.setTagCount(tagCountMap.getOrDefault(category.getId(), 0L).intValue());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void createCategory(AppUserTagCategoryDTO categoryDTO) {
        AppUserTagCategory category = new AppUserTagCategory();
        BeanUtils.copyProperties(categoryDTO, category);
        // 默认颜色为蓝色
        if (category.getColor() == null) {
            category.setColor("blue");
        }
        this.save(category);
    }

    @Override
    public void updateCategory(Long id, AppUserTagCategoryDTO categoryDTO) {
        AppUserTagCategory category = new AppUserTagCategory();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setId(id);
        this.updateById(category);
        
        // 当分类颜色更新时，同步更新该分类下所有标签的颜色
        if (categoryDTO.getColor() != null) {
            tagMapper.update(null, new LambdaUpdateWrapper<AppUserTag>()
                    .eq(AppUserTag::getCategoryId, id)
                    .set(AppUserTag::getColor, categoryDTO.getColor()));
        }
    }

    @Override
    public void deleteCategory(Long id) {
        // 检查是否有标签关联
        long tagCount = tagMapper.selectCount(new LambdaQueryWrapper<AppUserTag>()
                .eq(AppUserTag::getCategoryId, id));
        if (tagCount > 0) {
            throw new RuntimeException("该分类下存在标签，无法删除");
        }
        this.removeById(id);
    }
}
