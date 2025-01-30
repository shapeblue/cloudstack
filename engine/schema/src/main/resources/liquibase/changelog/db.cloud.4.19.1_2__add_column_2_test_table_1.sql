-- liquibase formatted sql
-- id: 1

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.test_table_1', 'column_2', 'bigint unsigned DEFAULT 2 COMMENT "Column 2"');
