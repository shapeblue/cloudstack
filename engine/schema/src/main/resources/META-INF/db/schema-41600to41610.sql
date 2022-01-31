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
-- Schema upgrade from 4.16.0.0 to 4.16.1.0
--;

ALTER TABLE `cloud`.`vm_work_job` ADD COLUMN `secondary_object` char(100) COMMENT 'any additional item that must be checked during queueing' AFTER `vm_instance_id`;
UPDATE `cloud`.`vm_template` set deploy_as_is = 0 where id = 8;

CREATE PROCEDURE `cloud`.`UPDATE_KUBERNETES_NODE_DETAILS`()
BEGIN
  DECLARE vmid BIGINT
; DECLARE done TINYINT DEFAULT FALSE
; DECLARE vmidcursor CURSOR FOR SELECT DISTINCT(vm_id) FROM `cloud`.`kubernetes_cluster_vm_map`
; DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE
; OPEN vmidcursor
; vmid_loop:LOOP
    FETCH NEXT FROM vmidcursor INTO vmid
;   IF done THEN
      LEAVE vmid_loop
;   ELSE
      INSERT `cloud`.`user_vm_details` (vm_id, name, value, display) VALUES (vmid, 'controlNodeLoginUser', 'core', 1)
;   END IF
; END LOOP
; CLOSE vmidcursor
; END

CALL `cloud`.`UPDATE_KUBERNETES_NODE_DETAILS`()
DROP PROCEDURE IF EXISTS `cloud`.`UPDATE_KUBERNETES_NODE_DETAILS`
