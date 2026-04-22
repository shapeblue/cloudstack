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
package org.apache.cloudstack.service;

import com.cloud.network.ovn.OvnService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

public class OvnServiceImpl implements OvnService, Configurable {
    private final OvnNbClient ovnNbClient = new OvnNbClient();

    @Override
    public String getLogicalSwitchName(long networkId) {
        return String.format("cs-net-%d", networkId);
    }

    @Override
    public String getLogicalRouterName(long vpcId) {
        return String.format("cs-vpc-%d", vpcId);
    }

    @Override
    public String getLogicalSwitchPortName(long nicId) {
        return String.format("cs-nic-%d", nicId);
    }

    @Override
    public boolean isValidConnectionString(String connection) {
        return ovnNbClient.isValidConnectionString(connection);
    }

    @Override
    public String getConfigComponentName() {
        return OvnService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[0];
    }
}
