-- 视图的 user_id 表示创建者（管理员/sys_user id），不再关联 app_user。
-- 若库中 user_views 已存在且带外键 fk_user_views_user_id，请执行下一行以移除外键（仅需执行一次）。
-- 若报错提示外键不存在，说明已是新结构或表为新建，可忽略。
ALTER TABLE `user_views` DROP FOREIGN KEY `fk_user_views_user_id`;
