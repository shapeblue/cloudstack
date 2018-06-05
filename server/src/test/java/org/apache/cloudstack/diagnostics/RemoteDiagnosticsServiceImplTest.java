//
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
//


package org.apache.cloudstack.diagnostics;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import junit.framework.TestCase;
import org.apache.cloudstack.api.command.admin.diagnostics.RemoteDiagnosticsCmd;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteDiagnosticsServiceImplTest extends TestCase {

    @Mock
    private VMInstanceDao vmInstanceDao;
    @Mock
    private RemoteDiagnosticsCmd remoteDiagnosticsCmd;
    @InjectMocks
    private RemoteDiagnosticsServiceImpl diagnosticsService = new RemoteDiagnosticsServiceImpl();

    @Before
    public void setUp() throws Exception{
        MockitoAnnotations.initMocks(this);
        remoteDiagnosticsCmd = Mockito.mock(RemoteDiagnosticsCmd.class);
        Mockito.when(remoteDiagnosticsCmd.getId()).thenReturn(1L);
        Mockito.when(remoteDiagnosticsCmd.getAddress()).thenReturn("8.8.8.8");
        Mockito.when(remoteDiagnosticsCmd.getType()).thenReturn("ping");
        Mockito.when(remoteDiagnosticsCmd.getOptionalArguments()).thenReturn("-c");
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testExecuteDiagnosticsToolInSystemVmThrowsException() throws Exception {
        Mockito.when(vmInstanceDao.findByIdTypes(remoteDiagnosticsCmd.getId(), VirtualMachine.Type.ConsoleProxy,
                VirtualMachine.Type.DomainRouter, VirtualMachine.Type.SecondaryStorageVm)).thenReturn(null);
        diagnosticsService.executeDiagnosticsToolInSystemVm(remoteDiagnosticsCmd);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalCommandArgumentsThrowsException() throws Exception {
        diagnosticsService.setupRemoteCommand(remoteDiagnosticsCmd.getType(), remoteDiagnosticsCmd.getAddress(),
                "-c && sleep");
    }
}
