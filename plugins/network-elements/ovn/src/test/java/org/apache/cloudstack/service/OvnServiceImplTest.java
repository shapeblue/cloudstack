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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class OvnServiceImplTest {
    private final OvnNbClient mockClient = Mockito.mock(OvnNbClient.class);
    private final OvnServiceImpl service = new OvnServiceImpl(mockClient);

    @Test
    public void testDeterministicObjectNames() {
        Assert.assertEquals("cs-net-10", service.getLogicalSwitchName(10L));
        Assert.assertEquals("cs-vpc-20", service.getLogicalRouterName(20L));
        Assert.assertEquals("cs-nic-30", service.getLogicalSwitchPortName(30L));
    }

    @Test
    public void testConnectionStringValidationDelegatesToClient() {
        Mockito.when(mockClient.isValidConnectionString("tcp:127.0.0.1:6641")).thenReturn(true);
        Mockito.when(mockClient.isValidConnectionString("bogus")).thenReturn(false);
        Assert.assertTrue(service.isValidConnectionString("tcp:127.0.0.1:6641"));
        Assert.assertFalse(service.isValidConnectionString("bogus"));
    }

    @Test
    public void testVerifyNbConnectionDelegatesToClient() {
        service.verifyNbConnection("tcp:1.2.3.4:6641", null, null, null);
        Mockito.verify(mockClient).verifyConnection("tcp:1.2.3.4:6641", null, null, null);
    }
}
