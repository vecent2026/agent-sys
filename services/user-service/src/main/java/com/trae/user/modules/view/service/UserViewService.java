package com.trae.user.modules.view.service;

import com.trae.user.modules.view.dto.CreateViewDTO;
import com.trae.user.modules.view.dto.UpdateViewDTO;
import com.trae.user.modules.view.dto.ViewConfigDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.modules.view.entity.UserView;

import java.util.List;

public interface UserViewService extends IService<UserView> {
    List<ViewConfigDTO> getViewsByUserId(Long userId);
    ViewConfigDTO createView(Long userId, CreateViewDTO createViewDTO);
    ViewConfigDTO updateView(String id, Long userId, UpdateViewDTO updateViewDTO);
    void deleteView(String id, Long userId);
}
