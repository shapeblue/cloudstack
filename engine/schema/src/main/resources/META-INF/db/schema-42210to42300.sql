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
-- Schema upgrade from 4.22.1.0 to 4.23.0.0
--;

CREATE TABLE `cloud`.`backup_offering_details` (
    `id` bigint unsigned NOT NULL auto_increment,
    `backup_offering_id` bigint unsigned NOT NULL COMMENT 'Backup offering id',
    `name` varchar(255) NOT NULL,
    `value` varchar(1024) NOT NULL,
    `display` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Should detail be displayed to the end user',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_offering_details__backup_offering_id` FOREIGN KEY `fk_offering_details__backup_offering_id`(`backup_offering_id`) REFERENCES `backup_offering`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Update value to random for the config 'vm.allocation.algorithm' or 'volume.allocation.algorithm' if configured as userconcentratedpod_random
-- Update value to firstfit for the config 'vm.allocation.algorithm' or 'volume.allocation.algorithm' if configured as userconcentratedpod_firstfit
UPDATE `cloud`.`configuration` SET value='random' WHERE name IN ('vm.allocation.algorithm', 'volume.allocation.algorithm') AND value='userconcentratedpod_random';
UPDATE `cloud`.`configuration` SET value='firstfit' WHERE name IN ('vm.allocation.algorithm', 'volume.allocation.algorithm') AND value='userconcentratedpod_firstfit';

-- Create kubernetes_cluster_affinity_group_map table for CKS per-node-type affinity groups
CREATE TABLE IF NOT EXISTS `cloud`.`kubernetes_cluster_affinity_group_map` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `cluster_id` bigint unsigned NOT NULL COMMENT 'kubernetes cluster id',
    `node_type` varchar(32) NOT NULL COMMENT 'CONTROL, WORKER, or ETCD',
    `affinity_group_id` bigint unsigned NOT NULL COMMENT 'affinity group id',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_kubernetes_cluster_ag_map__cluster_id` FOREIGN KEY (`cluster_id`) REFERENCES `kubernetes_cluster`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_kubernetes_cluster_ag_map__ag_id` FOREIGN KEY (`affinity_group_id`) REFERENCES `affinity_group`(`id`) ON DELETE CASCADE,
    INDEX `i_kubernetes_cluster_ag_map__cluster_id`(`cluster_id`),
    INDEX `i_kubernetes_cluster_ag_map__ag_id`(`affinity_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Create webhook_filter table
DROP TABLE IF EXISTS `cloud`.`webhook_filter`;
CREATE TABLE IF NOT EXISTS `cloud`.`webhook_filter` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id of the webhook filter',
    `uuid` varchar(255) COMMENT 'uuid of the webhook filter',
    `webhook_id` bigint unsigned  NOT NULL COMMENT 'id of the webhook',
    `type` varchar(20) COMMENT 'type of the filter',
    `mode` varchar(20) COMMENT 'mode of the filter',
    `match_type` varchar(20) COMMENT 'match type of the filter',
    `value` varchar(256) NOT NULL COMMENT 'value of the filter used for matching',
    `created` datetime NOT NULL COMMENT 'date created',
    PRIMARY KEY (`id`),
    INDEX `i_webhook_filter__webhook_id`(`webhook_id`),
    CONSTRAINT `fk_webhook_filter__webhook_id` FOREIGN KEY(`webhook_id`) REFERENCES `webhook`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- "api_keypair" table for API and secret keys
CREATE TABLE IF NOT EXISTS `cloud`.`api_keypair` (
    `id` bigint(20) unsigned NOT NULL auto_increment,
    `uuid` varchar(40) UNIQUE NOT NULL,
    `name` varchar(255) NOT NULL,
    `domain_id` bigint(20) unsigned NOT NULL,
    `account_id` bigint(20) unsigned NOT NULL,
    `user_id` bigint(20) unsigned NOT NULL,
    `start_date` datetime,
    `end_date` datetime,
    `description` varchar(100),
    `api_key` varchar(255) NOT NULL,
    `secret_key` varchar(255) NOT NULL,
    `created` datetime NOT NULL,
    `removed` datetime,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_api_keypair__user_id` FOREIGN KEY(`user_id`) REFERENCES `cloud`.`user`(`id`),
    CONSTRAINT `fk_api_keypair__account_id` FOREIGN KEY(`account_id`) REFERENCES `cloud`.`account`(`id`),
    CONSTRAINT `fk_api_keypair__domain_id` FOREIGN KEY(`domain_id`) REFERENCES `cloud`.`domain`(`id`)
);

-- "api_keypair_permissions" table for API key pairs permissions
CREATE TABLE IF NOT EXISTS `cloud`.`api_keypair_permissions` (
    `id` bigint(20) unsigned NOT NULL auto_increment,
    `uuid` varchar(40) UNIQUE,
    `sort_order` bigint(20) unsigned NOT NULL DEFAULT 0,
    `rule` varchar(255) NOT NULL,
    `api_keypair_id` bigint(20) unsigned NOT NULL,
    `permission` varchar(255) NOT NULL,
    `description` varchar(255),
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_keypair_permissions__api_keypair_id` FOREIGN KEY(`api_keypair_id`) REFERENCES `cloud`.`api_keypair`(`id`)
);

-- Populate "api_keypair" table with existing user API keys
INSERT INTO `cloud`.`api_keypair` (uuid, user_id, domain_id, account_id, api_key, secret_key, created, name)
SELECT UUID(), user.id, account.domain_id, account.id, user.api_key, user.secret_key, NOW(), 'Active key pair'
FROM `cloud`.`user` AS user
JOIN `cloud`.`account` AS account ON user.account_id = account.id
WHERE user.api_key IS NOT NULL AND user.secret_key IS NOT NULL;

-- Drop API keys from user table
ALTER TABLE `cloud`.`user` DROP COLUMN api_key, DROP COLUMN secret_key;

-- Grant access to the "deleteUserKeys" API to the "User", "Domain Admin" and "Resource Admin" roles, similarly to the "registerUserKeys" API
CALL `cloud`.`IDEMPOTENT_UPDATE_API_PERMISSION`('User', 'deleteUserKeys', 'ALLOW');
CALL `cloud`.`IDEMPOTENT_UPDATE_API_PERMISSION`('Domain Admin', 'deleteUserKeys', 'ALLOW');
CALL `cloud`.`IDEMPOTENT_UPDATE_API_PERMISSION`('Resource Admin', 'deleteUserKeys', 'ALLOW');

-- Add conserve mode for VPC offerings
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.vpc_offerings','conserve_mode', 'tinyint(1) unsigned NULL DEFAULT 0 COMMENT ''True if the VPC offering is IP conserve mode enabled, allowing public IP services to be used across multiple VPC tiers'' ');

--- Disable/enable NICs
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.nics','enabled', 'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Indicates whether the NIC is enabled or not'' ');

-- OVN Plugin
CREATE TABLE IF NOT EXISTS `cloud`.`ovn_providers` (
    `id` bigint unsigned NOT NULL auto_increment COMMENT 'id',
    `uuid` varchar(40),
    `zone_id` bigint unsigned NOT NULL COMMENT 'Zone ID',
    `host_id` bigint unsigned COMMENT 'Optional resource host ID if OVN command routing is enabled',
    `name` varchar(255) NOT NULL,
    `nb_connection` varchar(255) NOT NULL COMMENT 'OVN Northbound database connection string',
    `sb_connection` varchar(255) COMMENT 'OVN Southbound database connection string',
    `ca_cert_path` varchar(1024) COMMENT 'OVN TLS CA certificate path',
    `client_cert_path` varchar(1024) COMMENT 'OVN TLS client certificate path',
    `client_private_key_path` varchar(1024) COMMENT 'OVN TLS client private key path',
    `external_bridge` varchar(255) COMMENT 'OVN external bridge used for provider network access',
    `localnet_name` varchar(255) COMMENT 'OVN localnet name used for provider network mapping',
    `ic_nb_connection` varchar(255) COMMENT 'OVN-IC Northbound DB connection string for inter-zone mesh networks',
    `ic_sb_connection` varchar(255) COMMENT 'OVN-IC Southbound DB connection string for diagnostics',
    `availability_zone_name` varchar(255) COMMENT 'Availability zone name registered in NB_Global for OVN-IC',
    `created` datetime NOT NULL COMMENT 'created date',
    `removed` datetime COMMENT 'removed date if not null',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_ovn_providers__zone_id` FOREIGN KEY `fk_ovn_providers__zone_id` (`zone_id`) REFERENCES `data_center`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ovn_providers__host_id` FOREIGN KEY `fk_ovn_providers__host_id` (`host_id`) REFERENCES `host`(`id`) ON DELETE SET NULL,
    UNIQUE KEY `uk_ovn_providers__zone_id` (`zone_id`),
    INDEX `i_ovn_providers__zone_id`(`zone_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- OVN Mesh Network
-- A row stores one mesh-network membership. The member is either a VPC
-- (vpc_id set) or an Isolated guest Network (network_id set) — exactly
-- one of those two columns is populated; the other is NULL. A mesh
-- network groups one or more members sharing the same mesh_uuid.
CREATE TABLE IF NOT EXISTS `cloud`.`ovn_mesh_networks` (
    `id` bigint unsigned NOT NULL auto_increment,
    `uuid` varchar(40) NOT NULL,
    `mesh_uuid` varchar(40) NOT NULL COMMENT 'Mesh network identifier (groups members in a single mesh)',
    `name` varchar(255) DEFAULT NULL COMMENT 'User-given mesh network name',
    `description` varchar(1024) DEFAULT NULL COMMENT 'User-given mesh network description',
    `vpc_id` bigint unsigned DEFAULT NULL COMMENT 'Set when the member is a VPC; NULL when the member is an isolated network',
    `network_id` bigint unsigned DEFAULT NULL COMMENT 'Set when the member is an isolated guest network; NULL when the member is a VPC',
    `zone_id` bigint unsigned NOT NULL,
    `account_id` bigint unsigned NOT NULL,
    `domain_id` bigint unsigned NOT NULL,
    `link_local_ip` varchar(15) NOT NULL COMMENT 'Link-local IP on the mesh network switch',
    `acl_id` bigint unsigned DEFAULT NULL COMMENT 'Optional Network ACL applied to this mesh network membership',
    `state` varchar(16) NOT NULL DEFAULT 'Active',
    `created` datetime NOT NULL,
    `removed` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ovn_mesh_networks_uuid` (`uuid`),
    INDEX `i_ovn_mesh_networks_mesh` (`mesh_uuid`),
    INDEX `i_ovn_mesh_networks_vpc` (`vpc_id`),
    INDEX `i_ovn_mesh_networks_network` (`network_id`),
    CONSTRAINT `fk_ovn_mesh_networks_vpc` FOREIGN KEY (`vpc_id`) REFERENCES `cloud`.`vpc`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ovn_mesh_networks_network` FOREIGN KEY (`network_id`) REFERENCES `cloud`.`networks`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ovn_mesh_networks_zone` FOREIGN KEY (`zone_id`) REFERENCES `cloud`.`data_center`(`id`),
    CONSTRAINT `fk_ovn_mesh_networks_account` FOREIGN KEY (`account_id`) REFERENCES `cloud`.`account`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Add Firewall service to the default OVN VPC offering so that OVN VPC tiers
-- using network offerings with Firewall/Ovn pass the service validation check.
INSERT IGNORE INTO `cloud`.`vpc_offering_service_map` (`vpc_offering_id`, `service`, `provider`)
    SELECT vo.id, 'Firewall', 'Ovn'
    FROM `cloud`.`vpc_offerings` vo
    WHERE vo.unique_name = 'VPC offering with OVN - NAT Mode'
      AND NOT EXISTS (
          SELECT 1 FROM `cloud`.`vpc_offering_service_map` sm
          WHERE sm.vpc_offering_id = vo.id AND sm.service = 'Firewall'
      );

--- Quota tariff/usage mapping
CREATE TABLE IF NOT EXISTS `cloud_usage`.`quota_tariff_usage` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `tariff_id` bigint(20) unsigned NOT NULL COMMENT 'ID of the tariff of the Quota usage detail calculated, foreign key to quota_tariff table',
    `quota_usage_id` bigint(20) unsigned NOT NULL COMMENT 'ID of the aggregation of Quota usage details, foreign key to quota_usage table',
    `quota_used` decimal(20,8) NOT NULL COMMENT 'Amount of quota used',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_quota_tariff_usage__tariff_id` FOREIGN KEY (`tariff_id`) REFERENCES `cloud_usage`.`quota_tariff` (`id`),
    CONSTRAINT `fk_quota_tariff_usage__quota_usage_id` FOREIGN KEY (`quota_usage_id`) REFERENCES `cloud_usage`.`quota_usage` (`id`));
