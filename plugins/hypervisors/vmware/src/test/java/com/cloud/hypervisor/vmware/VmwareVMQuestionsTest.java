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

package com.cloud.hypervisor.vmware;

import com.cloud.hypervisor.vmware.mo.DatacenterMO;
import com.cloud.hypervisor.vmware.mo.VirtualMachineMO;
import com.cloud.hypervisor.vmware.util.VmwareClient;
import com.cloud.hypervisor.vmware.util.VmwareContext;
import com.vmware.vim25.VirtualMachineQuestionInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VmwareVMQuestionsTest {

    private String vCenterIp = "10.2.2.86";
    private String username = "administrator@vsphere.local";
    private String password = "P@ssword123";
    private String datacenterName = "Trillian";

    private String applianceTemplate = "836337534f7e35fa94910b4cb45547a8";
    private String vmFromAppliance = "i-2-3-VM";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testConnectToVmwareDatacenter() throws Exception {
        VmwareClient vimClient = new VmwareClient(vCenterIp);
        String serviceUrl = "https://" + vCenterIp + "/sdk/vimService";
        vimClient.connect(serviceUrl, username, password);
        VmwareContext context = new VmwareContext(vimClient, vCenterIp);
        DatacenterMO dataCenterMO = new DatacenterMO(context, datacenterName);
        VirtualMachineMO vm = dataCenterMO.findVm(applianceTemplate);
        VirtualMachineQuestionInfo questions = vm.getRuntimeInfo().getQuestion();
    }
}
