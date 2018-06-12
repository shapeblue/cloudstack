/*
 * // Licensed to the Apache Software Foundation (ASF) under one
 * // or more contributor license agreements.  See the NOTICE file
 * // distributed with this work for additional information
 * // regarding copyright ownership.  The ASF licenses this file
 * // to you under the Apache License, Version 2.0 (the
 * // "License"); you may not use this file except in compliance
 * // with the License.  You may obtain a copy of the License at
 * //
 * //   http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing,
 * // software distributed under the License is distributed on an
 * // "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * // KIND, either express or implied.  See the License for the
 * // specific language governing permissions and limitations
 * // under the License.
 */

/*
 * // Licensed to the Apache Software Foundation (ASF) under one
 * // or more contributor license agreements.  See the NOTICE file
 * // distributed with this work for additional information
 * // regarding copyright ownership.  The ASF licenses this file
 * // to you under the Apache License, Version 2.0 (the
 * // "License"); you may not use this file except in compliance
 * // with the License.  You may obtain a copy of the License at
 * //
 * //   http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing,
 * // software distributed under the License is distributed on an
 * // "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * // KIND, either express or implied.  See the License for the
 * // specific language governing permissions and limitations
 * // under the License.
 */

/*
 * // Licensed to the Apache Software Foundation (ASF) under one
 * // or more contributor license agreements.  See the NOTICE file
 * // distributed with this work for additional information
 * // regarding copyright ownership.  The ASF licenses this file
 * // to you under the Apache License, Version 2.0 (the
 * // "License"); you may not use this file except in compliance
 * // with the License.  You may obtain a copy of the License at
 * //
 * //   http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing,
 * // software distributed under the License is distributed on an
 * // "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * // KIND, either express or implied.  See the License for the
 * // specific language governing permissions and limitations
 * // under the License.
 */

package org.apache.cloudstack.diagnostics;

import com.cloud.host.HostVO;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnostics;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsDao;
import org.apache.cloudstack.framework.config.impl.RetrieveDiagnosticsVO;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class RetrieveDiagnosticsServiceImplTest {
    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImplTest.class);

    RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = new RetrieveDiagnosticsCmd();

    @Spy
    @InjectMocks
    private RetrieveDiagnosticsServiceImpl diagnosticsService = new RetrieveDiagnosticsServiceImpl();

    private final String msCSVList = "agent.log, management.log, cloud.log, server.log";
    private final List<String> msListDiagnosticsFiles = Arrays.asList(msCSVList.replace(" ","").split(","));
    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    private final String type = "PROPERTYFILES";

    @Mock
    ConfigurationDao _configDao;

    @Mock
    private RetrieveDiagnosticsVO retrieveDiagnosticsVOMock;

    @Mock
    private RetrieveDiagnosticsDao retrieveDiagnosticsDao;

    @Mock
    private HostVO hostMock;

    @Mock
    private VMInstanceVO vmInstanceMock;


    @Before
    public void setUp() {
        ComponentContext.initComponentsLifeCycle();

        RetrieveDiagnostics diagnostic = new RetrieveDiagnosticsVO("SecondaryStorageVm", "LOGFILES", "agent.log,management.log,cloud.log");
        when(_configDao.getValue("retrieveDiagnostics.retrieval.timeout")).thenReturn(RetrieveDiagnosticsTimeOut.toString());
        doNothing().when(diagnosticsService.RetrieveDiagnosticsTimeOut).value().longValue();
        when(retrieveDiagnosticsCmd.getOptionalListOfFiles()).thenReturn(msCSVList);
        when(diagnosticsService.getDefaultDiagnosticsData().get(new DiagnosticsKey("ConsoleProxy", "LOGFILES", "agent.log,cloud.log,management.log","ConsoleProxy System VM")).add(new DiagnosticsKey())).thenReturn(true);

    }

    @After
    public void tearDown() {
        CallContext.unregister();
    }

    @Test
    public void testRetrieveDiagnostics() throws Exception {

        LOGGER.info("Running tests for RetrieveDiagnostics API");

        runConfigureTest();

        runGetListOfDiagnosticsKeyClassesTest();

        runLoadDiagnosticsDataConfigurationTest();

        runGetDiagnosticsFilesTest();

        runcreateRetrieveDiagnosticsResponseTest();

        runGetAllDefaultFilesForEachSystemVmTest();

        runGetDefaultFilesForVmTest();

        runRetrieveDiagnosticsFilesTest();


    }

    void runConfigureTest() throws Exception {
        TransactionLegacy txn = TransactionLegacy.open("runConfigureTest");
        when(_configDao.findById((Long.toString((anyLong()))))).thenReturn(null);
        try {
            _configDao.getValue(RetrieveDiagnosticsTimeOut.toString());
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Unable to find timeout value"));
        } finally {
            txn.close("runConfigureTest");
        }

    }

    void runGetListOfDiagnosticsKeyClassesTest() throws Exception {
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("DHCPFILES") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("DHCPFILES")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("LOGFILES") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("LOGFILES")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("PROPERTYFILES") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("PROPERTYFILES")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("LB") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("LB")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("DNS") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("DNS")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("IPTABLES.retrieve") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("IPTABLES.retrieve")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("VPN") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("VPN")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("USERDATA") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("USERDATA")));
        assertTrue((diagnosticsService.getDefaultDiagnosticsData().containsKey("IPTABLES.remove") == diagnosticsService.allDefaultDiagnosticsTypeKeys.containsValue("IPTABLES.remove")));
    }

    void runLoadDiagnosticsDataConfigurationTest() throws Exception {
        when(diagnosticsService.getDefaultDiagnosticsData()).thenReturn(diagnosticsService.allDefaultDiagnosticsTypeKeys);

    }

    void runGetDiagnosticsFilesTest() throws Exception {
        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = mock(RetrieveDiagnosticsResponse.class);
        RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = mock(RetrieveDiagnosticsCmd.class);
        when(diagnosticsService.getDiagnosticsFiles(retrieveDiagnosticsCmd)).thenReturn(retrieveDiagnosticsResponse);
    }

    void runcreateRetrieveDiagnosticsResponseTest() throws Exception {
        when(vmInstanceMock.getId()).thenReturn(1L);
        when(vmInstanceMock.getInstanceName()).thenReturn("sysVm");
        when(vmInstanceMock.getHostId()).thenReturn(2L);
        when(vmInstanceMock.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(hostMock.getId()).thenReturn(1L);
        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = mock(RetrieveDiagnosticsResponse.class);
        when(diagnosticsService.createRetrieveDiagnosticsResponse(hostMock)).thenReturn(retrieveDiagnosticsResponse);

    }

    void runGetAllDefaultFilesForEachSystemVmTest() throws Exception {
        final String msCSVList = "agent.log,management.log,cloud.log";
        final String[] msList = msCSVList.split(",");

        RetrieveDiagnosticsVO retrieveDiagnosticsVOMock = mock(RetrieveDiagnosticsVO.class);
        when(diagnosticsService.getAllDefaultFilesForEachSystemVm(retrieveDiagnosticsVOMock.getType())).thenReturn(msList);
    }

    void runGetDefaultFilesForVmTest() throws Exception {
        final String msCSVList = "agent.log,management.log,cloud.log";
        final String[] msList = msCSVList.split(",");
        RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = mock(RetrieveDiagnosticsCmd.class);
        when(diagnosticsService.getDefaultFilesForVm(retrieveDiagnosticsCmd.getType(), Long.valueOf(hostMock.getId()).toString())).thenReturn(msList);
    }

    void runRetrieveDiagnosticsFilesTest() throws Exception {
        VMInstanceVO systemVm = mock(VMInstanceVO.class);
        RetrieveDiagnosticsVO retrieveDiagnosticsVO = Mockito.mock(RetrieveDiagnosticsVO.class);

        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(retrieveDiagnosticsVO.getDefaultValue());
        RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = mock(RetrieveDiagnosticsCmd.class);
        when(diagnosticsService.retrieveDiagnosticsFiles(retrieveDiagnosticsCmd, hostMock.getId(), "DHCPFILES", arrayList, systemVm));
    }

}
