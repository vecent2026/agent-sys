package com.trae.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.common.result.Result;
import com.trae.user.dto.AppUserFieldDTO;
import com.trae.user.dto.FieldIdsDTO;
import com.trae.user.service.AppUserFieldService;
import com.trae.user.vo.AppUserFieldVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户字段管理")
@RestController
@RequestMapping("/api/v1/user-fields")
@RequiredArgsConstructor
public class AppUserFieldController {

    private final AppUserFieldService fieldService;

    @Operation(summary = "获取字段列表")
    @GetMapping
    public Result<Page<AppUserFieldVO>> getFieldList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status) {
        return Result.success(fieldService.getFieldPage(page, size, name, type, status));
    }

    @Operation(summary = "获取所有启用的字段")
    @GetMapping("/enabled")
    public Result<List<AppUserFieldVO>> getEnabledFields() {
        return Result.success(fieldService.getEnabledFields());
    }

    @Operation(summary = "创建字段")
    @PostMapping
    public Result<Void> createField(@RequestBody @Valid AppUserFieldDTO fieldDTO) {
        fieldService.createField(fieldDTO);
        return Result.success();
    }

    @Operation(summary = "更新字段")
    @PutMapping("/{id}")
    public Result<Void> updateField(@PathVariable Long id, @RequestBody @Valid AppUserFieldDTO fieldDTO) {
        fieldService.updateField(id, fieldDTO);
        return Result.success();
    }

    @Operation(summary = "删除字段")
    @DeleteMapping("/{id}")
    public Result<Void> deleteField(@PathVariable Long id) {
        fieldService.deleteField(id);
        return Result.success();
    }

    @Operation(summary = "更新字段状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateFieldStatus(@PathVariable Long id, @RequestParam Integer status) {
        fieldService.updateFieldStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "字段排序")
    @PutMapping("/sort")
    public Result<Void> sortFields(@RequestBody FieldIdsDTO fieldIdsDTO) {
        fieldService.sortFields(fieldIdsDTO.getFieldIds());
        return Result.success();
    }
}
