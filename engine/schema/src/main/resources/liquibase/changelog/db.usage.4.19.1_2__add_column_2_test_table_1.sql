-- ChangeSet 1: Add a column to an existing table
-- liquibase formatted sql
-- id: 1

CALL `cloud_usage`.`IDEMPOTENT_ADD_COLUMN`('cloud_usage.test_table_1', 'column_2', 'bigint unsigned DEFAULT 2 COMMENT "Column 2"');
