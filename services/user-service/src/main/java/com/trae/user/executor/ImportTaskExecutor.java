package com.trae.user.executor;

import cn.hutool.core.util.StrUtil;
import com.trae.user.cache.ImportCacheService;
import com.trae.user.dto.UserImportDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.AppUserTag;
import com.trae.user.entity.AppUserTagRelation;
import com.trae.user.service.AppUserService;
import com.trae.user.service.AppUserTagRelationService;
import com.trae.user.service.AppUserTagService;
import com.trae.user.vo.UserImportProgressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportTaskExecutor {
    private final ImportCacheService cacheService;
    private final AppUserTagService tagService;
    private final AppUserTagRelationService tagRelationService;

    @Autowired
    @Lazy
    private AppUserService appUserService;

    private static final int BATCH_SIZE = 500;
    private static final String DEFAULT_AVATAR = "https://default-avatar.example.com/avatar.png";

    @Async
    public void executeImport(String taskId, String importTaskId, List<UserImportDTO> validDataList) {
        UserImportProgressVO progress = new UserImportProgressVO();
        progress.setStatus(UserImportProgressVO.STATUS_PROCESSING);
        progress.setTotal(validDataList.size());
        progress.setProcessed(0);
        progress.setSuccess(0);
        progress.setFailed(0);
        progress.setProgress(0);
        progress.setErrors(new ArrayList<>());
        cacheService.cacheImportProgress(importTaskId, progress);

        try {
            Map<String, Long> tagNameToIdMap = buildTagNameToIdMap();
            List<List<UserImportDTO>> batches = splitIntoBatches(validDataList);

            int processed = 0;
            int success = 0;
            int failed = 0;
            List<UserImportProgressVO.ImportError> errors = new ArrayList<>();

            for (List<UserImportDTO> batch : batches) {
                List<AppUser> userBatch = new ArrayList<>();
                List<UserImportDTO> validDtosInBatch = new ArrayList<>();

                for (UserImportDTO dto : batch) {
                    try {
                        AppUser user = convertToEntity(dto);
                        userBatch.add(user);
                        validDtosInBatch.add(dto);
                    } catch (Exception e) {
                        failed++;
                        UserImportProgressVO.ImportError error = new UserImportProgressVO.ImportError();
                        error.setRow(dto.getRowIndex());
                        error.setMobile(dto.getMobile());
                        error.setReason(e.getMessage());
                        errors.add(error);
                        processed++;
                    }
                }

                if (!userBatch.isEmpty()) {
                    try {
                        appUserService.saveBatch(userBatch);

                        List<AppUserTagRelation> allRelations = new ArrayList<>();
                        for (int i = 0; i < userBatch.size(); i++) {
                            AppUser user = userBatch.get(i);
                            UserImportDTO dto = validDtosInBatch.get(i);
                            if (StrUtil.isNotBlank(dto.getTags())) {
                                String[] tagNames = dto.getTags().split(",");
                                for (String tagName : tagNames) {
                                    Long tagId = tagNameToIdMap.get(tagName.trim());
                                    if (tagId != null) {
                                        AppUserTagRelation relation = new AppUserTagRelation();
                                        relation.setUserId(user.getId());
                                        relation.setTagId(tagId);
                                        allRelations.add(relation);
                                    }
                                }
                            }
                            success++;
                            processed++;
                        }

                        if (!allRelations.isEmpty()) {
                            tagRelationService.saveBatch(allRelations);
                            Set<Long> updatedTagIds = allRelations.stream().map(AppUserTagRelation::getTagId).collect(Collectors.toSet());
                            tagService.batchUpdateTagUserCount(new ArrayList<>(updatedTagIds));
                        }
                    } catch (Exception e) {
                        log.error("批量插入用户失败", e);
                        for (UserImportDTO dto : validDtosInBatch) {
                            failed++;
                            processed++;
                            UserImportProgressVO.ImportError error = new UserImportProgressVO.ImportError();
                            error.setRow(dto.getRowIndex());
                            error.setMobile(dto.getMobile());
                            error.setReason("批量插入失败: " + e.getMessage());
                            errors.add(error);
                        }
                    }
                }

                progress.setProcessed(processed);
                progress.setSuccess(success);
                progress.setFailed(failed);
                progress.setProgress(calculateProgress(processed, validDataList.size()));
                if (!errors.isEmpty()) {
                    progress.setErrors(errors.size() <= 10 ? errors : errors.subList(errors.size() - 10, errors.size()));
                }
                cacheService.cacheImportProgress(importTaskId, progress);
            }

            progress.setStatus(UserImportProgressVO.STATUS_COMPLETED);
            progress.setProgress(100);
            cacheService.cacheImportProgress(importTaskId, progress);

            cacheService.deleteValidateResult(taskId);
        } catch (Exception e) {
            log.error("导入任务执行失败", e);
            progress.setStatus(UserImportProgressVO.STATUS_FAILED);
            UserImportProgressVO.ImportError error = new UserImportProgressVO.ImportError();
            error.setReason("导入任务执行失败: " + e.getMessage());
            progress.setErrors(List.of(error));
            cacheService.cacheImportProgress(importTaskId, progress);
        }
    }

    private Map<String, Long> buildTagNameToIdMap() {
        List<AppUserTag> tags = tagService.list();
        return tags.stream()
                .collect(Collectors.toMap(
                        tag -> tag.getName().trim(),
                        AppUserTag::getId,
                        (existing, replacement) -> existing
                ));
    }

    private List<List<UserImportDTO>> splitIntoBatches(List<UserImportDTO> dataList) {
        List<List<UserImportDTO>> batches = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, dataList.size());
            batches.add(dataList.subList(i, end));
        }
        return batches;
    }

    private AppUser convertToEntity(UserImportDTO dto) {
        AppUser user = new AppUser();
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? "用户" + dto.getMobile().substring(7) : dto.getNickname().trim());
        user.setMobile(dto.getMobile().trim());
        user.setEmail(StrUtil.isBlank(dto.getEmail()) ? null : dto.getEmail().trim());
        user.setGender(parseGender(dto.getGender()));
        user.setBirthday(parseBirthday(dto.getBirthday()));
        user.setStatus(parseStatus(dto.getStatus()));
        user.setRegisterSource("IMPORT");
        user.setRegisterTime(LocalDateTime.now());
        user.setAvatar(DEFAULT_AVATAR);
        return user;
    }

    private Integer parseGender(String gender) {
        if (StrUtil.isBlank(gender)) {
            return 0;
        }
        return switch (gender.trim()) {
            case "男" -> 1;
            case "女" -> 2;
            default -> 0;
        };
    }

    private LocalDate parseBirthday(String birthday) {
        if (StrUtil.isBlank(birthday)) {
            return null;
        }
        try {
            return LocalDate.parse(birthday.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return 1;
        }
        return switch (status.trim()) {
            case "禁用" -> 0;
            case "注销" -> 2;
            default -> 1;
        };
    }

    // assignTags removed as it's now handled in batch

    private int calculateProgress(int processed, int total) {
        if (total == 0) {
            return 100;
        }
        return (int) ((double) processed / total * 100);
    }
}
