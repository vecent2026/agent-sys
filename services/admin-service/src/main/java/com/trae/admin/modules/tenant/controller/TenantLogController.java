package com.trae.admin.modules.tenant.controller;

import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.log.dto.LogQueryDto;
import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.log.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户操作日志", description = "租户端操作日志查询")
@RestController
@RequestMapping("/api/tenant/logs")
@RequiredArgsConstructor
public class TenantLogController {

    private final LogService logService;

    @Operation(summary = "分页查询当前租户操作日志")
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:log:list')")
    public Result<Page<SysLogDocument>> page(LogQueryDto queryDto) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("租户上下文缺失");
        }
        queryDto.setIsPlatform(false);
        queryDto.setTenantId(tenantId);
        return Result.success(logService.page(queryDto));
    }
}
