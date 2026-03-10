package com.trae.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.dto.AppUserQueryDTO;
import com.trae.user.dto.BatchTagDTO;
import com.trae.user.dto.UserStatusDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.AppUserTag;
import com.trae.user.entity.AppUserTagRelation;
import com.trae.user.mapper.AppUserMapper;
import com.trae.user.service.AppUserService;
import com.trae.user.service.AppUserTagRelationService;
import com.trae.user.service.AppUserTagService;
import com.trae.user.vo.AppUserVO;
import com.trae.user.vo.UserFieldValueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppUserServiceTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private AppUserTagService tagService;

    @Mock
    private AppUserTagRelationService tagRelationService;

    @InjectMocks
    private AppUserService appUserService;

    private AppUser testUser;
    private AppUserTag testTag;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        testUser = new AppUser();
        testUser.setId(1L);
        testUser.setNickname("测试用户");
        testUser.setMobile("13800138000");
        testUser.setEmail("test@example.com");
        testUser.setStatus(1);
        testUser.setRegisterTime(LocalDateTime.now());

        testTag = new AppUserTag();
        testTag.setId(1L);
        testTag.setName("VIP用户");
        testTag.setColor("gold");
        testTag.setUserCount(0);
    }

    @Test
    void testGetUserPage() {
        // 准备测试数据
        AppUserQueryDTO queryDTO = new AppUserQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setSize(10);

        List<AppUser> userList = new ArrayList<>();
        userList.add(testUser);

        Page<AppUser> page = new Page<>(1, 10);
        page.setRecords(userList);
        page.setTotal(1);

        // 模拟方法调用
        when(appUserMapper.selectPage(any(), any())).thenReturn(page);
        when(tagRelationService.getTagIdsByUserId(anyLong())).thenReturn(new ArrayList<>());

        // 执行测试
        Page<AppUserVO> result = appUserService.getUserPage(queryDTO);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("测试用户", result.getRecords().get(0).getNickname());
    }

    @Test
    void testGetUserDetail() {
        // 模拟方法调用
        when(appUserMapper.selectById(anyLong())).thenReturn(testUser);
        when(tagRelationService.getTagIdsByUserId(anyLong())).thenReturn(new ArrayList<>());

        // 执行测试
        AppUserVO result = appUserService.getUserDetail(1L);

        // 验证结果
        assertNotNull(result);
        assertEquals("测试用户", result.getNickname());
    }

    @Test
    void testUpdateUserStatus() {
        // 准备测试数据
        UserStatusDTO statusDTO = new UserStatusDTO();
        statusDTO.setStatus(0);

        // 执行测试
        appUserService.updateUserStatus(1L, statusDTO);

        // 验证结果
        verify(appUserMapper, times(1)).updateById(any(AppUser.class));
    }

    @Test
    void testAssignUserTags() {
        // 准备测试数据
        List<Long> tagIds = new ArrayList<>();
        tagIds.add(1L);

        // 模拟方法调用
        when(tagRelationService.getTagIdsByUserId(anyLong())).thenReturn(new ArrayList<>());

        // 执行测试
        appUserService.assignUserTags(1L, tagIds);

        // 验证结果
        verify(tagRelationService, times(1)).lambdaUpdate();
        verify(tagRelationService, times(1)).saveBatch(anyList());
        verify(tagService, times(1)).updateTagUserCount(anyLong());
    }

    @Test
    void testBatchAddTags() {
        // 准备测试数据
        BatchTagDTO batchTagDTO = new BatchTagDTO();
        List<Long> userIds = new ArrayList<>();
        userIds.add(1L);
        List<Long> tagIds = new ArrayList<>();
        tagIds.add(1L);
        batchTagDTO.setUserIds(userIds);
        batchTagDTO.setTagIds(tagIds);

        // 模拟方法调用
        when(tagRelationService.getTagIdsByUserId(anyLong())).thenReturn(new ArrayList<>());

        // 执行测试
        appUserService.batchAddTags(batchTagDTO);

        // 验证结果
        verify(tagRelationService, times(1)).saveBatch(anyList());
        verify(tagService, times(1)).updateTagUserCount(anyLong());
    }

    @Test
    void testBatchRemoveTags() {
        // 准备测试数据
        BatchTagDTO batchTagDTO = new BatchTagDTO();
        List<Long> userIds = new ArrayList<>();
        userIds.add(1L);
        List<Long> tagIds = new ArrayList<>();
        tagIds.add(1L);
        batchTagDTO.setUserIds(userIds);
        batchTagDTO.setTagIds(tagIds);

        // 执行测试
        appUserService.batchRemoveTags(batchTagDTO);

        // 验证结果
        verify(tagRelationService, times(1)).batchDeleteByUserIdsAndTagIds(anyList(), anyList());
        verify(tagService, times(1)).updateTagUserCount(anyLong());
    }

    @Test
    void testGetUserFieldValues() {
        // 执行测试
        List<UserFieldValueVO> result = appUserService.getUserFieldValues(1L);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testUpdateUserFieldValues() {
        // 准备测试数据
        List<UserFieldValueVO> fieldValues = new ArrayList<>();

        // 执行测试
        appUserService.updateUserFieldValues(1L, fieldValues);

        // 验证结果
        // 这里可以添加更多验证逻辑
    }
}
