package com.trae.admin.modules.platform.controller;

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

@Tag(name = "平台操作日志", description = "平台端操作日志查询")
@RestController
@RequestMapping("/api/platform/logs")
@RequiredArgsConstructor
public class PlatformLogController {

    private final LogService logService;

    @Operation(summary = "分页查询操作日志")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:log:list')")
    public Result<Page<SysLogDocument>> page(LogQueryDto queryDto) {
        return Result.success(logService.page(queryDto));
    }
}
