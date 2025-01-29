-- ChangeSet 1: Create a new table
-- liquibase formatted sql
-- id: 1

CREATE TABLE IF NOT EXISTS `cloud`.`test_table_2` (
    `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
    `column_1` bigint unsigned DEFAULT 1 COMMENT 'Column 1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
