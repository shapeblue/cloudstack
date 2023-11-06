// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.storage.template;

import com.cloud.dc.DataCenter;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.security.SecurityGroup;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.uservm.UserVm;
import org.apache.cloudstack.api.command.user.template.RegisterVnfTemplateCmd;
import org.apache.cloudstack.api.command.user.template.UpdateVnfTemplateCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVnfApplianceCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import java.util.List;

public interface VnfTemplateManager {

    ConfigKey<Boolean> VnfTemplateAndApplianceEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class,
            "vnf.template.appliance.enabled",
            "true",
            "Indicates whether the creation of VNF templates and VNF appliances is enabled or not.",
            false);

    void persistVnfTemplate(long templateId, RegisterVnfTemplateCmd cmd);

    void updateVnfTemplate(long templateId, UpdateVnfTemplateCmd cmd);

    void validateVnfApplianceNics(VirtualMachineTemplate template, List<Long> networkIds);

    SecurityGroup createSecurityGroupForVnfAppliance(DataCenter zone, VirtualMachineTemplate template, Account owner, DeployVnfApplianceCmd cmd);

    void createIsolatedNetworkRulesForVnfAppliance(DataCenter zone, VirtualMachineTemplate template, Account owner,
                                                   UserVm vm, DeployVnfApplianceCmd cmd)
            throws InsufficientAddressCapacityException, ResourceAllocationException, ResourceUnavailableException;
}
