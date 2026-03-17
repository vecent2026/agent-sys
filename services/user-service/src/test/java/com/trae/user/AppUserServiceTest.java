package com.trae.user;

import com.trae.user.cache.ImportCacheService;
import com.trae.user.executor.ImportTaskExecutor;
import com.trae.user.dto.BatchTagDTO;
import com.trae.user.dto.UserStatusDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.mapper.AppUserMapper;
import com.trae.user.service.AppUserFieldService;
import com.trae.user.service.AppUserFieldValueService;
import com.trae.user.service.AppUserTagRelationService;
import com.trae.user.service.AppUserTagService;
import com.trae.user.service.impl.AppUserServiceImpl;
import com.trae.user.vo.AppUserVO;
import com.trae.user.vo.UserFieldValueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AppUserServiceImpl 单元测试
 * 注意：AppUserServiceImpl extends ServiceImpl，需要通过 ReflectionTestUtils 注入 baseMapper
 */
@ExtendWith(MockitoExtension.class)
public class AppUserServiceTest {

    @Mock AppUserMapper appUserMapper;
    @Mock AppUserTagService tagService;
    @Mock AppUserTagRelationService tagRelationService;
    @Mock AppUserFieldService fieldService;
    @Mock AppUserFieldValueService fieldValueService;
    @Mock ImportCacheService cacheService;
    @Mock ImportTaskExecutor importTaskExecutor;

    private AppUserServiceImpl appUserService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        appUserService = new AppUserServiceImpl(
                tagService, tagRelationService, fieldService, fieldValueService, cacheService, importTaskExecutor);
        // ServiceImpl 父类中的 baseMapper 字段名不同，需手动注入
        ReflectionTestUtils.setField(appUserService, "baseMapper", appUserMapper);

        testUser = new AppUser();
        testUser.setId(1L);
        testUser.setNickname("测试用户");
        testUser.setMobile("13800138000");
        testUser.setEmail("test@example.com");
        testUser.setStatus(1);
        testUser.setRegisterTime(LocalDateTime.now());
        testUser.setIsDeleted(0);
    }

    // ── getUserDetail ──────────────────────────────────────

    @Test
    void testGetUserDetail_returnsVO() {
        when(appUserMapper.selectById(1L)).thenReturn(testUser);
        when(tagRelationService.getTagIdsByUserId(1L)).thenReturn(new ArrayList<>());

        AppUserVO result = appUserService.getUserDetail(1L);

        assertNotNull(result);
        assertEquals("测试用户", result.getNickname());
        assertEquals("13800138000", result.getMobile());
    }

    // ── updateUserStatus ───────────────────────────────────

    @Test
    void testUpdateUserStatus_callsUpdateById() {
        UserStatusDTO dto = new UserStatusDTO();
        dto.setStatus(0);

        appUserService.updateUserStatus(1L, dto);

        verify(appUserMapper).updateById(any(AppUser.class));
    }

    // ── updateUserFieldValues ──────────────────────────────

    @Test
    void testUpdateUserFieldValues_emptyList_noException() {
        assertDoesNotThrow(() -> appUserService.updateUserFieldValues(1L, new ArrayList<>()));
    }

    // ── batchRemoveTags ────────────────────────────────────

    @Test
    void testBatchRemoveTags_delegatesToRelationService() {
        BatchTagDTO dto = new BatchTagDTO();
        dto.setUserIds(List.of(1L));
        dto.setTagIds(List.of(1L));

        appUserService.batchRemoveTags(dto);

        verify(tagRelationService).batchDeleteByUserIdsAndTagIds(List.of(1L), List.of(1L));
        verify(tagService).batchUpdateTagUserCount(List.of(1L));
    }
}
