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
-- Schema upgrade from 4.9.3.0 to 4.10.0.0;
--;

ALTER TABLE `cloud`.`domain_router` ADD COLUMN  update_state varchar(64) DEFAULT NULL;

INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (257, UUID(), 6, 'Windows 10 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (258, UUID(), 6, 'Windows 10 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (259, UUID(), 6, 'Windows Server 2016 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (260, UUID(), 1, 'CentOS 7.1', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (261, UUID(), 1, 'CentOS 6.6 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (262, UUID(), 1, 'CentOS 6.6 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (263, UUID(), 1, 'CentOS 6.7 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (264, UUID(), 1, 'CentOS 6.7 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (265, UUID(), 4, 'Red Hat Enterprise Linux 6.6 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (266, UUID(), 4, 'Red Hat Enterprise Linux 6.6 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (267, UUID(), 4, 'Red Hat Enterprise Linux 6.7 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (268, UUID(), 4, 'Red Hat Enterprise Linux 6.7 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (269, UUID(), 2, 'Debian GNU/Linux 8 (32-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (270, UUID(), 2, 'Debian GNU/Linux 8 (64-bit)', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (271, UUID(), 7, 'CoreOS', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (272, UUID(), 4, 'Red Hat Enterprise Linux 7.1', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (273, UUID(), 4, 'Red Hat Enterprise Linux 7.2', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (274, UUID(), 1, 'CentOS 7.2', now());
INSERT IGNORE INTO `cloud`.`guest_os` (id, uuid, category_id, display_name, created) VALUES (275, UUID(), 6, 'Other PV Virtio-SCSI (64-bit)', now());

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '6.5.0', 'Windows 10 (32-bit)', 257, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '6.0', 'windows9Guest', 257, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'Windows 10', 257, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '6.5.0', 'Windows 10 (64-bit)', 258, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '6.0', 'windows9_64Guest', 258, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'Windows 10', 258, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '6.5.0', 'Windows Server 2016 (64-bit)', 259, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '7.0.0', 'Windows Server 2016 (64-bit)', 259, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '6.0', 'windows9Server64Guest', 259, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'Windows Server 2016', 259, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'CentOS 7', 260, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '5.5', 'centos64Guest', 260, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'centos64Guest', 260, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'KVM', 'default', 'CentOS 7.1', 260, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'CentOS 6 (32-bit)', 261, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'centosGuest', 261, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'CentOS 6 (64-bit)', 262, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'centos64Guest', 262, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'CentOS 6 (32-bit)', 263, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'centosGuest', 263, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'CentOS 6 (64-bit)', 264, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'centos64Guest', 264, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 6 (32-bit)', 265, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'rhel6Guest', 265, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 6 (64-bit)', 266, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'rhel6_64Guest', 266, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 6 (32-bit)', 267, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'rhel6Guest', 267, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 6 (64-bit)', 268, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'rhel6_64Guest', 268, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'debian8Guest', 269, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'debian8Guest', 270, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', 'default', 'CoreOS', 271, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', 'default', 'coreos64Guest', 271, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '5.5', 'coreos64Guest', 271, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'coreos64Guest', 271, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'KVM', 'default', 'CoreOS', 271, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 7', 272, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '5.5', 'rhel7_64Guest', 272, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'VMware', '6.0', 'rhel7_64Guest', 272, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(),'KVM', 'default', 'Red Hat Enterprise Linux 7.1', 272, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '6.5.0', 'Red Hat Enterprise Linux 7', 273, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '7.0.0', 'Red Hat Enterprise Linux 7', 273, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '5.5', 'rhel7_64Guest', 273, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '6.0', 'rhel7_64Guest', 273, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'Red Hat Enterprise Linux 7.2', 273, now(), 0);

INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '6.5.0', 'CentOS 7', 274, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'Xenserver', '7.0.0', 'CentOS 7', 274, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '5.5', 'centos64Guest', 274, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'VMware', '6.0', 'centos64Guest', 274, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'CentOS 7.2', 274, now(), 0);
INSERT IGNORE INTO `cloud`.`guest_os_hypervisor` (uuid, hypervisor_type, hypervisor_version, guest_os_name, guest_os_id, created, is_user_defined) VALUES (UUID(), 'KVM', 'default', 'Other PV Virtio-SCSI (64-bit)', 275, now(), 0);

CREATE TABLE `cloud`.`vlan_details` (
  `id` bigint unsigned NOT NULL auto_increment,
  `vlan_id` bigint unsigned NOT NULL COMMENT 'vlan id',
  `name` varchar(255) NOT NULL,
  `value` varchar(1024) NOT NULL,
  `display` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Should detail be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_vlan_details__vlan_id` FOREIGN KEY `fk_vlan_details__vlan_id`(`vlan_id`) REFERENCES `vlan`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `cloud`.`network_offerings` ADD COLUMN supports_public_access boolean default false;

ALTER TABLE `cloud`.`image_store_details` CHANGE COLUMN `value` `value` VARCHAR(255) NULL DEFAULT NULL COMMENT 'value of the detail', ADD COLUMN `display` tinyint(1) NOT
NULL DEFAULT '1' COMMENT 'True if the detail can be displayed to the end user' AFTER `value`;

ALTER TABLE `snapshots` ADD COLUMN `location_type` VARCHAR(32) COMMENT 'Location of snapshot (ex. Primary)';

-- Database change for CLOUDSTACK-8746 (VM Snapshotting implementation for KVM)
UPDATE `cloud`.`hypervisor_capabilities` SET `vm_snapshot_enabled` = 1 WHERE `hypervisor_type` ='KVM' AND `hypervisor_version` = 'default';

-- [VM-SNAPSHOT] add role permissions for new API command createSnapshotFromVMSnapshot
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 2, 'createSnapshotFromVMSnapshot', 'ALLOW', 318) ON DUPLICATE KEY UPDATE rule=rule;
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 3, 'createSnapshotFromVMSnapshot', 'ALLOW', 302) ON DUPLICATE KEY UPDATE rule=rule;
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 4, 'createSnapshotFromVMSnapshot', 'ALLOW', 260) ON DUPLICATE KEY UPDATE rule=rule;

-- Create table storage_pool_tags
CREATE TABLE `cloud`.`storage_pool_tags` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pool_id` bigint(20) unsigned NOT NULL COMMENT "pool id",
  `tag` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_storage_pool_tags__pool_id` (`pool_id`),
  CONSTRAINT `fk_storage_pool_tags__pool_id` FOREIGN KEY (`pool_id`) REFERENCES `storage_pool` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;

-- Insert storage tags from storage_pool_details
INSERT INTO `cloud`.`storage_pool_tags` (pool_id, tag) SELECT pool_id, 
name FROM `cloud`.`storage_pool_details` WHERE value = 'true';

-- Alter view storage_pool_view
CREATE OR REPLACE
VIEW `storage_pool_view` AS
    SELECT
        `storage_pool`.`id` AS `id`,
        `storage_pool`.`uuid` AS `uuid`,
        `storage_pool`.`name` AS `name`,
        `storage_pool`.`status` AS `status`,
        `storage_pool`.`path` AS `path`,
        `storage_pool`.`pool_type` AS `pool_type`,
        `storage_pool`.`host_address` AS `host_address`,
        `storage_pool`.`created` AS `created`,
        `storage_pool`.`removed` AS `removed`,
        `storage_pool`.`capacity_bytes` AS `capacity_bytes`,
        `storage_pool`.`capacity_iops` AS `capacity_iops`,
        `storage_pool`.`scope` AS `scope`,
        `storage_pool`.`hypervisor` AS `hypervisor`,
        `storage_pool`.`storage_provider_name` AS `storage_provider_name`,
        `cluster`.`id` AS `cluster_id`,
        `cluster`.`uuid` AS `cluster_uuid`,
        `cluster`.`name` AS `cluster_name`,
        `cluster`.`cluster_type` AS `cluster_type`,
        `data_center`.`id` AS `data_center_id`,
        `data_center`.`uuid` AS `data_center_uuid`,
        `data_center`.`name` AS `data_center_name`,
        `data_center`.`networktype` AS `data_center_type`,
        `host_pod_ref`.`id` AS `pod_id`,
        `host_pod_ref`.`uuid` AS `pod_uuid`,
        `host_pod_ref`.`name` AS `pod_name`,
        `storage_pool_tags`.`tag` AS `tag`,
        `op_host_capacity`.`used_capacity` AS `disk_used_capacity`,
        `op_host_capacity`.`reserved_capacity` AS `disk_reserved_capacity`,
        `async_job`.`id` AS `job_id`,
        `async_job`.`uuid` AS `job_uuid`,
        `async_job`.`job_status` AS `job_status`,
        `async_job`.`account_id` AS `job_account_id`
    FROM
        ((((((`storage_pool`
        LEFT JOIN `cluster` ON ((`storage_pool`.`cluster_id` = `cluster`.`id`)))
        LEFT JOIN `data_center` ON ((`storage_pool`.`data_center_id` = `data_center`.`id`)))
        LEFT JOIN `host_pod_ref` ON ((`storage_pool`.`pod_id` = `host_pod_ref`.`id`)))
        LEFT JOIN `storage_pool_tags` ON (((`storage_pool_tags`.`pool_id` = `storage_pool`.`id`))))
        LEFT JOIN `op_host_capacity` ON (((`storage_pool`.`id` = `op_host_capacity`.`host_id`)
            AND (`op_host_capacity`.`capacity_type` IN (3 , 9)))))
        LEFT JOIN `async_job` ON (((`async_job`.`instance_id` = `storage_pool`.`id`)
            AND (`async_job`.`instance_type` = 'StoragePool')
            AND (`async_job`.`job_status` = 0))));

-- Alter view image_store_view
CREATE OR REPLACE
VIEW `image_store_view` AS
    SELECT
        `image_store`.`id` AS `id`,
        `image_store`.`uuid` AS `uuid`,
        `image_store`.`name` AS `name`,
        `image_store`.`image_provider_name` AS `image_provider_name`,
        `image_store`.`protocol` AS `protocol`,
        `image_store`.`url` AS `url`,
        `image_store`.`scope` AS `scope`,
        `image_store`.`role` AS `role`,
        `image_store`.`removed` AS `removed`,
        `data_center`.`id` AS `data_center_id`,
        `data_center`.`uuid` AS `data_center_uuid`,
        `data_center`.`name` AS `data_center_name`
    FROM
        (`image_store`
        LEFT JOIN `data_center` ON ((`image_store`.`data_center_id` = `data_center`.`id`)));

-- Add service_offering_id column to vm_snapshots table
ALTER TABLE `cloud`.`vm_snapshots` ADD COLUMN `service_offering_id` BIGINT(20) UNSIGNED NOT NULL COMMENT '' AFTER `domain_id`;
UPDATE `cloud`.`vm_snapshots` s JOIN `cloud`.`vm_instance` v ON v.id = s.vm_id SET s.service_offering_id = v.service_offering_id;
ALTER TABLE `cloud`.`vm_snapshots` ADD CONSTRAINT `fk_vm_snapshots_service_offering_id` FOREIGN KEY (`service_offering_id`) REFERENCES `cloud`.`service_offering` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- Update vm snapshot details for instances with custom service offerings
INSERT INTO `cloud`.`vm_snapshot_details` (vm_snapshot_id, name, value)
SELECT s.id, d.name, d.value
FROM `cloud`.`user_vm_details` d JOIN `cloud`.`vm_instance` v ON (d.vm_id = v.id)
JOIN `cloud`.`service_offering` o ON (v.service_offering_id = o.id) 
JOIN `cloud`.`vm_snapshots` s ON (s.service_offering_id = o.id AND s.vm_id = v.id)
WHERE (o.cpu is null AND o.speed IS NULL AND o.ram_size IS NULL) AND
(d.name = 'cpuNumber' OR d.name = 'cpuSpeed' OR d.name = 'memory');

-- CLOUDSTACK-9827: Storage tags stored in multiple places
DROP VIEW IF EXISTS `cloud`.`storage_tag_view`;

CREATE TABLE `cloud`.`guest_os_details` (
  `id` bigint unsigned NOT NULL auto_increment,
  `guest_os_id` bigint unsigned NOT NULL COMMENT 'VPC gateway id',
  `name` varchar(255) NOT NULL,
  `value` varchar(1024) NOT NULL,
  `display` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'True if the detail can be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_guest_os_details__guest_os_id` FOREIGN KEY `fk_guest_os_details__guest_os_id`(`guest_os_id`) REFERENCES `guest_os`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `user_ip_address` ADD COLUMN `rule_state` VARCHAR(32) COMMENT 'static  rule state while removing';


ALTER TABLE `cloud`.`vm_template` ADD COLUMN `parent_template_id` bigint(20) unsigned DEFAULT NULL COMMENT 'If datadisk template, then id of the root template this template belongs to';





DROP VIEW IF EXISTS `cloud`.`template_view`;
CREATE  VIEW `template_view` AS
    SELECT
        `vm_template`.`id` AS `id`,
        `vm_template`.`uuid` AS `uuid`,
        `vm_template`.`unique_name` AS `unique_name`,
        `vm_template`.`name` AS `name`,
        `vm_template`.`public` AS `public`,
        `vm_template`.`featured` AS `featured`,
        `vm_template`.`type` AS `type`,
        `vm_template`.`hvm` AS `hvm`,
        `vm_template`.`bits` AS `bits`,
        `vm_template`.`url` AS `url`,
        `vm_template`.`format` AS `format`,
        `vm_template`.`created` AS `created`,
        `vm_template`.`checksum` AS `checksum`,
        `vm_template`.`display_text` AS `display_text`,
        `vm_template`.`enable_password` AS `enable_password`,
        `vm_template`.`dynamically_scalable` AS `dynamically_scalable`,
        `vm_template`.`state` AS `template_state`,
        `vm_template`.`guest_os_id` AS `guest_os_id`,
        `guest_os`.`uuid` AS `guest_os_uuid`,
        `guest_os`.`display_name` AS `guest_os_name`,
        `vm_template`.`bootable` AS `bootable`,
        `vm_template`.`prepopulate` AS `prepopulate`,
        `vm_template`.`cross_zones` AS `cross_zones`,
        `vm_template`.`hypervisor_type` AS `hypervisor_type`,
        `vm_template`.`extractable` AS `extractable`,
        `vm_template`.`template_tag` AS `template_tag`,
        `vm_template`.`sort_key` AS `sort_key`,
        `vm_template`.`removed` AS `removed`,
        `vm_template`.`enable_sshkey` AS `enable_sshkey`,
        `parent_template`.`id` AS `parent_template_id`,
        `parent_template`.`uuid` AS `parent_template_uuid`,
        `source_template`.`id` AS `source_template_id`,
        `source_template`.`uuid` AS `source_template_uuid`,
        `account`.`id` AS `account_id`,
        `account`.`uuid` AS `account_uuid`,
        `account`.`account_name` AS `account_name`,
        `account`.`type` AS `account_type`,
        `domain`.`id` AS `domain_id`,
        `domain`.`uuid` AS `domain_uuid`,
        `domain`.`name` AS `domain_name`,
        `domain`.`path` AS `domain_path`,
        `projects`.`id` AS `project_id`,
        `projects`.`uuid` AS `project_uuid`,
        `projects`.`name` AS `project_name`,
        `data_center`.`id` AS `data_center_id`,
        `data_center`.`uuid` AS `data_center_uuid`,
        `data_center`.`name` AS `data_center_name`,
        `launch_permission`.`account_id` AS `lp_account_id`,
        `template_store_ref`.`store_id` AS `store_id`,
        `image_store`.`scope` AS `store_scope`,
        `template_store_ref`.`state` AS `state`,
        `template_store_ref`.`download_state` AS `download_state`,
        `template_store_ref`.`download_pct` AS `download_pct`,
        `template_store_ref`.`error_str` AS `error_str`,
        `template_store_ref`.`size` AS `size`,
        `template_store_ref`.`destroyed` AS `destroyed`,
        `template_store_ref`.`created` AS `created_on_store`,
        `vm_template_details`.`name` AS `detail_name`,
        `vm_template_details`.`value` AS `detail_value`,
        `resource_tags`.`id` AS `tag_id`,
        `resource_tags`.`uuid` AS `tag_uuid`,
        `resource_tags`.`key` AS `tag_key`,
        `resource_tags`.`value` AS `tag_value`,
        `resource_tags`.`domain_id` AS `tag_domain_id`,
        `domain`.`uuid` AS `tag_domain_uuid`,
        `domain`.`name` AS `tag_domain_name`,
        `resource_tags`.`account_id` AS `tag_account_id`,
        `account`.`account_name` AS `tag_account_name`,
        `resource_tags`.`resource_id` AS `tag_resource_id`,
        `resource_tags`.`resource_uuid` AS `tag_resource_uuid`,
        `resource_tags`.`resource_type` AS `tag_resource_type`,
        `resource_tags`.`customer` AS `tag_customer`,
        CONCAT(`vm_template`.`id`,
                '_',
                IFNULL(`data_center`.`id`, 0)) AS `temp_zone_pair`
    FROM
        (((((((((((((`vm_template`
        JOIN `guest_os` ON ((`guest_os`.`id` = `vm_template`.`guest_os_id`)))
        JOIN `account` ON ((`account`.`id` = `vm_template`.`account_id`)))
        JOIN `domain` ON ((`domain`.`id` = `account`.`domain_id`)))
        LEFT JOIN `projects` ON ((`projects`.`project_account_id` = `account`.`id`)))
        LEFT JOIN `vm_template_details` ON ((`vm_template_details`.`template_id` = `vm_template`.`id`)))
        LEFT JOIN `vm_template` `source_template` ON ((`source_template`.`id` = `vm_template`.`source_template_id`)))
        LEFT JOIN `template_store_ref` ON (((`template_store_ref`.`template_id` = `vm_template`.`id`)
            AND (`template_store_ref`.`store_role` = 'Image')
            AND (`template_store_ref`.`destroyed` = 0))))
        LEFT JOIN `vm_template` `parent_template` ON ((`parent_template`.`id` = `vm_template`.`parent_template_id`)))
        LEFT JOIN `image_store` ON ((ISNULL(`image_store`.`removed`)
            AND (`template_store_ref`.`store_id` IS NOT NULL)
            AND (`image_store`.`id` = `template_store_ref`.`store_id`))))
        LEFT JOIN `template_zone_ref` ON (((`template_zone_ref`.`template_id` = `vm_template`.`id`)
            AND ISNULL(`template_store_ref`.`store_id`)
            AND ISNULL(`template_zone_ref`.`removed`))))
        LEFT JOIN `data_center` ON (((`image_store`.`data_center_id` = `data_center`.`id`)
            OR (`template_zone_ref`.`zone_id` = `data_center`.`id`))))
        LEFT JOIN `launch_permission` ON ((`launch_permission`.`template_id` = `vm_template`.`id`)))
        LEFT JOIN `resource_tags` ON (((`resource_tags`.`resource_id` = `vm_template`.`id`)
            AND ((`resource_tags`.`resource_type` = 'Template')
            OR (`resource_tags`.`resource_type` = 'ISO')))));
