package com.trae.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.entity.AppUserTagRelation;

import java.util.List;

public interface AppUserTagRelationService extends IService<AppUserTagRelation> {
    void batchInsert(List<AppUserTagRelation> relations);
    void batchDeleteByUserIdsAndTagIds(List<Long> userIds, List<Long> tagIds);
    List<Long> getTagIdsByUserId(Long userId);
}
