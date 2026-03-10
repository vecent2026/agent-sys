package com.trae.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.dto.AppUserTagCategoryDTO;
import com.trae.user.entity.AppUserTagCategory;
import com.trae.user.vo.AppUserTagCategoryVO;

import java.util.List;

public interface AppUserTagCategoryService extends IService<AppUserTagCategory> {
    List<AppUserTagCategoryVO> getCategoryList();
    void createCategory(AppUserTagCategoryDTO categoryDTO);
    void updateCategory(Long id, AppUserTagCategoryDTO categoryDTO);
    void deleteCategory(Long id);
}
