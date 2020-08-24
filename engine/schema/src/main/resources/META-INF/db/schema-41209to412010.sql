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
-- Schema upgrade from 4.12.0.6 to 4.12.0.7
--;

-- Adding Pxe Boot variables
ALTER TABLE `cloud`.`vm_template` ADD `boot_filename` varchar(255) NULL default NULL COMMENT 'the url where the template exists externally';
ALTER TABLE `cloud`.`vpc` ADD `network_boot_ip` char(40) NULL default NULL COMMENT 'Network Boot Ip';

-- Add XenServer 8.2 hypervisor capabilities
INSERT IGNORE INTO `cloud`.`hypervisor_capabilities`(uuid, hypervisor_type, hypervisor_version, max_guests_limit, max_data_volumes_limit, storage_motion_supported)
values (UUID(), 'Citrix Hypervisor', '8.2.0', 500, 13, 1);

-- Copy XenServer 8.1 hypervisor guest OS mappings to XenServer 8.2
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid,hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined)
SELECT UUID(),'Citrix Hypervisor', '8.2.0', guest_os_name, guest_os_id, utc_timestamp(), 0  FROM `cloud`.`guest_os_hypervisor` WHERE hypervisor_type='Citrix Hypervisor' AND hypervisor_version='8.1.0';