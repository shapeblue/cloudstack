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
-- Schema upgrade from 4.12.0.0 to 4.13.0.0
--;

CREATE TABLE `cloud`.`ovf_properties` (
  `id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `key` VARCHAR(100) NOT NULL,
  `type` VARCHAR(45) NULL,
  `value` VARCHAR(100) NULL,
  `qualifiers` TEXT NULL,
  `user_configurable` TINYINT(1) NULL,
  `label` TEXT NULL,
  `description` TEXT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ovf_vapp_options__template_id` FOREIGN KEY (`template_id`) REFERENCES `vm_template`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;