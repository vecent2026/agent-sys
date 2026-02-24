#!/bin/bash

# 数据库迁移脚本
echo "执行数据库迁移..."

# 直接执行SQL命令
echo "请手动执行以下SQL命令："
echo "1. 连接到MySQL数据库"
echo "2. 执行以下SQL："
echo "ALTER TABLE sys_log ADD COLUMN username VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名';"
echo "ALTER TABLE sys_log ADD COLUMN status TINYINT(1) DEFAULT 1 COMMENT '状态：1成功 0失败';"