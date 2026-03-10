package com.trae.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.user.dto.AppUserQueryDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.service.AppUserService;
import com.trae.user.vo.AppUserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
public class AppUserServiceIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.4.8")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("sql/schema.sql");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }

    @Autowired
    private AppUserService appUserService;

    @Test
    void testGetUserPage() {
        // 准备测试数据
        AppUserQueryDTO queryDTO = new AppUserQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setSize(10);

        // 执行测试
        Page<AppUserVO> result = appUserService.getUserPage(queryDTO);

        // 验证结果
        assertNotNull(result);
        // 这里可以添加更多验证逻辑
    }

    @Test
    void testGetUserDetail() {
        // 执行测试
        AppUserVO result = appUserService.getUserDetail(1L);

        // 验证结果
        // 这里可以添加更多验证逻辑
    }
}
