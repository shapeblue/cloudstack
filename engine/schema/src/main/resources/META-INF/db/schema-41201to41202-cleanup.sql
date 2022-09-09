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
-- Schema upgrade cleanup from 4.12.0.1 to 4.12.0.2
--;

-- Ubuntu 18.04 fixes
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 278;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 277;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 278;

-- Ubuntu 18.10 fixes
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.2' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.2' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 280;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.6.0' AND `guest_os_id` = 279;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.6.0' AND `guest_os_id` = 280;

-- Ubuntu 19.04 fixes
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '6.5.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.0.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.1' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.2' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.1.2' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.2.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.3.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.4.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.5.0' AND `guest_os_id` = 282;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.6.0' AND `guest_os_id` = 281;
UPDATE `cloud`.`guest_os_hypervisor` SET `guest_os_name` = 'Other install media' WHERE `hypervisor_type` = 'Xenserver' AND `hypervisor_version` = '7.6.0' AND `guest_os_id` = 282;
