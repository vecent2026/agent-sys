package com.trae.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.entity.AppUserTagRelation;
import com.trae.user.mapper.AppUserTagRelationMapper;
import com.trae.user.service.AppUserTagRelationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppUserTagRelationServiceImpl extends ServiceImpl<AppUserTagRelationMapper, AppUserTagRelation> implements AppUserTagRelationService {

    @Override
    public void batchInsert(List<AppUserTagRelation> relations) {
        this.saveBatch(relations);
    }

    @Override
    public void batchDeleteByUserIdsAndTagIds(List<Long> userIds, List<Long> tagIds) {
        this.lambdaUpdate()
                .in(AppUserTagRelation::getUserId, userIds)
                .in(AppUserTagRelation::getTagId, tagIds)
                .remove();
    }

    @Override
    public List<Long> getTagIdsByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(AppUserTagRelation::getUserId, userId)
                .list()
                .stream()
                .map(AppUserTagRelation::getTagId)
                .collect(Collectors.toList());
    }
}
