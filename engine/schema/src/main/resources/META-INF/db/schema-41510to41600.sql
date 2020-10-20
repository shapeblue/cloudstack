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
-- Schema upgrade from 4.15.1.0 to 4.16.0.0
--;

ALTER TABLE `cloud`.`kubernetes_cluster` ADD COLUMN `autoscaling_enabled` tinyint(1) NOT NULL DEFAULT '0';
ALTER TABLE `cloud`.`kubernetes_cluster` ADD COLUMN `minsize` bigint;
ALTER TABLE `cloud`.`kubernetes_cluster` ADD COLUMN `maxsize` bigint;

ALTER TABLE `cloud`.`kubernetes_cluster_vm_map` ADD COLUMN `is_master` tinyint(1) NOT NULL DEFAULT '0';
