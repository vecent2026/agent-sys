package com.trae.admin.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantContext 单元测试
 * 覆盖：set/get/clear、ThreadLocal 线程隔离
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnsCorrectTenantId() {
        TenantContext.setTenantId(5L);
        assertEquals(5L, TenantContext.getTenantId());
    }

    @Test
    void clear_removesValue() {
        TenantContext.setTenantId(5L);
        TenantContext.clear();
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void get_withoutSet_returnsNull() {
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void threadIsolation_differentThreadsHaveIndependentValues() throws InterruptedException {
        TenantContext.setTenantId(1L);

        AtomicLong threadValue = new AtomicLong(-1L);
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            // 新线程应看不到主线程设置的 tenantId
            Long val = TenantContext.getTenantId();
            threadValue.set(val != null ? val : 0L);
            // 新线程设置自己的值
            TenantContext.setTenantId(99L);
            latch.countDown();
        });
        t.start();
        latch.await();

        // 新线程里 get = null（0L 代替）
        assertEquals(0L, threadValue.get());
        // 主线程值不受影响
        assertEquals(1L, TenantContext.getTenantId());
    }

    @Test
    void overwrite_updatesValue() {
        TenantContext.setTenantId(1L);
        TenantContext.setTenantId(2L);
        assertEquals(2L, TenantContext.getTenantId());
    }
}
