package com.trae.admin.modules.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.platform.dto.TenantDto;
import com.trae.admin.modules.platform.dto.TenantQueryDto;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.TenantVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "租户管理", description = "平台级租户信息管理")
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService tenantService;

    @Operation(summary = "分页查询租户")
    @GetMapping
    @PreAuthorize("hasAuthority('platform:tenant:list')")
    public Result<Page<TenantVo>> page(TenantQueryDto query) {
        return Result.success(tenantService.page(query));
    }

    @Operation(summary = "获取全部租户（下拉用）")
    @GetMapping("/all")
    public Result<List<TenantVo>> listAll() {
        return Result.success(tenantService.listAll());
    }

    @Operation(summary = "租户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:query')")
    public Result<TenantVo> get(@PathVariable Long id) {
        return Result.success(tenantService.get(id));
    }

    @Operation(summary = "新增租户")
    @PostMapping
    @PreAuthorize("hasAuthority('platform:tenant:add')")
    @Log(module = "租户管理", action = "新增租户")
    public Result<Void> save(@Valid @RequestBody TenantDto dto) {
        tenantService.save(dto);
        return Result.success();
    }

    @Operation(summary = "修改租户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "修改租户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TenantDto dto) {
        dto.setId(id);
        tenantService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:remove')")
    @Log(module = "租户管理", action = "删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.success();
    }

    @Operation(summary = "修改租户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "修改状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        tenantService.changeStatus(id, Integer.valueOf(params.get("status").toString()));
        return Result.success();
    }
}
