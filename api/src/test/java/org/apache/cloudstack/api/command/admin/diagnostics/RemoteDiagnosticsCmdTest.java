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

package org.apache.cloudstack.api.command.admin.diagnostics;

import junit.framework.TestCase;
import org.apache.cloudstack.api.ApiCmdTestUtil;
import org.apache.cloudstack.diangosis.RemoteDiagnosticsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteDiagnosticsCmdTest extends TestCase {

    @Mock
    private RemoteDiagnosticsService diagnosticsService;

    @InjectMocks
    private RemoteDiagnosticsCmd remoteDiagnosticsCmd = new RemoteDiagnosticsCmd();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected =IllegalStateException.class)
    public void testUnsupportedDiagnosticsType() throws Exception{
        ApiCmdTestUtil.set(remoteDiagnosticsCmd, "type", "unknownType");
        remoteDiagnosticsCmd.execute();
    }

    @Test
    public void testPingCommand() throws Exception{
        ApiCmdTestUtil.set(remoteDiagnosticsCmd, "type", "ping");
        ApiCmdTestUtil.set(remoteDiagnosticsCmd, "id", 1L);
        ApiCmdTestUtil.set(remoteDiagnosticsCmd, "address", "8.8.8.8");
        ApiCmdTestUtil.set(remoteDiagnosticsCmd, "optionalArgument", "-c 5");
        remoteDiagnosticsCmd.execute();
        Mockito.verify(diagnosticsService, Mockito.times(1)).executeDiagnosticsToolInSystemVm(remoteDiagnosticsCmd);
    }
}