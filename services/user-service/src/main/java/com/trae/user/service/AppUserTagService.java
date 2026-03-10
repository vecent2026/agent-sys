package com.trae.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.dto.AppUserTagDTO;
import com.trae.user.entity.AppUserTag;
import com.trae.user.vo.AppUserTagVO;
import com.trae.user.vo.AppUserVO;

import java.util.Collection;

public interface AppUserTagService extends IService<AppUserTag> {
    Page<AppUserTagVO> getTagPage(Integer page, Integer size, String name, Long categoryId, Integer status);
    void createTag(AppUserTagDTO tagDTO);
    void updateTag(Long id, AppUserTagDTO tagDTO);
    void deleteTag(Long id);
    void updateTagStatus(Long id, Integer status);
    Page<AppUserVO> getTagUsers(Long tagId, Integer page, Integer size);
    void updateTagUserCount(Long tagId);
    void batchUpdateTagUserCount(Collection<Long> tagIds);
}
