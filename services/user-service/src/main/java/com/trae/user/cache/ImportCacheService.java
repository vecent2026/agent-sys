package com.trae.user.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trae.user.common.exception.BusinessException;
import com.trae.user.dto.UserImportDTO;
import com.trae.user.vo.UserImportProgressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportCacheService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String VALIDATE_KEY_PREFIX = "import:validate:";
    private static final String PROGRESS_KEY_PREFIX = "import:progress:";
    private static final long VALIDATE_EXPIRE_MINUTES = 30;
    private static final long PROGRESS_EXPIRE_HOURS = 1;

    public void cacheValidateResult(String taskId, List<UserImportDTO> dataList) {
        String key = VALIDATE_KEY_PREFIX + taskId;
        try {
            String json = objectMapper.writeValueAsString(dataList);
            redisTemplate.opsForValue().set(key, json, VALIDATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("缓存校验结果失败", e);
            throw new BusinessException("缓存校验结果失败");
        }
    }

    public List<UserImportDTO> getValidateResult(String taskId) {
        String key = VALIDATE_KEY_PREFIX + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UserImportDTO>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析校验结果失败", e);
            throw new BusinessException("解析校验结果失败");
        }
    }

    public void deleteValidateResult(String taskId) {
        String key = VALIDATE_KEY_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    public void cacheImportProgress(String importTaskId, UserImportProgressVO progress) {
        String key = PROGRESS_KEY_PREFIX + importTaskId;
        try {
            String json = objectMapper.writeValueAsString(progress);
            redisTemplate.opsForValue().set(key, json, PROGRESS_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("缓存导入进度失败", e);
            throw new BusinessException("缓存导入进度失败");
        }
    }

    public UserImportProgressVO getImportProgress(String importTaskId) {
        String key = PROGRESS_KEY_PREFIX + importTaskId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UserImportProgressVO.class);
        } catch (JsonProcessingException e) {
            log.error("解析导入进度失败", e);
            throw new BusinessException("解析导入进度失败");
        }
    }

    public void deleteImportProgress(String importTaskId) {
        String key = PROGRESS_KEY_PREFIX + importTaskId;
        redisTemplate.delete(key);
    }
}
