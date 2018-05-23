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
-- Schema upgrade from 4.11.1.0 to 4.12.0.0
--;

-- [CLOUDSTACK-10314] Add reason column to ACL rule table
ALTER TABLE `cloud`.`network_acl_item` ADD COLUMN `reason` VARCHAR(2500) AFTER `display`;

-- [CLOUDSTACK-9846] Make provision to store content and subject for Alerts in separate columns.
ALTER TABLE `cloud`.`alert` ADD COLUMN `content` VARCHAR(5000);

-- Fix the name of the column used to hold IPv4 range in 'vlan' table.
ALTER TABLE `vlan` CHANGE `description` `ip4_range` varchar(255);

-- [CLOUDSTACK-10344] bug when moving ACL rules (change order with drag and drop)
-- We are only adding the permission to the default rules. Any custom rule must be configured by the root admin.
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 2, 'moveNetworkAclItem', 'ALLOW', 100) ON DUPLICATE KEY UPDATE rule=rule;
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 3, 'moveNetworkAclItem', 'ALLOW', 302) ON DUPLICATE KEY UPDATE rule=rule;
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`) values (UUID(), 4, 'moveNetworkAclItem', 'ALLOW', 260) ON DUPLICATE KEY UPDATE rule=rule;

CREATE TABLE `cloud`.`diagnosticsdata` (
  `role_id`   bigint unsigned NOT NULL auto_increment,
  `role` varchar(10) NOT NULL COMMENT 'role as for system vm',
  `class` varchar(30) NOT NULL COMMENT 'the kind of diagnostics files',
  `value` varchar(200) NOT NULL COMMENT 'default comma delimited list of files',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (2, 'SSMV', 'LOGFILES', 'cloud.log,agent.log,[IPTABLES]') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (3, 'SSMV', 'PROPERTYFILES', '<SSVM property files>') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (4, 'VR', 'DHCPFILES', 'dnsmasq.conf,resolv.conf,cloud.log,[IPTABLES],[IFCONFIG]') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (5, 'VR', 'USERDATA', '<userdatafiles>') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (6, 'VR', 'LB', 'haproxy.conf') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (7, 'VR', 'DNS', 'Hosts,resolv.conf,[IFCONFIG],[IPTABLES]') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (8, 'VR', 'VPN', '<vpn configuration file>') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (9, 'VR', 'LOGFILES', 'cloud.log,agent.log') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (10, 'CPVM', 'PROPERTYFILES', '<CPVM property file>') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (11, 'ALL', 'IPTABLES.retrieve', 'iptablesretrieve.sh') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (12, 'ALL', 'IPTABLES.remove', 'iptablesremove.sh') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (13, 'ALL', 'IPTABLES.retrieve', 'iptablesretrieve.sh') ON DUPLICATE KEY UPDATE role_id=role_id;
INSERT INTO `cloud`.`diagnosticsdata` ('role_id', `role`, `class`, `value`) values (14, 'ALL', 'IPTABLES.remove', 'iptablesremove.sh') ON DUPLICATE KEY UPDATE role_id=role_id;

