-- liquibase formatted sql
-- id: 1

CALL `cloud_usage`.`IDEMPOTENT_ADD_COLUMN`('cloud_usage.test_table_2', 'column_2', 'bigint unsigned DEFAULT 2 COMMENT "Column 2"');
