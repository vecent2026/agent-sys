CREATE TABLE `user_views` (
  `id` VARCHAR(36) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '创建者用户ID（来自认证，如 admin 的 sys_user.id）',
  `filters` JSON NOT NULL,
  `hidden_fields` JSON NOT NULL,
  `view_config` JSON NULL COMMENT '视图配置(JSON)：列顺序/列宽/固定列等',
  `filter_logic` ENUM('AND', 'OR') NOT NULL DEFAULT 'AND',
  `is_default` BOOLEAN DEFAULT FALSE,
  `order_no` INT NOT NULL DEFAULT 0 COMMENT '视图序号，从 1 开始，越小越靠前',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
