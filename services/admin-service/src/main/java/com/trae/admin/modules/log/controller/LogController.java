package com.trae.admin.modules.log.controller;

import com.trae.admin.common.result.Result;
import com.trae.admin.modules.log.dto.LogQueryDto;
import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.log.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志管理控制器
 */
@Tag(name = "日志模块", description = "操作日志查询、记录与清理")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(
            summary = "分页查询操作日志",
            description = "根据条件分页查询操作日志，支持用户ID、模块、动作筛选",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping
    @PreAuthorize("hasAuthority('platform:log:list')")
    public Result<Page<SysLogDocument>> page(LogQueryDto queryDto) {
        return Result.success(logService.page(queryDto));
    }
}
