# 06 迁移与实施计划

## 1. 阶段划分

```
Phase 0（准备）  → Phase 1（数据层）→ Phase 2（后端）→ Phase 3（前端）→ Phase 4（上线）
    1周                2周               4周               4周              1周
```

---

## 2. Phase 0：准备工作（约 1 周）

### 2.1 数据备份

```bash
# 全量备份，命名带日期
mysqldump -u root -p admin_system > backup_admin_$(date +%Y%m%d_%H%M).sql
mysqldump -u root -p trae_user    > backup_user_$(date +%Y%m%d_%H%M).sql

# 验证备份文件完整性
mysql -u root -p < backup_admin_xxx.sql  # 在测试库验证可恢复
```

### 2.2 环境确认

```bash
# 确认 MyBatis-Plus 版本 ≥ 3.4.0（支持 TenantLineInnerInterceptor）
grep 'mybatis-plus' services/admin-service/pom.xml

# 确认 Redis 已部署（用于 token 黑名单、权限缓存、租户版本号缓存）
redis-cli ping
```

### 2.3 分支策略

```
main
 └── feature/multi-tenant            # 多租户总分支，各阶段向此分支合入
      ├── feature/tenant-db-admin    # admin_db 迁移
      ├── feature/tenant-db-user     # user_db 迁移
      ├── feature/tenant-backend     # 后端改造
      └── feature/tenant-frontend    # 前端改造
```

---

## 3. Phase 1：数据库迁移（约 2 周）

> **执行原则**：每步 DDL 之后立刻验证，不要批量执行。生产环境在业务低峰期操作，大表 UPDATE 使用分批次方案。重命名（RENAME TABLE）操作为原子操作，不会造成数据丢失。

### 3.1 admin_db 完整执行顺序

```sql
-- ══════════════════════════════════════════════
-- Step 1: 创建默认租户
-- ══════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `platform_tenant` ( ... );   -- 见 02-database-design.md §1.1
INSERT INTO `platform_tenant` (`id`,`tenant_code`,`tenant_name`,`status`,`data_version`,`create_by`)
VALUES (1, 'tiannan', '天南大陆', 1, 0, 'system');

-- ══════════════════════════════════════════════
-- Step 2: 创建 platform_user（不删 sys_user，并行运行一段时间）
-- ══════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `platform_user` ( ... );     -- 见 02-database-design.md §1.2
INSERT INTO `platform_user` (...)
SELECT ... FROM `sys_user`;
UPDATE `platform_user` SET `is_super` = 1 WHERE `id` = 1;

-- ══════════════════════════════════════════════
-- Step 3: sys_permission 增加 scope 字段，然后重命名为 platform_permission
-- ══════════════════════════════════════════════
ALTER TABLE `sys_permission`
  ADD COLUMN `scope` VARCHAR(20) NOT NULL DEFAULT 'tenant'
    COMMENT 'platform=平台专属 tenant=可授权租户' AFTER `type`;

-- 更新权限标识（见 §4）后执行重命名
RENAME TABLE `sys_permission` TO `platform_permission`;

-- ══════════════════════════════════════════════
-- Step 4: 更新权限标识（sys:xxx → 新格式）
-- ══════════════════════════════════════════════
-- 注意：此步在 RENAME TABLE 之前执行，仍用 sys_permission；
--        若 RENAME 已完成，则用 platform_permission
-- 见 §4 权限标识迁移

-- ══════════════════════════════════════════════
-- Step 5: sys_role 增加 tenant_id，更新数据，然后重命名为 tenant_role
-- ══════════════════════════════════════════════
ALTER TABLE `sys_role`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0
    COMMENT '租户ID（0=平台级）' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- 分批次更新（避免大表锁）
UPDATE `sys_role` SET `tenant_id` = 1 WHERE `tenant_id` = 0 AND `is_deleted` = 0 LIMIT 5000;
-- 重复执行直到 affected rows = 0

RENAME TABLE `sys_role` TO `tenant_role`;

-- ══════════════════════════════════════════════
-- Step 6: 创建 tenant_user_role（三元关联表）
-- ══════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `tenant_user_role` ( ... );  -- 见 02-database-design.md §1.8

-- 迁移现有 sys_user_role 数据 → tenant_user_role（归入天南大陆）
INSERT INTO `tenant_user_role` (`user_id`, `tenant_id`, `role_id`)
SELECT `user_id`, 1, `role_id`
FROM `sys_user_role`;

-- 验证计数匹配后，可将旧表保留为备份（不立即删除）
-- DROP TABLE `sys_user_role`;  -- 待回归验证通过后执行

-- ══════════════════════════════════════════════
-- Step 7: 创建 tenant_permission + tenant_role_permission
-- ══════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `tenant_permission` ( ... );      -- 见 02-database-design.md §1.7
CREATE TABLE IF NOT EXISTS `tenant_role_permission` ( ... ); -- 见 02-database-design.md §1.6

-- 初始化：天南大陆拥有所有 scope=tenant 的权限节点
INSERT INTO `tenant_permission` (`tenant_id`, `permission_id`)
SELECT 1, `id` FROM `platform_permission`
WHERE `scope` = 'tenant' AND `is_deleted` = 0;

-- 迁移 sys_role_permission → tenant_role_permission（仅针对租户角色）
INSERT INTO `tenant_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1, srp.`role_id`, srp.`permission_id`
FROM `sys_role_permission` srp
JOIN `tenant_role` r ON r.`id` = srp.`role_id`
WHERE r.`tenant_id` = 1;

-- 创建平台角色权限表（平台端权限体系，见 02-database-design.md §1.7-1.9）
CREATE TABLE IF NOT EXISTS `platform_role` ( ... );
CREATE TABLE IF NOT EXISTS `platform_user_role` ( ... );
CREATE TABLE IF NOT EXISTS `platform_role_permission` ( ... );

-- 迁移 sys_role_permission 中平台级角色 → platform_role_permission
-- （如旧系统中存在平台级角色，否则跳过）
INSERT INTO `platform_role_permission` (`role_id`, `permission_id`)
SELECT srp.`role_id`, srp.`permission_id`
FROM `sys_role_permission` srp
JOIN `tenant_role` r ON r.`id` = srp.`role_id`
WHERE r.`tenant_id` = 0;  -- tenant_id=0 表示平台级角色

-- ══════════════════════════════════════════════
-- Step 8: sys_operation_log 增加多租户字段，然后重命名为 operation_log
-- ══════════════════════════════════════════════
ALTER TABLE `sys_operation_log`
  ADD COLUMN `tenant_id`   BIGINT(20) DEFAULT NULL COMMENT '租户ID（NULL=平台操作）' AFTER `id`,
  ADD COLUMN `is_platform` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否平台操作' AFTER `tenant_id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- 现有日志归入天南大陆（分批）
UPDATE `sys_operation_log`
SET `tenant_id` = 1, `is_platform` = 0
WHERE `tenant_id` IS NULL
LIMIT 5000;
-- 重复执行直到 affected rows = 0

RENAME TABLE `sys_operation_log` TO `operation_log`;
```

### 3.2 user_db 完整执行顺序

```sql
-- Step 1: 创建 tenant_user（用户-租户多对多关联表）
CREATE TABLE IF NOT EXISTS `tenant_user` ( ... );   -- 见 02-database-design.md §2.1

-- 迁移：现有 app_user 加入天南大陆
INSERT INTO `tenant_user` (`user_id`, `tenant_id`, `status`, `join_source`)
SELECT `id`, 1, `status`, 'MIGRATE'
FROM `app_user`
WHERE `is_deleted` = 0;

-- Step 2-N: 各业务表增加 tenant_id，更新数据，然后重命名
ALTER TABLE `user_tag_category` ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0
  COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

ALTER TABLE `user_tag` ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0
  COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

ALTER TABLE `user_tag_relation` ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0
  COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- user_field_definition、user_field_value 如存在同理

-- 分批次更新
UPDATE `user_tag_category` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;
-- 重复直到 affected rows = 0
UPDATE `user_tag`          SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;
-- 重复直到 affected rows = 0
UPDATE `user_tag_relation` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;
-- 重复直到 affected rows = 0

-- 重建 user_tag_category 唯一索引（原 uk_name 不含 tenant_id，需重建）
ALTER TABLE `user_tag_category` DROP INDEX `uk_name`;
ALTER TABLE `user_tag_category` ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`);

-- 重命名业务表为规范命名
RENAME TABLE `user_tag_category` TO `tenant_tag_category`;
RENAME TABLE `user_tag`          TO `tenant_tag`;
RENAME TABLE `user_tag_relation` TO `tenant_user_tag`;
-- 如存在 user_field_definition、user_field_value：
-- RENAME TABLE `user_field_definition` TO `tenant_field_def`;
-- RENAME TABLE `user_field_value`       TO `tenant_field_value`;
```

### 3.3 数据验证 SQL（迁移后必须执行）

```sql
-- ── admin_db 验证 ──
-- 1. 租户记录
SELECT COUNT(*) AS tenant_count FROM `platform_tenant`;  -- 应 = 1

-- 2. platform_user 数量与 sys_user 一致
SELECT
  (SELECT COUNT(*) FROM `platform_user`) AS pu_count,
  (SELECT COUNT(*) FROM `sys_user`)      AS su_count;    -- 应相等

-- 3. tenant_role 全部有 tenant_id
SELECT COUNT(*) AS roles_without_tenant FROM `tenant_role`
WHERE `tenant_id` = 0 AND `is_deleted` = 0;  -- 应 = 0

-- 4. tenant_user_role 迁移完整
SELECT COUNT(*) AS original FROM `sys_user_role`;
SELECT COUNT(*) AS migrated  FROM `tenant_user_role`;    -- 应 ≥ original

-- 5. tenant_permission 初始化
SELECT COUNT(*) AS tp_count FROM `tenant_permission` WHERE `tenant_id` = 1;
-- 应 = SELECT COUNT(*) FROM platform_permission WHERE scope='tenant' AND is_deleted=0

-- 6. tenant_role_permission 迁移
SELECT COUNT(*) AS trp FROM `tenant_role_permission` WHERE `tenant_id` = 1;
-- 参考 sys_role_permission 中 tenant_id=1 角色的记录数

-- 7. operation_log 迁移完整（无 tenant_id=NULL 残留）
SELECT COUNT(*) FROM `operation_log` WHERE `tenant_id` IS NULL;  -- 应 = 0

-- ── user_db 验证 ──
-- 8. tenant_user 与 app_user 数量匹配
SELECT
  (SELECT COUNT(*) FROM `tenant_user`)                      AS tu_count,
  (SELECT COUNT(*) FROM `app_user` WHERE is_deleted=0)      AS au_count;  -- 应相等

-- 9. 无 tenant_id=0 的遗留记录
SELECT COUNT(*) FROM `tenant_tag_category` WHERE `tenant_id` = 0;  -- 应 = 0
SELECT COUNT(*) FROM `tenant_tag`          WHERE `tenant_id` = 0;  -- 应 = 0
SELECT COUNT(*) FROM `tenant_user_tag`     WHERE `tenant_id` = 0;  -- 应 = 0

-- 10. 唯一索引验证（uk_tenant_name 生效）
SELECT `tenant_id`, `name`, COUNT(*) AS cnt
FROM `tenant_tag_category`
GROUP BY `tenant_id`, `name`
HAVING cnt > 1;  -- 应返回 0 行
```

---

## 4. 权限标识迁移（`sys:xxx` → 新格式）

### 4.1 映射表

**旧标识迁移（`sys:xxx` → 新格式）**

| 旧标识 | 新标识 | 说明 |
|--------|--------|------|
| `sys:user:list`   | `platform:user:list`   | 平台用户管理（非 app_user） |
| `sys:user:add`    | `platform:user:add`    | |
| `sys:user:edit`   | `platform:user:edit`   | |
| `sys:user:remove` | `platform:user:remove` | |
| `sys:user:reset`  | `platform:user:reset`  | |
| `sys:user:query`  | `platform:user:query`  | |
| `sys:role:list`   | `tenant:role:list`     | 角色管理属租户功能 |
| `sys:role:add`    | `tenant:role:add`      | |
| `sys:role:edit`   | `tenant:role:edit`     | |
| `sys:role:remove` | `tenant:role:remove`   | |
| `sys:perm:list`   | `platform:perm:list`   | 权限节点属平台功能 |
| `sys:perm:add`    | `platform:perm:add`    | |
| `sys:log:list`    | `platform:log:list`    | 平台操作日志 |

**新增权限节点（无旧标识，通过初始化 SQL 直接插入 `platform_permission`，见 03-permission-system.md §7）**

| 新标识 | scope | 说明 |
|--------|-------|------|
| `platform:role:list/add/edit/remove` | platform | 平台角色管理（新功能） |
| `tenant:member:list/add/remove/disable/role` | tenant | 租户成员管理（新功能） |
| `tenant:appuser:export` | tenant | 导出用户数据（新增操作） |
| `tenant:tag:category` | tenant | 管理标签分类（新增操作） |
| `tenant:field:list/add/edit/remove` | tenant | 用户中心字段管理（新功能） |
| `tenant:log:list` | tenant | 租户操作日志（新功能） |
| `platform:tenant:permission` | platform | 配置租户权限范围（新功能） |

### 4.2 执行 SQL

> **注意**：此步在 Step 3 的 `RENAME TABLE sys_permission → platform_permission` **之前**执行（即还叫 `sys_permission` 时）。若已重命名，则将下方表名改为 `platform_permission`。

```sql
-- 批量更新权限标识（admin_db，表尚未重命名时执行用 sys_permission；已重命名用 platform_permission）
UPDATE `sys_permission` SET `permission_key` = 'platform:user:list',   `scope` = 'platform' WHERE `permission_key` = 'sys:user:list';
UPDATE `sys_permission` SET `permission_key` = 'platform:user:add',    `scope` = 'platform' WHERE `permission_key` = 'sys:user:add';
UPDATE `sys_permission` SET `permission_key` = 'platform:user:edit',   `scope` = 'platform' WHERE `permission_key` = 'sys:user:edit';
UPDATE `sys_permission` SET `permission_key` = 'platform:user:remove', `scope` = 'platform' WHERE `permission_key` = 'sys:user:remove';
UPDATE `sys_permission` SET `permission_key` = 'platform:user:reset',  `scope` = 'platform' WHERE `permission_key` = 'sys:user:reset';
UPDATE `sys_permission` SET `permission_key` = 'tenant:role:list',     `scope` = 'tenant'   WHERE `permission_key` = 'sys:role:list';
UPDATE `sys_permission` SET `permission_key` = 'tenant:role:add',      `scope` = 'tenant'   WHERE `permission_key` = 'sys:role:add';
UPDATE `sys_permission` SET `permission_key` = 'tenant:role:edit',     `scope` = 'tenant'   WHERE `permission_key` = 'sys:role:edit';
UPDATE `sys_permission` SET `permission_key` = 'tenant:role:remove',   `scope` = 'tenant'   WHERE `permission_key` = 'sys:role:remove';
UPDATE `sys_permission` SET `permission_key` = 'platform:perm:list',   `scope` = 'platform' WHERE `permission_key` = 'sys:perm:list';
UPDATE `sys_permission` SET `permission_key` = 'platform:log:list',    `scope` = 'platform' WHERE `permission_key` = 'sys:log:list';
-- 根据实际 sys_permission 数据补充
```

### 4.3 后端 @PreAuthorize 注解同步更新

权限标识变更后，所有 Controller 的 `@PreAuthorize` 注解需要同步修改：

```bash
# 批量查找需要修改的位置
grep -r "hasAuthority('sys:" services/ --include="*.java"
```

---

## 5. Phase 2：后端服务改造（约 4 周）

### 5.1 admin-service 改造清单

**Week 1（核心基础设施）**

- [ ] `TenantContext.java`（ThreadLocal 工具类）
- [ ] `MybatisPlusConfig.java`（租户插件 + 分页插件，ignoreTable 白名单使用新表名）
- [ ] `JwtUtil.java`（增加 tenantId、isPlatform、tenantVersion 字段支持）
- [ ] `JwtAuthenticationFilter.java`（注入 TenantContext，校验 tenant data_version）
- [ ] `TenantContextFilter.java`（内部服务调用时从 Header 注入）
- [ ] `TenantFeignInterceptor.java`（Feign 调用 user-service 时透传 tenant_id）

**Week 2（认证接口）**

- [ ] `PlatformAuthController`（登录、登出、刷新 token）
- [ ] `TenantAuthController`（登录、选择租户、切换租户、登出、刷新）
- [ ] `PlatformUserService / Controller`（platform_user CRUD）
- [ ] `PlatformTenantService / Controller`（platform_tenant CRUD + 权限配置）

**Week 3（租户端业务）**

- [ ] `TenantRole` Entity/Mapper 适配 tenant_id（插件自动过滤，几乎不用改）
- [ ] `TenantRolePermissionMapper`（替代 sys_role_permission，带权限范围校验）
- [ ] `TenantUserRoleMapper`（三元关联 CRUD，操作 tenant_user_role 表）
- [ ] `TenantMemberController`（成员管理：分配角色、禁用、移除）
- [ ] 操作日志 AOP 改造（自动填充 tenant_id / is_platform，写入 operation_log 表）

**Week 4（权限缓存 + 完善）**

- [ ] `TenantPermissionCacheService`（Redis 权限缓存 + 失效逻辑）
- [ ] `TenantPermissionController`（平台端为租户授权权限节点）
- [ ] 租户禁用 token 失效机制（data_version 校验 + Redis 缓存）
- [ ] token 黑名单（登出后 jti 存 Redis）
- [ ] 全部 `@PreAuthorize` 注解更新（见 §4.3）

### 5.2 user-service 改造清单

- [ ] 复制/引入 `TenantContext.java`（与 admin-service 相同实现）
- [ ] `MybatisPlusConfig.java`（租户插件配置，ignoreTable 白名单同 admin-service）
- [ ] `InternalTenantFilter.java`（从 X-Tenant-Id Header 注入 TenantContext）
- [ ] `TenantUserMapper / Service`（tenant_user 表的 CRUD）
- [ ] 现有接口无需改代码（插件自动注入 tenant_id 过滤，表已重命名为新规范名）

### 5.3 向后兼容处理（过渡期）

```java
// JwtAuthenticationFilter 中：旧 JWT 无 tenantId 字段，默认天南大陆
Long tenantId = jwtUtil.getTenantId(token);
if (tenantId == null && !jwtUtil.isPlatformUser(token)) {
    tenantId = 1L;  // 向后兼容：默认天南大陆
    log.warn("旧格式 JWT，userId={} 使用默认 tenantId=1", jwtUtil.getUserId(token));
}
TenantContext.setTenantId(tenantId);
```

---

## 6. Phase 3：前端改造（约 4 周）

### 6.1 目录重组（首先执行，影响后续所有开发）

```bash
# 创建目录结构
mkdir -p src/platform/pages src/platform/router src/platform/store src/platform/layout
mkdir -p src/tenant
mkdir -p src/shared/components src/shared/hooks src/shared/utils src/shared/types src/shared/styles

# 将现有 src 内容迁移到 src/tenant（注意更新所有 import 路径）
# 将通用工具迁移到 src/shared

# 全局替换 import 路径（可借助 VSCode 批量重构）
```

### 6.2 各阶段任务

**Week 1-2：基础框架**
- [ ] 目录重组，更新所有 import 路径（可用 IDE 重构工具）
- [ ] Vite 多入口配置 + platform.html
- [ ] 双 axios 实例（tenantInstance / platformInstance）
- [ ] 平台端 router 骨架 + PlatformGuard
- [ ] 租户端 TenantGuard 改造（增加 tenantId 检查）
- [ ] storage.ts 增加 platform token key

**Week 3-4：平台端页面**
- [ ] 平台端登录页
- [ ] 平台端 Layout（侧边栏 + 面包屑 + Header）
- [ ] 租户管理页（列表 + 创建 + 权限配置抽屉）
- [ ] 平台用户管理页

**Week 5-6：租户端适配**
- [ ] 登录页改造（loginType 分流逻辑）
- [ ] 登录页内嵌租户选择步骤（TenantSelectStep 组件，无独立路由，URL 始终保持 /login）
- [ ] Header 头像下拉含租户切换区块
- [ ] AuthButton + usePermission Hook
- [ ] 菜单动态过滤

**Week 7-8：补全功能**
- [ ] 角色管理页（权限树抽屉）
- [ ] 成员管理页（角色分配抽屉）
- [ ] 操作日志页（平台/租户分别）

---

## 7. Phase 4：上线（约 1 周）

### 7.1 上线顺序

```
1. 数据库迁移（已在 Phase 1 完成，生产执行）
2. admin-service 部署新版本（含向后兼容逻辑）
3. user-service 部署新版本
4. 平台端前端上线（/platform 路径，不影响现有租户端）
5. 租户端前端上线（更新现有页面，包含租户切换等新功能）
6. 逐步下线兼容代码（旧 JWT 向后兼容 tenantId=1 的逻辑，在全部 token 过期后去除）
```

### 7.2 上线前验证清单

**数据层验证**（见 §3.3 验证 SQL）
- [ ] admin_db 所有验证 SQL 结果符合预期
- [ ] user_db 所有验证 SQL 结果符合预期

**功能验证**
- [ ] 平台管理员登录 `/platform/login` 成功，菜单正常
- [ ] 租户用户登录（单租户直进 / 多租户显示选择页）
- [ ] 切换租户后数据完全切换，看不到其他租户数据
- [ ] 角色权限分配不超出租户授权范围（提交超出范围的权限节点返回错误）
- [ ] 禁用租户后，该租户所有用户下次请求被拒绝（401）
- [ ] 修改平台用户密码后，旧 token 失效

**安全验证**
- [ ] 租户用户访问 `/api/platform/` 接口返回 403
- [ ] 手动构造携带其他 tenantId 的 JWT 无法越权访问
- [ ] preToken 超时（5min）后无法使用

**性能基线**
- [ ] 单租户 10000 条用户数据下，列表接口 < 200ms
- [ ] 权限缓存命中后，接口响应时间不因权限查询增加

---

## 8. 回滚方案

### 8.1 数据库回滚

> **注意**：表已通过 RENAME TABLE 更名，回滚时需反向重命名，且只有在无其他租户数据时才能安全回滚。

```sql
-- admin_db 回滚（按逆序执行）
DROP TABLE IF EXISTS `tenant_role_permission`;
DROP TABLE IF EXISTS `tenant_permission`;
DROP TABLE IF EXISTS `tenant_user_role`;
DROP TABLE IF EXISTS `platform_role_permission`;
DROP TABLE IF EXISTS `platform_user_role`;
DROP TABLE IF EXISTS `platform_role`;

-- 恢复 tenant_role → sys_role（仅在无其他租户数据时安全）
ALTER TABLE `tenant_role` DROP COLUMN `tenant_id`, DROP INDEX `idx_tenant_id`;
RENAME TABLE `tenant_role` TO `sys_role`;

-- 恢复 platform_permission → sys_permission
ALTER TABLE `platform_permission` DROP COLUMN `scope`;
RENAME TABLE `platform_permission` TO `sys_permission`;

-- 恢复 operation_log → sys_operation_log
ALTER TABLE `operation_log` DROP COLUMN `tenant_id`, DROP COLUMN `is_platform`, DROP INDEX `idx_tenant_id`;
RENAME TABLE `operation_log` TO `sys_operation_log`;

-- user_db 回滚
DROP TABLE IF EXISTS `tenant_user`;

RENAME TABLE `tenant_tag_category` TO `user_tag_category`;
RENAME TABLE `tenant_tag`          TO `user_tag`;
RENAME TABLE `tenant_user_tag`     TO `user_tag_relation`;

ALTER TABLE `user_tag_category` DROP COLUMN `tenant_id`, DROP INDEX `idx_tenant_id`;
ALTER TABLE `user_tag_category` ADD UNIQUE KEY `uk_name` (`name`);
ALTER TABLE `user_tag`          DROP COLUMN `tenant_id`, DROP INDEX `idx_tenant_id`;
ALTER TABLE `user_tag_relation` DROP COLUMN `tenant_id`, DROP INDEX `idx_tenant_id`;
```

> **注意**：`platform_user` 和 `platform_tenant` 不回滚，`sys_user` 始终保留。

### 8.2 服务回滚

通过 feature flag 快速关闭多租户逻辑（无需重新部署）：

```yaml
# application.yml
feature:
  multi-tenant:
    enabled: true   # 改为 false 则所有接口跳过租户过滤，回退到单租户模式
```

```java
// TenantLineHandler.ignoreTable 中判断 flag
@Override
public boolean ignoreTable(String tableName) {
    if (!featureProperties.isMultiTenantEnabled()) return true; // 全部忽略
    // ... 正常逻辑
}
```

---

## 9. 风险点汇总

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `sys_user_role` 迁移不完整（有遗漏行）| 用户登录后无权限 | 迁移后对比计数（§3.3），生产迁移前在测试环境验证 |
| 权限标识 `sys:xxx` 未全部更新 | 菜单/按钮权限校验失败 | 用 `grep -r "sys:"` 全面扫描代码，迁移前建立完整映射表 |
| MyBatis-Plus 插件与 JOIN 查询冲突 | SQL 错误 / 数据越权 | 对所有含 JOIN 的 Mapper 方法做集成测试，必要时加 `@InterceptorIgnore` |
| tenant_tag_category 唯一索引冲突（同名分类）| DDL 执行失败 | 先执行 `SELECT name, COUNT(*) FROM user_tag_category GROUP BY name HAVING COUNT(*) > 1` 检查重复，处理后再改索引 |
| 大表 UPDATE 锁表导致服务不可用 | 服务卡顿/超时 | 分批次（LIMIT 5000）+ 低峰期执行，观察 `SHOW PROCESSLIST` |
| RENAME TABLE 后旧代码引用旧表名 | SQL 报"Table not found" | 后端代码改造（Mapper Entity 更名）必须在 RENAME TABLE 之前或同步部署 |
| 跨服务调用未传 X-Tenant-Id | 跨服务查询返回空数据 | admin-service → user-service 的调用全部走 Feign，在 Feign 拦截器统一注入 |
| preToken 被截获重放 | 攻击者选择任意租户登录 | Redis 标记已用（一次性），有效期 5 分钟，生产环境强制 HTTPS |
| 租户成员移除后 token 未立即失效 | 被移除的用户仍可操作 | 可接受（现有 token 在 2h 内仍有效）；高安全要求场景可升级为：移除时给该用户 token 打黑名单标记 |

---

## 10. 2026-03-18 增量实施记录

### 10.1 已执行数据库脚本

本次已执行并验证以下脚本：

- `services/admin-service/sql/v5_log_domain_cleanup.sql`
- `services/admin-service/sql/v6_tenant_test_data.sql`

说明：本轮按“直接收敛目标模型”执行，不保留历史双权限兼容迁移。

### 10.2 已完成系统收敛项

- 权限域分离：平台与租户日志权限彻底拆分
- API 收敛：租户日志切换到 `/api/tenant/logs`，`/api/logs` 下线
- 网关治理：动态 DNS 重解析避免容器重建后的 502
- 前端口径统一：日志耗时字段按 `costTime` 展示，权限树交互规则统一

### 10.3 验收建议清单

- 平台登录成功（`/api/platform/auth/login`）
- 租户登录成功（`/api/tenant/auth/login`）
- 平台日志接口 200（携带有效平台 token）
- 租户日志接口 200（携带有效租户 token）
- 旧日志路径 `/api/logs` 返回 404
