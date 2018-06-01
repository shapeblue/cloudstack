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

import org.apache.cloudstack.diangosis.RemoteDiagnosticsService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteDiagnosticsCmdTest {

    private RemoteDiagnosticsCmd remoteDiagnosticsCmd;

    @Mock
    private RemoteDiagnosticsService diagnosticsService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        remoteDiagnosticsCmd = new RemoteDiagnosticsCmd();
    }

    @Test
    public void testPingCommand(){

    }

    @Test
    public void testTracerouteCommand(){

    }

    @Test
    public void testArpingCommand(){

    }

    @After
    public void tearDown() throws Exception {
    }
}