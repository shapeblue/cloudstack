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

import com.cloud.network.Network;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class OvnElementTest {
    @Test
    public void testGetProvider() {
        Assert.assertEquals(Network.Provider.Ovn, new OvnElement().getProvider());
    }

    @Test
    public void testCapabilitiesIncludeInitialOvnServices() {
        Map<Network.Service, Map<Network.Capability, String>> capabilities = new OvnElement().getCapabilities();

        Assert.assertTrue(capabilities.containsKey(Network.Service.Dhcp));
        Assert.assertTrue(capabilities.containsKey(Network.Service.Dns));
        Assert.assertTrue(capabilities.containsKey(Network.Service.SourceNat));
        Assert.assertTrue(capabilities.containsKey(Network.Service.StaticNat));
        Assert.assertTrue(capabilities.containsKey(Network.Service.PortForwarding));
        Assert.assertTrue(capabilities.containsKey(Network.Service.Firewall));
        Assert.assertTrue(capabilities.containsKey(Network.Service.NetworkACL));
        Assert.assertTrue(capabilities.containsKey(Network.Service.Lb));
        Assert.assertTrue(capabilities.containsKey(Network.Service.Gateway));
        Assert.assertTrue(capabilities.containsKey(Network.Service.Connectivity));
        Assert.assertFalse(capabilities.containsKey(Network.Service.SecurityGroup));
    }
}
