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
-- Schema upgrade from 4.12.0.0 to 4.12.0.1
--;

-- add KVM / qemu io bursting options PR 3133
ALTER VIEW `cloud`.`disk_offering_view` AS
    SELECT
        `disk_offering`.`id` AS `id`,
        `disk_offering`.`uuid` AS `uuid`,
        `disk_offering`.`name` AS `name`,
        `disk_offering`.`display_text` AS `display_text`,
        `disk_offering`.`provisioning_type` AS `provisioning_type`,
        `disk_offering`.`disk_size` AS `disk_size`,
        `disk_offering`.`min_iops` AS `min_iops`,
        `disk_offering`.`max_iops` AS `max_iops`,
        `disk_offering`.`created` AS `created`,
        `disk_offering`.`tags` AS `tags`,
        `disk_offering`.`customized` AS `customized`,
        `disk_offering`.`customized_iops` AS `customized_iops`,
        `disk_offering`.`removed` AS `removed`,
        `disk_offering`.`use_local_storage` AS `use_local_storage`,
        `disk_offering`.`system_use` AS `system_use`,
        `disk_offering`.`hv_ss_reserve` AS `hv_ss_reserve`,
        `disk_offering`.`bytes_read_rate` AS `bytes_read_rate`,
        `disk_offering`.`bytes_read_rate_max` AS `bytes_read_rate_max`,
        `disk_offering`.`bytes_read_rate_max_length` AS `bytes_read_rate_max_length`,
        `disk_offering`.`bytes_write_rate` AS `bytes_write_rate`,
        `disk_offering`.`bytes_write_rate_max` AS `bytes_write_rate_max`,
        `disk_offering`.`bytes_write_rate_max_length` AS `bytes_write_rate_max_length`,
        `disk_offering`.`iops_read_rate` AS `iops_read_rate`,
        `disk_offering`.`iops_read_rate_max` AS `iops_read_rate_max`,
        `disk_offering`.`iops_read_rate_max_length` AS `iops_read_rate_max_length`,
        `disk_offering`.`iops_write_rate` AS `iops_write_rate`,
        `disk_offering`.`iops_write_rate_max` AS `iops_write_rate_max`,
        `disk_offering`.`iops_write_rate_max_length` AS `iops_write_rate_max_length`,
        `disk_offering`.`min_iops_per_gb` AS `min_iops_per_gb`,
        `disk_offering`.`max_iops_per_gb` AS `max_iops_per_gb`,
        `disk_offering`.`highest_min_iops` AS `highest_min_iops`,
        `disk_offering`.`highest_max_iops` AS `highest_max_iops`,
        `disk_offering`.`cache_mode` AS `cache_mode`,
        `disk_offering`.`sort_key` AS `sort_key`,
        `disk_offering`.`type` AS `type`,
        `disk_offering`.`display_offering` AS `display_offering`,
        `domain`.`id` AS `domain_id`,
        `domain`.`uuid` AS `domain_uuid`,
        `domain`.`name` AS `domain_name`,
        `domain`.`path` AS `domain_path`
    FROM
        (`disk_offering`
        LEFT JOIN `domain` ON ((`disk_offering`.`domain_id` = `domain`.`id`)))
    WHERE
        (`disk_offering`.`state` = 'ACTIVE');
