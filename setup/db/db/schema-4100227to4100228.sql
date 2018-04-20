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
-- Schema upgrade from 4.10.0.227 to 4.10.0.228;
--;

-- VPN implementation based on IKEv2
ALTER TABLE `cloud`.`remote_access_vpn` CHANGE COLUMN `ipsec_psk` `ipsec_psk` VARCHAR(256) NULL ;
ALTER TABLE `cloud`.`remote_access_vpn`
    ADD COLUMN `vpn_type` VARCHAR(8) NOT NULL AFTER `display`,
    ADD COLUMN `ca_certificate` VARCHAR(8191) NULL AFTER `vpn_type`;

ALTER TABLE `cloud`.`remote_access_vpn_details` CHANGE COLUMN `value` `value` VARCHAR(8191) NOT NULL ;
