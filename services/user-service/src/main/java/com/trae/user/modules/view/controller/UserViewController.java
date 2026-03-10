package com.trae.user.modules.view.controller;

import com.trae.user.common.result.Result;
import com.trae.user.modules.view.dto.CreateViewDTO;
import com.trae.user.modules.view.dto.UpdateViewDTO;
import com.trae.user.modules.view.dto.ViewConfigDTO;
import com.trae.user.modules.view.service.UserViewService;
import com.trae.user.common.exception.UnauthenticatedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户视图管理", description = "用户自定义视图的增删改查")
@RestController
@RequestMapping("/api/user/views")
@RequiredArgsConstructor
public class UserViewController {
    private final UserViewService userViewService;

    @Operation(summary = "获取当前用户所有视图")
    @GetMapping
    public Result<List<ViewConfigDTO>> getViews(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        List<ViewConfigDTO> views = userViewService.getViewsByUserId(userId);
        return Result.success(views);
    }

    @Operation(summary = "创建自定义视图")
    @PostMapping
    public Result<ViewConfigDTO> createView(@RequestBody CreateViewDTO createViewDTO, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        ViewConfigDTO view = userViewService.createView(userId, createViewDTO);
        return Result.success(view);
    }

    @Operation(summary = "更新自定义视图")
    @PutMapping("/{id}")
    public Result<ViewConfigDTO> updateView(@PathVariable String id, @RequestBody UpdateViewDTO updateViewDTO, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        ViewConfigDTO view = userViewService.updateView(id, userId, updateViewDTO);
        return Result.success(view);
    }

    @Operation(summary = "删除自定义视图")
    @DeleteMapping("/{id}")
    public Result<Void> deleteView(@PathVariable String id, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        userViewService.deleteView(id, userId);
        return Result.success();
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthenticatedException("未登录");
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            throw new UnauthenticatedException("请重新登录以使用视图功能");
        }
        try {
            return Long.parseLong(name.trim());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("请重新登录以使用视图功能（当前 token 缺少用户信息）");
        }
    }
}
