package com.trae.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.common.result.Result;
import com.trae.user.dto.AppUserTagCategoryDTO;
import com.trae.user.dto.AppUserTagDTO;
import com.trae.user.service.AppUserTagCategoryService;
import com.trae.user.service.AppUserTagService;
import com.trae.user.vo.AppUserTagCategoryVO;
import com.trae.user.vo.AppUserTagVO;
import com.trae.user.vo.AppUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户标签管理")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppUserTagController {

    private final AppUserTagService tagService;
    private final AppUserTagCategoryService categoryService;

    @Operation(summary = "获取标签分类列表")
    @GetMapping("/tag-categories")
    public Result<List<AppUserTagCategoryVO>> getCategoryList() {
        return Result.success(categoryService.getCategoryList());
    }

    @Operation(summary = "创建标签分类")
    @PostMapping("/tag-categories")
    public Result<Void> createCategory(@RequestBody @Valid AppUserTagCategoryDTO categoryDTO) {
        categoryService.createCategory(categoryDTO);
        return Result.success();
    }

    @Operation(summary = "更新标签分类")
    @PutMapping("/tag-categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody @Valid AppUserTagCategoryDTO categoryDTO) {
        categoryService.updateCategory(id, categoryDTO);
        return Result.success();
    }

    @Operation(summary = "删除标签分类")
    @DeleteMapping("/tag-categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @Operation(summary = "获取标签列表")
    @GetMapping("/user-tags")
    public Result<Page<AppUserTagVO>> getTagList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return Result.success(tagService.getTagPage(page, size, name, categoryId, status));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/user-tags")
    public Result<Void> createTag(@RequestBody @Valid AppUserTagDTO tagDTO) {
        tagService.createTag(tagDTO);
        return Result.success();
    }

    @Operation(summary = "更新标签")
    @PutMapping("/user-tags/{id}")
    public Result<Void> updateTag(@PathVariable Long id, @RequestBody @Valid AppUserTagDTO tagDTO) {
        tagService.updateTag(id, tagDTO);
        return Result.success();
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/user-tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }

    @Operation(summary = "更新标签状态")
    @PutMapping("/user-tags/{id}/status")
    public Result<Void> updateTagStatus(@PathVariable Long id, @RequestParam Integer status) {
        tagService.updateTagStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "获取标签下的用户列表")
    @GetMapping("/user-tags/{id}/users")
    public Result<Page<AppUserVO>> getTagUsers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(tagService.getTagUsers(id, page, size));
    }
}
