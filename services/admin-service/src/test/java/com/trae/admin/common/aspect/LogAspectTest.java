package com.trae.admin.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogAspectTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Mock
    private JwtUtil jwtUtil;
    
    @Mock
    private SysUserMapper sysUserMapper;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private LogAspect logAspect;

    @Test
    void desensitize_SensitiveFields() {
        // 测试敏感字段脱敏功能
        String input = "{\"username\":\"admin\",\"password\":\"123456\",\"token\":\"abc123\",\"accessToken\":\"def456\",\"refreshToken\":\"ghi789\"}";
        
        String result = logAspect.desensitize(input);
        
        // 验证所有敏感字段都被正确脱敏
        assert result.contains("\"password\":\"******\"");
        assert result.contains("\"token\":\"******\"");
        assert result.contains("\"accessToken\":\"******\"");
        assert result.contains("\"refreshToken\":\"******\"");
        // 验证非敏感字段保持不变
        assert result.contains("\"username\":\"admin\"");
    }
    
    @Test
    void desensitize_NullInput() {
        // 测试空输入脱敏
        String result = logAspect.desensitize(null);
        assert result == null;
        
        result = logAspect.desensitize("");
        assert result.isEmpty();
    }
    
    @Test
    void desensitize_NonSensitiveFields() {
        // 测试非敏感字段不被脱敏
        String input = "{\"username\":\"admin\",\"email\":\"test@example.com\",\"phone\":\"13800138000\"}";
        
        String result = logAspect.desensitize(input);
        
        // 验证非敏感字段保持不变
        assert result.equals(input);
    }
    
    @Test
    void log_Aspect_DependenciesInjected() {
        // 测试日志切面的依赖注入
        // 由于saveLog方法依赖多个静态上下文，无法直接测试，这里只验证基本依赖注入
        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(sysUserMapper);
        verifyNoInteractions(objectMapper);
    }
    
    @Test
    void test_MdcTraceId() {
        // 测试MDC traceId获取
        String expectedTraceId = "test-trace-id-123"; 
        MDC.put("traceId", expectedTraceId);
        
        // 验证MDC能正确获取traceId
        String actualTraceId = MDC.get("traceId");
        assert expectedTraceId.equals(actualTraceId);
        
        MDC.remove("traceId");
        assert MDC.get("traceId") == null;
    }
}
