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
import org.apache.cloudstack.diangostics.DiagnosticsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExecuteDiagnosticsCmdTest extends TestCase {

    @Mock
    private DiagnosticsService diagnosticsService;

    @InjectMocks
    private ExecuteDiagnosticsCmd executeDiagnosticsCmd = new ExecuteDiagnosticsCmd();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

//    @Test(expected = IllegalStateException.class)
//    public void testUnsupportedDiagnosticsType() throws Exception {
//        ApiCmdTestUtil.set(executeDiagnosticsCmd, "type", "unknownType");
//        executeDiagnosticsCmd.execute();
//    }

    @Test
    public void testPingCommand() throws Exception {
        ApiCmdTestUtil.set(executeDiagnosticsCmd, "type", "ping");
        ApiCmdTestUtil.set(executeDiagnosticsCmd, "id", 1L);
        ApiCmdTestUtil.set(executeDiagnosticsCmd, "address", "8.8.8.8");
        ApiCmdTestUtil.set(executeDiagnosticsCmd, "optionalArgument", "-c 5");
        executeDiagnosticsCmd.execute();
        Mockito.verify(diagnosticsService, Mockito.times(1)).runDiagnosticsCommand(executeDiagnosticsCmd);
    }
}