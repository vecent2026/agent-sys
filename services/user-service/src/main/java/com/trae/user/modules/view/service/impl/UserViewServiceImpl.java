package com.trae.user.modules.view.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trae.user.modules.view.mapper.UserViewMapper;
import com.trae.user.modules.view.dto.CreateViewDTO;
import com.trae.user.modules.view.dto.UpdateViewDTO;
import com.trae.user.modules.view.dto.ViewConfigDTO;
import com.trae.user.modules.view.dto.ViewConfig;
import com.trae.user.modules.view.entity.UserView;
import com.trae.user.modules.view.service.UserViewService;
import com.trae.user.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserViewServiceImpl extends ServiceImpl<UserViewMapper, UserView> implements UserViewService {
    private final UserViewMapper userViewMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ViewConfigDTO> getViewsByUserId(Long userId) {
        List<UserView> userViews = userViewMapper.selectByUserId(userId);
        return userViews.stream().map(this::convertToDTO).toList();
    }

    @Override
    @Transactional
    public ViewConfigDTO createView(Long userId, CreateViewDTO createViewDTO) {
        // 数量上限校验：每个用户最多创建 20 个视图
        long count = lambdaQuery().eq(UserView::getUserId, userId).count();
        if (count >= 20) {
            throw new BusinessException("视图数量已达上限，最多创建 20 个视图");
        }
        UserView userView = new UserView();
        userView.setId(UUID.randomUUID().toString());
        userView.setName(createViewDTO.getName());
        userView.setUserId(userId);
        try {
            userView.setFilters(objectMapper.writeValueAsString(createViewDTO.getFilters() != null ? createViewDTO.getFilters() : List.of()));
            userView.setHiddenFields(objectMapper.writeValueAsString(createViewDTO.getHiddenFields() != null ? createViewDTO.getHiddenFields() : List.of()));
            if (createViewDTO.getViewConfig() != null) {
                userView.setViewConfig(objectMapper.writeValueAsString(createViewDTO.getViewConfig()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize view data", e);
        }
        userView.setFilterLogic(createViewDTO.getFilterLogic() != null ? createViewDTO.getFilterLogic() : "AND");
        userView.setIsDefault(false);
        // 处理序号：如果前端传了 orderNo，用前端的；否则自动取当前用户最大序号 + 1
        Integer orderNo = createViewDTO.getOrderNo();
        if (orderNo == null || orderNo <= 0) {
            Integer maxOrder = lambdaQuery()
                    .eq(UserView::getUserId, userId)
                    .orderByDesc(UserView::getOrderNo)
                    .last("LIMIT 1")
                    .oneOpt()
                    .map(UserView::getOrderNo)
                    .orElse(0);
            orderNo = maxOrder + 1;
        }
        userView.setOrderNo(orderNo);
        save(userView);
        return convertToDTO(userView);
    }


    @Override
    @Transactional
    public ViewConfigDTO updateView(String id, Long userId, UpdateViewDTO updateViewDTO) {
        UserView userView = getById(id);
        if (userView == null || !userView.getUserId().equals(userId)) {
            throw new IllegalArgumentException("视图不存在或无权限操作");
        }

        // 默认视图保护：不允许修改默认视图的名称
        if (Boolean.TRUE.equals(userView.getIsDefault())
                && updateViewDTO.getName() != null
                && !updateViewDTO.getName().equals(userView.getName())) {
            throw new BusinessException("默认视图名称不允许修改");
        }

        if (updateViewDTO.getName() != null) {
            userView.setName(updateViewDTO.getName());
        }
        try {
            if (updateViewDTO.getFilters() != null) {
                userView.setFilters(objectMapper.writeValueAsString(updateViewDTO.getFilters()));
            }
            if (updateViewDTO.getHiddenFields() != null) {
                userView.setHiddenFields(objectMapper.writeValueAsString(updateViewDTO.getHiddenFields()));
            }
            if (updateViewDTO.getViewConfig() != null) {
                userView.setViewConfig(objectMapper.writeValueAsString(updateViewDTO.getViewConfig()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize view data", e);
        }
        if (updateViewDTO.getFilterLogic() != null) {
            userView.setFilterLogic(updateViewDTO.getFilterLogic());
        }
        if (updateViewDTO.getOrderNo() != null && updateViewDTO.getOrderNo() > 0) {
            userView.setOrderNo(updateViewDTO.getOrderNo());
        }

        // 默认视图保护 & 约束：同一用户最多只有一个默认视图
        if (updateViewDTO.getIsDefault() != null) {
            boolean toDefault = Boolean.TRUE.equals(updateViewDTO.getIsDefault());
            if (toDefault) {
                // 将当前用户其他视图的默认标记清除
                lambdaUpdate()
                        .eq(UserView::getUserId, userId)
                        .ne(UserView::getId, id)
                        .set(UserView::getIsDefault, false)
                        .update();
                userView.setIsDefault(true);
            } else {
                userView.setIsDefault(false);
            }
        }
        
        updateById(userView);
        return convertToDTO(userView);
    }

    @Override
    @Transactional
    public void deleteView(String id, Long userId) {
        UserView userView = getById(id);
        if (userView == null || !userView.getUserId().equals(userId)) {
            throw new IllegalArgumentException("视图不存在或无权限操作");
        }
        // 默认视图保护：不允许删除默认视图
        if (Boolean.TRUE.equals(userView.getIsDefault())) {
            throw new BusinessException("默认视图不允许删除");
        }
        removeById(id);
    }

    private ViewConfigDTO convertToDTO(UserView userView) {
        ViewConfigDTO dto = new ViewConfigDTO();
        dto.setId(userView.getId());
        dto.setName(userView.getName());
        try {
            dto.setFilters(userView.getFilters() == null || userView.getFilters().isBlank()
                    ? List.of()
                    : objectMapper.readValue(userView.getFilters(), List.class));
            dto.setHiddenFields(userView.getHiddenFields() == null || userView.getHiddenFields().isBlank()
                    ? List.of()
                    : objectMapper.readValue(userView.getHiddenFields(), List.class));
            if (userView.getViewConfig() != null && !userView.getViewConfig().isBlank()) {
                dto.setViewConfig(objectMapper.readValue(userView.getViewConfig(), ViewConfig.class));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize view data", e);
        }
        dto.setFilterLogic(userView.getFilterLogic());
        dto.setIsDefault(userView.getIsDefault());
        dto.setCreateTime(userView.getCreatedAt());
        dto.setUpdateTime(userView.getUpdatedAt());
        dto.setOrderNo(userView.getOrderNo());
        return dto;
    }
}
