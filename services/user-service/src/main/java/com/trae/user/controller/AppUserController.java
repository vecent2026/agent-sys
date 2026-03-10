package com.trae.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.common.result.Result;
import com.trae.user.dto.AppUserQueryDTO;
import com.trae.user.dto.BatchTagDTO;
import com.trae.user.dto.UserFieldValuesDTO;
import com.trae.user.dto.UserStatusDTO;
import com.trae.user.service.AppUserService;
import com.trae.user.vo.AppUserVO;
import com.trae.user.vo.UserFieldValueVO;
import com.trae.user.vo.UserImportProgressVO;
import com.trae.user.vo.UserImportValidateResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "用户中心")
@RestController
@RequestMapping("/api/v1/app-users")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService userService;

    @PreAuthorize("hasAuthority('app:user:list')")
    @Operation(summary = "获取用户列表")
    @GetMapping
    public Result<Page<AppUserVO>> getUserList(AppUserQueryDTO queryDTO) {
        return Result.success(userService.getUserPage(queryDTO));
    }

    @PreAuthorize("hasAuthority('app:user:view')")
    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<AppUserVO> getUserDetail(@PathVariable Long id) {
        return Result.success(userService.getUserDetail(id));
    }

    @PreAuthorize("hasAuthority('app:user:status')")
    @Operation(summary = "更新用户状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody @Valid UserStatusDTO statusDTO) {
        userService.updateUserStatus(id, statusDTO);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('app:user:tag')")
    @Operation(summary = "为用户分配标签")
    @PostMapping("/{id}/tags")
    public Result<Void> assignUserTags(@PathVariable Long id, @RequestBody List<Long> tagIds) {
        userService.assignUserTags(id, tagIds);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('app:user:tag')")
    @Operation(summary = "批量打标签")
    @PostMapping("/batch-tags")
    public Result<Void> batchAddTags(@RequestBody @Valid BatchTagDTO batchTagDTO) {
        userService.batchAddTags(batchTagDTO);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('app:user:tag')")
    @Operation(summary = "批量移除标签")
    @DeleteMapping("/batch-tags")
    public Result<Void> batchRemoveTags(@RequestBody @Valid BatchTagDTO batchTagDTO) {
        userService.batchRemoveTags(batchTagDTO);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('app:user:export')")
    @Operation(summary = "导出用户列表")
    @PostMapping("/export")
    public void exportUsers(@RequestBody AppUserQueryDTO queryDTO, HttpServletResponse response) {
        userService.exportUsers(queryDTO, response);
    }

    @PreAuthorize("hasAuthority('app:user:view')")
    @Operation(summary = "获取用户扩展字段值")
    @GetMapping("/{userId}/field-values")
    public Result<List<UserFieldValueVO>> getUserFieldValues(@PathVariable Long userId) {
        return Result.success(userService.getUserFieldValues(userId));
    }

    @PreAuthorize("hasAuthority('app:user:edit')")
    @Operation(summary = "更新用户扩展字段值")
    @PutMapping("/{userId}/field-values")
    public Result<Void> updateUserFieldValues(@PathVariable Long userId, @RequestBody @Valid UserFieldValuesDTO fieldValuesDTO) {
        List<UserFieldValueVO> fieldValueVOs = fieldValuesDTO.getFieldValues().stream().map(item -> {
            UserFieldValueVO vo = new UserFieldValueVO();
            vo.setFieldId(item.getFieldId());
            vo.setFieldValue(item.getFieldValue());
            return vo;
        }).collect(Collectors.toList());
        userService.updateUserFieldValues(userId, fieldValueVOs);
        return Result.success();
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/import-template")
    public void downloadImportTemplate(HttpServletResponse response) {
        userService.downloadImportTemplate(response);
    }

    @Operation(summary = "校验导入数据")
    @PostMapping("/import-validate")
    public Result<UserImportValidateResultVO> validateImportData(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.validateImportData(file));
    }

    @PreAuthorize("hasAuthority('app:user:import')")
    @Operation(summary = "下载校验结果")
    @GetMapping("/import-validate-result/{taskId}")
    public void downloadValidateResult(@PathVariable String taskId, HttpServletResponse response) {
        userService.downloadValidateResult(taskId, response);
    }

    @PreAuthorize("hasAuthority('app:user:import')")
    @Operation(summary = "执行导入")
    @PostMapping("/import-execute")
    public Result<String> executeImport(@RequestBody java.util.Map<String, String> request) {
        String taskId = request.get("taskId");
        return Result.success(userService.executeImport(taskId));
    }

    @PreAuthorize("hasAuthority('app:user:import')")
    @Operation(summary = "查询导入进度")
    @GetMapping("/import-progress/{importTaskId}")
    public Result<UserImportProgressVO> getImportProgress(@PathVariable String importTaskId) {
        return Result.success(userService.getImportProgress(importTaskId));
    }
}
