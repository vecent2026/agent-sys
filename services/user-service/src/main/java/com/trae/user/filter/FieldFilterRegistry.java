package com.trae.user.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.handlers.BuiltinStringFieldHandler;
import com.trae.user.filter.handlers.BuiltinEnumFieldHandler;
import com.trae.user.filter.handlers.BuiltinDateFieldHandler;
import com.trae.user.filter.handlers.BuiltinNumberFieldHandler;
import com.trae.user.filter.handlers.TagCascadeFieldHandler;
import com.trae.user.filter.handlers.CustomTextFieldHandler;
import com.trae.user.filter.handlers.CustomNumberFieldHandler;
import com.trae.user.filter.handlers.CustomDateFieldHandler;
import com.trae.user.filter.handlers.CustomRadioFieldHandler;
import com.trae.user.filter.handlers.CustomCheckboxFieldHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段筛选处理器注册表 - 统一筛选系统处理器管理
 * 
 * 管理所有字段类型的筛选处理器，提供统一的注册、查找和应用接口
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldFilterRegistry {
    
    // 注入内置字段处理器
    private final BuiltinStringFieldHandler builtinStringFieldHandler;
    private final BuiltinEnumFieldHandler builtinEnumFieldHandler;
    private final BuiltinDateFieldHandler builtinDateFieldHandler;
    private final BuiltinNumberFieldHandler builtinNumberFieldHandler;
    
    // 注入特殊字段处理器
    private final TagCascadeFieldHandler tagCascadeFieldHandler;
    
    // 注入动态字段处理器
    private final CustomTextFieldHandler customTextFieldHandler;
    private final CustomNumberFieldHandler customNumberFieldHandler;
    private final CustomDateFieldHandler customDateFieldHandler;
    private final CustomRadioFieldHandler customRadioFieldHandler;
    private final CustomCheckboxFieldHandler customCheckboxFieldHandler;
    
    /**
     * 处理器映射表 (FieldType -> Handler)
     */
    private final Map<FieldType, FieldFilterHandler> handlerMap = new ConcurrentHashMap<>();
    
    /**
     * 处理器列表 (用于遍历)
     */
    private final List<FieldFilterHandler> handlers = new ArrayList<>();
    
    /**
     * 性能统计 (可选)
     */
    private final Map<FieldType, PerformanceStats> performanceStats = new ConcurrentHashMap<>();
    
    /**
     * 初始化注册表
     */
    @PostConstruct
    public void initializeRegistry() {
        log.info("Initializing FieldFilterRegistry...");
        
        // 注册内置字段处理器
        registerHandler(builtinStringFieldHandler);
        registerHandler(builtinEnumFieldHandler);
        registerHandler(builtinDateFieldHandler);
        registerHandler(builtinNumberFieldHandler);
        
        // 注册特殊字段处理器
        registerHandler(tagCascadeFieldHandler);
        
        // 注册动态字段处理器
        registerHandler(customTextFieldHandler);
        registerHandler(customNumberFieldHandler);
        registerHandler(customDateFieldHandler);
        registerHandler(customRadioFieldHandler);
        registerHandler(customCheckboxFieldHandler);
        
        log.info("FieldFilterRegistry initialized with {} handlers", handlers.size());
        
        // 打印支持的字段类型信息
        if (log.isDebugEnabled()) {
            for (FieldType fieldType : getSupportedFieldTypes()) {
                Optional<FieldFilterHandler> handler = getHandler(fieldType);
                if (handler.isPresent()) {
                    log.debug("Registered: {} - {}", fieldType.getCode(), handler.get().getDescription());
                }
            }
        }
    }
    
    /**
     * 注册字段处理器
     */
    public void registerHandler(FieldFilterHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        FieldType fieldType = handler.getSupportedFieldType();
        if (fieldType == null) {
            throw new IllegalArgumentException("Handler must specify supported field type");
        }
        
        if (handlerMap.containsKey(fieldType)) {
            log.warn("Overriding existing handler for field type: {}", fieldType);
        }
        
        handlerMap.put(fieldType, handler);
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }
        
        // 初始化性能统计
        performanceStats.putIfAbsent(fieldType, new PerformanceStats());
        
        log.debug("Registered handler for field type: {} - {}", fieldType, handler.getDescription());
    }
    
    /**
     * 获取字段类型的处理器
     */
    public Optional<FieldFilterHandler> getHandler(FieldType fieldType) {
        return Optional.ofNullable(handlerMap.get(fieldType));
    }
    
    /**
     * 检查字段类型是否被支持
     */
    public boolean isSupported(FieldType fieldType) {
        return handlerMap.containsKey(fieldType);
    }
    
    /**
     * 获取字段类型支持的操作符列表
     */
    public List<FilterOperator> getSupportedOperators(FieldType fieldType) {
        return getHandler(fieldType)
                .map(FieldFilterHandler::getSupportedOperators)
                .orElse(Collections.emptyList());
    }
    
    /**
     * 验证筛选条件
     */
    public FilterValidationResult validateCondition(FieldType fieldType, FilterOperator operator, Object value) {
        Optional<FieldFilterHandler> handler = getHandler(fieldType);
        if (!handler.isPresent()) {
            return FilterValidationResult.failure(
                    "不支持的字段类型: " + fieldType,
                    FilterValidationResult.ErrorCodes.FIELD_NOT_FOUND
            );
        }
        
        FieldFilterHandler h = handler.get();
        if (!h.supportsOperator(operator)) {
            return FilterValidationResult.failure(
                    String.format("字段类型 %s 不支持操作符 %s", fieldType.getDescription(), operator.getLabel()),
                    FilterValidationResult.ErrorCodes.OPERATOR_NOT_SUPPORTED
            );
        }
        
        return h.validateValue(operator, value);
    }
    
    /**
     * 应用筛选条件
     */
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        if (condition == null || !condition.isValid()) {
            log.warn("Attempting to apply invalid filter condition: {}", condition);
            return;
        }
        
        FieldType fieldType = condition.getFieldType();
        Optional<FieldFilterHandler> handler = getHandler(fieldType);
        
        if (!handler.isPresent()) {
            log.error("No handler found for field type: {} in condition: {}", fieldType, condition.getFieldKey());
            throw new UnsupportedFieldTypeException(fieldType);
        }
        
        long startTime = System.currentTimeMillis();
        try {
            handler.get().applyFilter(wrapper, condition);
            
            // 记录性能统计
            long executionTime = System.currentTimeMillis() - startTime;
            updatePerformanceStats(fieldType, executionTime, true);
            
            log.debug("Applied filter for field: {} ({}ms)", condition.getFieldKey(), executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            updatePerformanceStats(fieldType, executionTime, false);
            
            log.error("Error applying filter for field: {} - {}", condition.getFieldKey(), e.getMessage(), e);
            throw new FilterApplicationException("Failed to apply filter for field: " + condition.getFieldKey(), e);
        }
    }
    
    /**
     * 批量应用筛选条件
     */
    public void applyFilters(LambdaQueryWrapper<AppUser> wrapper, 
                           List<ValidatedFilterCondition> conditions,
                           boolean useAndLogic) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        
        log.debug("Applying {} filters with {} logic", conditions.size(), useAndLogic ? "AND" : "OR");
        
        if (useAndLogic) {
            // AND 逻辑：所有条件都必须满足
            for (ValidatedFilterCondition condition : conditions) {
                wrapper.and(w -> this.applyFilter(w, condition));
            }
        } else {
            // OR 逻辑：任一条件满足即可
            wrapper.and(w -> {
                for (int i = 0; i < conditions.size(); i++) {
                    if (i > 0) {
                        w.or();
                    }
                    ValidatedFilterCondition condition = conditions.get(i);
                    applyFilter(w, condition);
                }
            });
        }
    }
    
    /**
     * 获取所有支持的字段类型
     */
    public Set<FieldType> getSupportedFieldTypes() {
        return new HashSet<>(handlerMap.keySet());
    }
    
    /**
     * 获取注册表统计信息
     */
    public RegistryStats getStats() {
        return RegistryStats.builder()
                .totalHandlers(handlers.size())
                .supportedFieldTypes(handlerMap.keySet())
                .performanceStats(new HashMap<>(performanceStats))
                .build();
    }
    
    /**
     * 更新性能统计
     */
    private void updatePerformanceStats(FieldType fieldType, long executionTime, boolean success) {
        PerformanceStats stats = performanceStats.get(fieldType);
        if (stats != null) {
            stats.addExecution(executionTime, success);
        }
    }
    
    /**
     * 性能统计内部类
     */
    public static class PerformanceStats {
        private long totalExecutions = 0;
        private long successfulExecutions = 0;
        private long totalExecutionTime = 0;
        private long maxExecutionTime = 0;
        private long minExecutionTime = Long.MAX_VALUE;
        
        public synchronized void addExecution(long executionTime, boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            }
            totalExecutionTime += executionTime;
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            minExecutionTime = Math.min(minExecutionTime, executionTime);
        }
        
        public synchronized double getAverageExecutionTime() {
            return totalExecutions > 0 ? (double) totalExecutionTime / totalExecutions : 0;
        }
        
        public synchronized double getSuccessRate() {
            return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions : 0;
        }
        
        // Getters
        public long getTotalExecutions() { return totalExecutions; }
        public long getSuccessfulExecutions() { return successfulExecutions; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
        public long getMinExecutionTime() { return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime; }
    }
    
    /**
     * 注册表统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class RegistryStats {
        private int totalHandlers;
        private Set<FieldType> supportedFieldTypes;
        private Map<FieldType, PerformanceStats> performanceStats;
    }
    
    /**
     * 不支持的字段类型异常
     */
    public static class UnsupportedFieldTypeException extends RuntimeException {
        private final FieldType fieldType;
        
        public UnsupportedFieldTypeException(FieldType fieldType) {
            super("Unsupported field type: " + fieldType);
            this.fieldType = fieldType;
        }
        
        public FieldType getFieldType() {
            return fieldType;
        }
    }
    
    /**
     * 筛选应用异常
     */
    public static class FilterApplicationException extends RuntimeException {
        public FilterApplicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}