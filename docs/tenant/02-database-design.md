# 02 数据库设计

## 0. 表命名规范

| 前缀 | 归属 | 示例 |
|------|------|------|
| `platform_` | 平台层：平台管理员、租户元数据、全局权限节点、平台角色 | `platform_user`, `platform_tenant` |
| `tenant_` | 租户层：租户角色、租户权限授权、用户-租户成员关系、租户数据 | `tenant_role`, `tenant_user` |
| `app_` | 应用用户层：全局用户账号（跨租户） | `app_user` |
| `operation_log` | 操作日志（统一表，含 is_platform 区分） | `operation_log` |

---

## 1. admin_db 新增表

### 1.1 平台租户表 `platform_tenant`

```sql
CREATE TABLE `platform_tenant` (
  `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `tenant_code`  VARCHAR(50)  NOT NULL                COMMENT '租户编码（唯一，URL友好，仅字母数字下划线）',
  `tenant_name`  VARCHAR(100) NOT NULL                COMMENT '租户名称',
  `logo`         VARCHAR(255) DEFAULT NULL            COMMENT 'Logo URL',
  `description`  VARCHAR(500) DEFAULT NULL            COMMENT '租户描述',
  `contact_name` VARCHAR(50)  DEFAULT NULL            COMMENT '联系人',
  `contact_phone`VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
  `contact_email`VARCHAR(100) DEFAULT NULL            COMMENT '联系邮箱',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '状态: 1启用 0禁用',
  `expire_time`  DATETIME     DEFAULT NULL            COMMENT '到期时间（null=永久）',
  `max_users`    INT(11)      DEFAULT NULL            COMMENT '最大用户数（null=不限）',
  `data_version` BIGINT(20)   NOT NULL DEFAULT 0      COMMENT 'Token版本号，禁用租户时+1，令旧token立即失效',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`    VARCHAR(50)  DEFAULT NULL,
  `update_by`    VARCHAR(50)  DEFAULT NULL,
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台租户表';

-- 初始化：历史数据归入"天南大陆"
INSERT INTO `platform_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `data_version`, `create_by`)
VALUES (1, 'tiannan', '天南大陆', 1, 0, 'system');
```

> `data_version`：平台禁用租户时此字段 +1，JWT 中携带签发时的 `tenantVersion`，每次请求校验两者是否一致，不一致则拒绝请求，实现租户禁用后已颁发 token 立即失效。

---

### 1.2 平台用户表 `platform_user`

> 平台管理员与租户用户（`app_user`）完全隔离，互不干扰，登录入口不同。
> 超级管理员身份由所属角色（`platform_role.is_super=1`）决定，`platform_user` 本身不存储超管标志。
> `is_builtin=1` 表示内置账号（系统初始化时创建），不可删除、不可禁用、不可修改角色。

```sql
CREATE TABLE `platform_user` (
  `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(50)  NOT NULL               COMMENT '登录用户名',
  `password`        VARCHAR(100) NOT NULL               COMMENT '密码（BCrypt加密）',
  `nickname`        VARCHAR(50)  NOT NULL               COMMENT '昵称',
  `mobile`          VARCHAR(20)  DEFAULT NULL           COMMENT '手机号',
  `email`           VARCHAR(50)  DEFAULT NULL           COMMENT '邮箱',
  `is_builtin`      TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否内置账号: 1=内置（不可删除/禁用/修改角色）',
  `status`          TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '状态: 1启用 0禁用',
  `token_version`   INT(11)      NOT NULL DEFAULT 0     COMMENT 'Token版本号，修改密码或禁用时+1，令旧token立即失效',
  `last_login_time` DATETIME     DEFAULT NULL,
  `last_login_ip`   VARCHAR(50)  DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`       VARCHAR(50)  DEFAULT NULL,
  `update_by`       VARCHAR(50)  DEFAULT NULL,
  `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台管理员用户表';
```

---

### 1.3 平台角色表 `platform_role`

> 平台端角色体系，用于管理平台管理员的权限范围（如"运营管理员"只能查看日志）。
> `is_super=1` 标识超级管理员角色：持有此角色的平台用户拥有全部平台权限，后端跳过权限校验，前端展示所有权限节点为已勾选（仅展示效果）。
> `is_builtin=1` 标识内置角色：不可删除、不可修改权限配置（`is_super=1` 隐含 `is_builtin=1`）。

```sql
CREATE TABLE `platform_role` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name`   VARCHAR(50)  NOT NULL               COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL               COMMENT '角色标识（唯一，如 super_admin）',
  `is_builtin`  TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否内置角色: 1=内置（不可删除/修改）',
  `is_super`    TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否超管角色: 1=持有此角色的用户拥有全部权限',
  `description` VARCHAR(200) DEFAULT NULL           COMMENT '描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`   VARCHAR(50)  DEFAULT NULL,
  `update_by`   VARCHAR(50)  DEFAULT NULL,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色表';
```

---

### 1.4 平台用户-角色关联表 `platform_user_role`（新增）

```sql
CREATE TABLE `platform_user_role` (
  `id`          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT(20)  NOT NULL               COMMENT '平台用户ID（platform_user.id）',
  `role_id`     BIGINT(20)  NOT NULL               COMMENT '平台角色ID（platform_role.id）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户-角色关联表';

-- 迁移：将现有 sys_user_role 中平台用户的角色关联复制过来
-- （此时 sys_user_role.user_id 对应 sys_user = platform_user，role_id 对应 platform_role）
INSERT INTO `platform_user_role` (`user_id`, `role_id`)
SELECT `user_id`, `role_id` FROM `sys_user_role`;
```

---

### 1.5 平台角色-权限关联表 `platform_role_permission`（新增，原 `sys_role_permission`）

```sql
CREATE TABLE `platform_role_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id`       BIGINT(20)  NOT NULL               COMMENT '平台角色ID（platform_role.id）',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（platform_permission.id，scope=platform）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色-权限关联表';

-- 初始化数据见 §1.9
```

---

### 1.6 租户角色-权限关联表 `tenant_role_permission`（新增）

> 专用于租户角色与权限节点的映射，所选权限节点必须在 `tenant_permission` 授权范围内。

```sql
CREATE TABLE `tenant_role_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT(20)  NOT NULL               COMMENT '租户ID（冗余，便于按租户查询和缓存失效）',
  `role_id`       BIGINT(20)  NOT NULL               COMMENT '租户角色ID（tenant_role.id）',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（必须在 tenant_permission 授权范围内）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_tenant_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色-权限关联表';

-- 初始化数据见 §1.9
```

---

### 1.7 租户可用权限节点表 `tenant_permission`（新增）

> 平台为每个租户授权可以使用的权限节点范围。租户分配角色权限时，只能在此范围内选择。

```sql
CREATE TABLE `tenant_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT(20)  NOT NULL               COMMENT '租户ID',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（platform_permission.id，scope=tenant）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by`     VARCHAR(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_permission` (`tenant_id`, `permission_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户可用权限节点（平台为租户授权的权限上限）';

-- 迁移：天南大陆默认拥有所有 scope=tenant 的权限节点
INSERT INTO `tenant_permission` (`tenant_id`, `permission_id`)
SELECT 1, `id`
FROM `platform_permission`
WHERE `scope` = 'tenant' AND `is_deleted` = 0;
```

---

### 1.8 用户-租户-角色关联表 `tenant_user_role`（新增，原 `user_tenant_role`）

> **核心设计**：原 `sys_user_role` 是二元表 `(user_id, role_id)`，无法表达「用户在租户A是角色X、在租户B是角色Y」。废弃它，改用三元表。

```sql
CREATE TABLE `tenant_user_role` (
  `id`          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT(20)  NOT NULL               COMMENT '用户ID（app_user.id）',
  `tenant_id`   BIGINT(20)  NOT NULL               COMMENT '租户ID（platform_tenant.id）',
  `role_id`     BIGINT(20)  NOT NULL               COMMENT '角色ID（tenant_role.id，需保证role.tenant_id=本表tenant_id）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_role` (`user_id`, `tenant_id`, `role_id`),
  KEY `idx_user_tenant` (`user_id`, `tenant_id`),
  KEY `idx_tenant_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-租户-角色三元关联表';

```

> **约束**：`role_id` 对应的 `tenant_role.tenant_id` 必须等于本表 `tenant_id`，在业务层校验，不使用 FK（避免跨库/跨服务约束）。

---

### 1.9 内置数据初始化

> 系统启动时必须执行，创建内置超管角色和内置管理员账号。完整 SQL 见 `sql/init_data.sql`。

```sql
-- ── 内置超级管理员角色 ──
INSERT INTO `platform_role` (`id`, `role_name`, `role_key`, `is_builtin`, `is_super`, `description`)
VALUES (1, '超级管理员', 'super_admin', 1, 1, '内置超管角色，不可删除/修改，持有此角色的用户拥有全部平台权限');

-- ── 内置 admin 账号（密码 123456 的 BCrypt 值）──
INSERT INTO `platform_user` (`id`, `username`, `password`, `nickname`, `is_builtin`, `status`)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超级管理员', 1, 1);

-- ── 将 admin 账号绑定到超管角色 ──
INSERT INTO `platform_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ── 默认租户（天南大陆） ──
INSERT INTO `platform_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `data_version`)
VALUES (1, 'default', '默认租户', 1, 0);

-- ── 为默认租户授权所有 scope=tenant 的权限节点 ──
INSERT INTO `tenant_permission` (`tenant_id`, `permission_id`)
SELECT 1, `id` FROM `platform_permission` WHERE `scope` = 'tenant' AND `is_deleted` = 0;
```

**内置角色规则**：
- `is_builtin=1` 的角色：不可删除、不可修改 `role_name`/`role_key`/`is_super`/`is_builtin`
- 超管角色的权限勾选仅为前端展示效果（展示为全勾选），后端不依赖 `platform_role_permission` 做校验

**内置用户规则**：
- `is_builtin=1` 的用户：不可删除、不可禁用、不可修改其角色分配
- 可修改密码、昵称等非关键字段

---

## 2. admin_db 修改现有表

### 2.1 `sys_role` 重命名为 `tenant_role`，增加 `tenant_id`

```sql
-- 先重命名
RENAME TABLE `sys_role` TO `tenant_role`;

-- 增加 tenant_id 字段
ALTER TABLE `tenant_role`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0
    COMMENT '租户ID（对应 platform_tenant.id）' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- 现有角色全部归入"天南大陆"
UPDATE `tenant_role` SET `tenant_id` = 1
WHERE `tenant_id` = 0 AND `is_deleted` = 0;
```

---

### 2.2 `sys_permission` 重命名为 `platform_permission`，增加 `scope` 字段

> 权限节点由平台统一维护，全局唯一，租户只能使用不能修改。

```sql
-- 重命名
RENAME TABLE `sys_permission` TO `platform_permission`;

-- 增加 scope 字段
ALTER TABLE `platform_permission`
  ADD COLUMN `scope` VARCHAR(20) NOT NULL DEFAULT 'tenant'
    COMMENT '权限范围: platform=仅平台端可用 tenant=可授权给租户' AFTER `type`;

-- 更新 scope（根据权限标识前缀判断）
UPDATE `platform_permission` SET `scope` = 'platform' WHERE `permission_key` LIKE 'platform:%';
UPDATE `platform_permission` SET `scope` = 'tenant'   WHERE `permission_key` LIKE 'tenant:%';

-- 将现有 sys:xxx 格式权限标识更新为新格式（详见 06-migration-plan.md §4）
-- 示例：
UPDATE `platform_permission` SET `permission_key` = 'platform:user:list',   `scope` = 'platform' WHERE `permission_key` = 'sys:user:list';
UPDATE `platform_permission` SET `permission_key` = 'tenant:role:list',     `scope` = 'tenant'   WHERE `permission_key` = 'sys:role:list';
-- ... 完整映射见迁移计划
```

**`platform_permission` 完整字段说明**（增加 scope 后）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `parent_id` | BIGINT | 父节点ID，0=顶级 |
| `name` | VARCHAR(50) | 节点显示名称 |
| `type` | VARCHAR(20) | 节点类型：DIR/MENU/BTN |
| `scope` | VARCHAR(20) | **新增**：platform=平台专属，tenant=可授权给租户 |
| `permission_key` | VARCHAR(100) | 权限标识（如 `tenant:role:add`） |
| `path` | VARCHAR(200) | 路由路径（MENU类型有值） |
| `component` | VARCHAR(200) | 前端组件路径 |
| `sort` | INT | 排序号 |
| `is_deleted` | TINYINT | 逻辑删除 |

---

### 2.3 操作日志表：`sys_operation_log` 重命名并增加多租户字段

```sql
-- 重命名（统一日志表，通过 is_platform 区分来源）
RENAME TABLE `sys_operation_log` TO `operation_log`;

-- 增加多租户字段
ALTER TABLE `operation_log`
  ADD COLUMN `tenant_id`   BIGINT(20)  DEFAULT NULL
    COMMENT '租户ID（NULL=平台操作）' AFTER `id`,
  ADD COLUMN `is_platform` TINYINT(1)  NOT NULL DEFAULT 0
    COMMENT '是否平台操作: 1=平台用户操作 0=租户用户操作' AFTER `tenant_id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`),
  ADD INDEX `idx_is_platform` (`is_platform`);

-- 现有日志归入天南大陆
UPDATE `operation_log`
SET `tenant_id` = 1, `is_platform` = 0
WHERE `tenant_id` IS NULL
LIMIT 5000;
-- 重复执行直到 ROW_COUNT() = 0
```

**日志隔离规则**：

| 操作来源 | `is_platform` | `tenant_id` | 查询入口 |
|---------|--------------|-------------|---------|
| 平台管理员操作 | 1 | NULL | `GET /api/platform/logs` |
| 租户用户操作 | 0 | {tenantId} | `GET /api/tenant/logs` |

---

## 3. user_db 新增表

### 3.1 用户-租户成员关系表 `tenant_user`（原 `user_tenant`）

```sql
CREATE TABLE `tenant_user` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT(20)   NOT NULL               COMMENT '用户ID（app_user.id）',
  `tenant_id`   BIGINT(20)   NOT NULL               COMMENT '租户ID（platform_tenant.id）',
  `status`      TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '成员状态: 1正常 0禁用',
  `join_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `join_source` VARCHAR(20)  NOT NULL DEFAULT 'MIGRATE'
    COMMENT '加入方式: MIGRATE=数据迁移 INVITE=邀请 REGISTER=注册 IMPORT=批量导入',
  `expire_time` DATETIME     DEFAULT NULL           COMMENT '成员到期时间（null=永久）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-租户成员关系表（一用户可属于多个租户）';

-- 迁移：现有所有 app_user 加入"天南大陆"
INSERT INTO `tenant_user` (`user_id`, `tenant_id`, `status`, `join_source`)
SELECT `id`, 1, `status`, 'MIGRATE'
FROM `app_user`
WHERE `is_deleted` = 0;
```

---

## 4. user_db 修改现有表

### 4.1 需要增加 `tenant_id` 的表

> 所有租户数据表需要加 `tenant_id`，MyBatis-Plus 租户插件自动注入过滤条件。

```sql
-- ──── user_tag_category → tenant_tag_category ────
RENAME TABLE `user_tag_category` TO `tenant_tag_category`;
ALTER TABLE `tenant_tag_category`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`),
  DROP INDEX `uk_name`,
  ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`);   -- 同租户内名称唯一
UPDATE `tenant_tag_category` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;

-- ──── user_tag → tenant_tag ────
RENAME TABLE `user_tag` TO `tenant_tag`;
ALTER TABLE `tenant_tag`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `tenant_tag` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;

-- ──── user_tag_relation → tenant_user_tag ────
RENAME TABLE `user_tag_relation` TO `tenant_user_tag`;
ALTER TABLE `tenant_user_tag`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `tenant_user_tag` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;

-- ──── user_field_definition → tenant_field_def（如存在）────
RENAME TABLE `user_field_definition` TO `tenant_field_def`;
ALTER TABLE `tenant_field_def`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`),
  DROP INDEX `uk_field_key`,                            -- 旧唯一索引（如有）
  ADD UNIQUE KEY `uk_tenant_field_key` (`tenant_id`, `field_key`);
UPDATE `tenant_field_def` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;

-- ──── user_field_value → tenant_field_value（如存在）────
RENAME TABLE `user_field_value` TO `tenant_field_value`;
ALTER TABLE `tenant_field_value`
  ADD COLUMN `tenant_id` BIGINT(20) NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_tenant_id` (`tenant_id`);
UPDATE `tenant_field_value` SET `tenant_id` = 1 WHERE `tenant_id` = 0 LIMIT 5000;
```

---

## 5. 废弃表处理

| 旧表名 | 处理方式 | 原因 |
|--------|---------|------|
| `sys_user` | 保留至迁移验证完成后删除 | 数据已迁移至 `platform_user` |
| `sys_role` | 已 RENAME 为 `tenant_role` | |
| `sys_permission` | 已 RENAME 为 `platform_permission` | |
| `sys_operation_log` | 已 RENAME 为 `operation_log` | |
| `sys_user_role` | 数据迁移至 `tenant_user_role`，迁移验证后删除 | 由三元表替代 |
| `sys_role_permission` | 数据分别迁移至 `platform_role_permission` 和 `tenant_role_permission`，验证后删除 | |

---

## 6. 完整 ER 图（改造后）

```
admin_db:
─────────────────────────────────────────────────────────────────
platform_user ──── N:M ──── platform_role ──── N:M ──── platform_permission
  (平台管理员)    platform   (平台角色)    platform_role_  (权限节点，scope=platform/tenant)
                 _user_role              permission
       │
       │ 管理（CRUD）
       ▼
platform_tenant ──── 1:N ──── tenant_permission ──── N:1 ──── platform_permission
  (租户信息)                  (租户可用权限授权)                (scope=tenant的节点)
       │
       │ tenant_id 关联
       ▼
tenant_role ──── N:M ──── platform_permission
  (租户角色)    tenant_role_  (scope=tenant的节点，在tenant_permission范围内)
               permission
       ▲
       │ N:M
tenant_user_role (user_id, tenant_id, role_id)
       │
       │ user_id
       ▼
(user_db) app_user ──── N:M ──── platform_tenant
             (全局用户账号)      tenant_user (成员关系)

user_db:
─────────────────────────────────────────────────────────────────
app_user ──── N:M ──── platform_tenant
              tenant_user (tenant_id, user_id, status)

app_user ──── N:M ──── tenant_tag
              tenant_user_tag (tenant_id, user_id, tag_id)

tenant_tag ──── N:1 ──── tenant_tag_category
  (均含 tenant_id)

app_user ──── 1:N ──── tenant_field_value
                           │ N:1
                       tenant_field_def (均含 tenant_id)
```

---

## 7. 完整表清单

### admin_db

| 表名 | 类型 | 说明 |
|------|------|------|
| `platform_user` | 实体表 | 平台管理员账号 |
| `platform_tenant` | 实体表 | 租户信息 |
| `platform_permission` | 实体表 | 全局权限节点树（含 scope 字段） |
| `platform_role` | 实体表 | 平台级角色（`is_super=1` 标识超管角色，`is_builtin=1` 标识内置不可改） |
| `platform_user_role` | 关联表 | 平台用户 ↔ 平台角色 |
| `platform_role_permission` | 关联表 | 平台角色 ↔ 权限节点（scope=platform） |
| `tenant_role` | 实体表 | 租户角色（含 tenant_id） |
| `tenant_permission` | 关联表 | 平台授权给租户的权限范围 |
| `tenant_role_permission` | 关联表 | 租户角色 ↔ 权限节点（在 tenant_permission 范围内） |
| `tenant_user_role` | 关联表 | 用户 ↔ 租户 ↔ 角色（三元关系） |
| `operation_log` | 实体表 | 统一操作日志（is_platform 区分来源） |

### user_db

| 表名 | 类型 | 说明 |
|------|------|------|
| `app_user` | 实体表 | 全局用户账号（跨租户） |
| `tenant_user` | 关联表 | 用户 ↔ 租户成员关系 |
| `tenant_tag_category` | 实体表 | 标签分类（含 tenant_id） |
| `tenant_tag` | 实体表 | 标签（含 tenant_id） |
| `tenant_user_tag` | 关联表 | 用户 ↔ 标签（含 tenant_id） |
| `tenant_field_def` | 实体表 | 租户自定义字段定义 |
| `tenant_field_value` | 关联表 | 用户自定义字段值 |

---

## 8. 索引策略

> `tenant_id` 必须放在联合索引**最左侧**，确保按租户过滤的查询命中索引。

```sql
-- tenant_role：按租户查角色
KEY `idx_tenant_status`  (`tenant_id`, `status`)             -- tenant_role

-- tenant_user_role：按用户+租户查角色（核心查询路径）
KEY `idx_user_tenant`    (`user_id`, `tenant_id`)            -- tenant_user_role
KEY `idx_tenant_role`    (`tenant_id`, `role_id`)            -- tenant_user_role

-- tenant_tag：按租户+分类查标签
KEY `idx_tenant_category`(`tenant_id`, `category_id`)        -- tenant_tag

-- operation_log：按租户和平台分别查日志
KEY `idx_tenant_create`  (`tenant_id`, `create_time`)        -- operation_log
KEY `idx_platform_create`(`is_platform`, `create_time`)      -- operation_log
```

---

## 9. MyBatis-Plus 多租户插件配置

### 9.1 租户上下文（ThreadLocal）

```java
// TenantContext.java（admin-service 和 user-service 各自维护一份，逻辑相同）
public class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { TENANT_ID.set(tenantId); }
    public static Long getTenantId() { return TENANT_ID.get(); }
    public static boolean isPlatform() { return TENANT_ID.get() == null; }
    public static void clear() { TENANT_ID.remove(); }
}
```

### 9.2 插件忽略表白名单（使用最新表名）

```java
// MybatisPlusConfig.java
private static final Set<String> IGNORE_TABLES = Set.of(
    // admin_db 平台级表（不做租户过滤）
    "platform_user",
    "platform_tenant",
    "platform_permission",
    "platform_role",
    "platform_user_role",
    "platform_role_permission",
    // admin_db 关联表（查询时自己带 tenant_id 条件，不走插件）
    "tenant_permission",
    "tenant_role_permission",
    "tenant_user_role",
    "operation_log",            // 日志有自己的过滤逻辑（is_platform + tenant_id）
    // user_db 关联表
    "tenant_user"               // 按 user_id 查，不按 tenant_id 过滤
);

@Override
public boolean ignoreTable(String tableName) {
    if (TenantContext.isPlatform()) return true; // 平台用户：所有表不过滤
    return IGNORE_TABLES.contains(tableName);
}
```

### 9.3 异步任务中的 ThreadLocal 传递

> `@Async` 开新线程，ThreadLocal 不自动继承，必须显式传递。

```java
// 正确做法：在提交任务前捕获当前 tenantId，在异步方法内重新设置
@Service
public class TenantAsyncService {
    @Async
    public void executeWithTenant(Long tenantId, Runnable task) {
        try {
            TenantContext.setTenantId(tenantId);
            task.run();
        } finally {
            TenantContext.clear();
        }
    }
}

// 调用方：
Long tenantId = TenantContext.getTenantId();
tenantAsyncService.executeWithTenant(tenantId, () -> {
    // 此处 TenantContext.getTenantId() 正常返回
    tagService.rebuildTagCount();
});
```

### 9.4 JWT 过滤器注入租户上下文

```java
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    String token = extractToken(request);
    if (token != null && jwtUtil.validateToken(token)) {

        boolean isPlatform = jwtUtil.isPlatformUser(token);

        if (!isPlatform) {
            Long tenantId = jwtUtil.getTenantId(token);
            Long tenantVersion = jwtUtil.getTenantVersion(token);

            // 校验租户状态（Redis 缓存 platform_tenant.data_version）
            validateTenantVersion(tenantId, tenantVersion);

            TenantContext.setTenantId(tenantId);
        }
        // 平台用户：不设置 TenantContext，插件 ignoreTable 返回 true，不过滤任何表
    }

    try {
        filterChain.doFilter(request, response);
    } finally {
        TenantContext.clear();
    }
}

private void validateTenantVersion(Long tenantId, Long tokenVersion) {
    String cacheKey = "tenant:version:" + tenantId;
    Long dbVersion = (Long) redisTemplate.opsForValue().get(cacheKey);
    if (dbVersion == null) {
        dbVersion = platformTenantMapper.getDataVersion(tenantId);
        redisTemplate.opsForValue().set(cacheKey, dbVersion, 10, TimeUnit.MINUTES);
    }
    if (!Objects.equals(tokenVersion, dbVersion)) {
        throw new BusinessException(401, "租户状态已变更，请重新登录");
    }
}
```
