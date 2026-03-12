package com.trae.user.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.dto.UnifiedFilterCondition;
import com.trae.user.dto.UnifiedFilterQuery;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 统一筛选引擎
 *
 * 负责筛选条件的验证、SQL 构建与分页查询执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedFilterEngine {

    private final FieldFilterRegistry filterRegistry;
    private final AppUserService userService;

    // ==================== 对外接口 ====================

    /**
     * 执行统一筛选查询
     */
    public FilterQueryResult executeFilter(UnifiedFilterQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Filter query cannot be null");
        }

        log.info("Executing filter: {} conditions, logic={}",
                query.getConditions() != null ? query.getConditions().size() : 0,
                query.getLogic());

        try {
            // 1. 验证并构建已验证条件列表
            List<ValidatedFilterCondition> validatedConditions =
                    validateAndPrepareConditions(query.getConditions());

            // 2. 构建 SQL 查询
            LambdaQueryWrapper<AppUser> wrapper = buildQuery(validatedConditions, query.getLogic());

            // 3. 应用排序
            applySorting(wrapper, query.getSort());

            // 4. 执行分页查询
            UnifiedFilterQuery.PaginationInfo pagination = query.getPagination();
            int pageNum  = pagination != null && pagination.getPage() != null ? pagination.getPage() : 1;
            int pageSize = pagination != null && pagination.getSize() != null ? pagination.getSize() : 20;
            Page<AppUser> page = userService.page(new Page<>(pageNum, pageSize), wrapper);

            log.info("Filter completed: {} records (total={})", page.getRecords().size(), page.getTotal());

            return FilterQueryResult.builder()
                    .success(true)
                    .records(page.getRecords())
                    .totalRecords(page.getTotal())
                    .currentPage(page.getCurrent())
                    .pageSize(page.getSize())
                    .totalPages(page.getPages())
                    .build();

        } catch (Exception e) {
            log.error("Filter execution failed: {}", e.getMessage(), e);
            return FilterQueryResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 验证条件并构建已验证条件列表，跳过无效条件
     */
    private List<ValidatedFilterCondition> validateAndPrepareConditions(
            List<UnifiedFilterCondition> conditions) {

        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

        List<ValidatedFilterCondition> result = new ArrayList<>();
        for (UnifiedFilterCondition condition : conditions) {
            try {
                if (condition == null || !condition.isValid()) {
                    log.warn("Skipping invalid condition: {}", condition);
                    continue;
                }

                FilterValidationResult validationResult = filterRegistry.validateCondition(
                        condition.getFieldType(),
                        condition.getOperator(),
                        condition.getValue()
                );

                if (!validationResult.isValid()) {
                    log.warn("Condition validation failed [{}]: {}",
                            condition.getFieldKey(), validationResult.getErrorMessage());
                    continue;
                }

                result.add(ValidatedFilterCondition.from(condition, validationResult));

            } catch (Exception e) {
                log.error("Error validating condition [{}]: {}", condition.getFieldKey(), e.getMessage());
            }
        }

        log.debug("Validated {}/{} conditions", result.size(), conditions.size());
        return result;
    }

    /**
     * 构建 LambdaQueryWrapper
     */
    private LambdaQueryWrapper<AppUser> buildQuery(
            List<ValidatedFilterCondition> conditions,
            UnifiedFilterQuery.FilterLogic logic) {

        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        if (!conditions.isEmpty()) {
            boolean useAnd = (logic != UnifiedFilterQuery.FilterLogic.OR);
            filterRegistry.applyFilters(wrapper, conditions, useAnd);
        }
        return wrapper;
    }

    /**
     * 应用排序（默认按注册时间降序）
     */
    private void applySorting(LambdaQueryWrapper<AppUser> wrapper,
                               UnifiedFilterQuery.SortInfo sortInfo) {
        if (sortInfo == null || sortInfo.getField() == null) {
            wrapper.orderByDesc(AppUser::getRegisterTime);
            return;
        }

        String field = sortInfo.getField();
        boolean asc = (sortInfo.getDirection() == UnifiedFilterQuery.SortInfo.SortDirection.ASC);

        switch (field) {
            case "registerTime":
                if (asc) wrapper.orderByAsc(AppUser::getRegisterTime);
                else     wrapper.orderByDesc(AppUser::getRegisterTime);
                break;
            case "lastLoginTime":
                if (asc) wrapper.orderByAsc(AppUser::getLastLoginTime);
                else     wrapper.orderByDesc(AppUser::getLastLoginTime);
                break;
            case "nickname":
                if (asc) wrapper.orderByAsc(AppUser::getNickname);
                else     wrapper.orderByDesc(AppUser::getNickname);
                break;
            default:
                log.warn("Sort field '{}' not supported, falling back to registerTime desc", field);
                wrapper.orderByDesc(AppUser::getRegisterTime);
        }
    }

    // ==================== 结果类 ====================

    /**
     * 筛选查询结果
     */
    @Data
    @Builder
    public static class FilterQueryResult {
        private boolean success;
        private List<AppUser> records;
        private Long totalRecords;
        private Long currentPage;
        private Long pageSize;
        private Long totalPages;
        private String errorMessage;
    }
}
