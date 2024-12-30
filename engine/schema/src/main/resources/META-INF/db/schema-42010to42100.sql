-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from 4.20.1.0 to 4.21.0.0
--;

-- Add console_endpoint_creator_address column to cloud.console_session table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.console_session', 'console_endpoint_creator_address', 'VARCHAR(45)');

-- Add client_address column to cloud.console_session table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.console_session', 'client_address', 'VARCHAR(45)');

-- Add columns to backup for restoring backups of expunged VMs
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'hypervisor_type', 'CHAR(32)');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'service_offering_id', 'BIGINT UNSIGNED NOT NULL COMMENT \'service offering id\'');
ALTER TABLE `cloud`.`backups` ADD CONSTRAINT `fk_service_offering_id` FOREIGN KEY (`service_offering_id`) REFERENCES `cloud`.`service_offering`(`id`);
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'vm_template_id', 'BIGINT UNSIGNED');
ALTER TABLE `cloud`.`backups` ADD CONSTRAINT `fk_vm_template_id` FOREIGN KEY (`vm_template_id`) REFERENCES `cloud`.`vm_template`(`id`);
