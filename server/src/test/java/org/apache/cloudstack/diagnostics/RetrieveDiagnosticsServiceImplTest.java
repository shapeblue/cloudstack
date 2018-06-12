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

import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.host.HostVO;
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
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class RetrieveDiagnosticsServiceImplTest {
    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImplTest.class);

    RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = new RetrieveDiagnosticsCmd();

    @Mock
    private RetrieveDiagnosticsServiceImpl diagnosticsService;

    private final String msCSVList = "agent.log, management.log, cloud.log, server.log";
    private final List<String> msListDiagnosticsFiles = Arrays.asList(msCSVList.replace(" ","").split(","));
    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    private final String type = "PROPERTYFILES";

    @InjectMocks
    ConfigurationManagerImpl configurationMgr = new ConfigurationManagerImpl();

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
        RetrieveDiagnostics diagnostic = mock(RetrieveDiagnosticsVO.class);
        RetrieveDiagnosticsServiceImpl diagnosticsService = mock(RetrieveDiagnosticsServiceImpl.class);
        HashMap<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys = new HashMap<String, List<DiagnosticsKey>>();
        when(diagnostic.getDefaultValue()).thenReturn(msCSVList);
        when(diagnosticsService.getDefaultDiagnosticsData()).thenReturn(allDefaultDiagnosticsTypeKeys);
    }

    @After
    public void tearDown() {
        CallContext.unregister();
    }

    @Test
    public void runConfigureTest() throws Exception {
        TransactionLegacy txn = TransactionLegacy.open("runConfigureTest");
        ConfigurationDao _configDao = mock(ConfigurationDao.class);
        when(_configDao.findById((Long.toString((anyLong()))))).thenReturn(null);
        try {
            _configDao.getValue(RetrieveDiagnosticsTimeOut.toString());
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Unable to find timeout value"));
        } finally {
            txn.close("runConfigureTest");
        }

    }

    @Test
    public void runGetListOfDiagnosticsKeyClassesTest() throws Exception {
        RetrieveDiagnosticsServiceImpl diagnosticsService = new RetrieveDiagnosticsServiceImpl();
        try {
            Map<String, List<DiagnosticsKey>> allDefaultDiagnosticsTypeKeys  = diagnosticsService.getDefaultDiagnosticsData();
            Assert.assertNotNull(allDefaultDiagnosticsTypeKeys);
        } catch (Exception e) {
            LOGGER.info("exception in testing runGetListOfDiagnosticsKeyClassesTest message: " + e.toString());
        } finally {

        }
    }

    @Test
    public void runLoadDiagnosticsDataConfigurationTest() throws Exception {
        RetrieveDiagnosticsServiceImpl diagnosticsService = mock(RetrieveDiagnosticsServiceImpl.class);
        when(diagnosticsService.getDefaultDiagnosticsData()).thenReturn(diagnosticsService.allDefaultDiagnosticsTypeKeys);

    }

    @Test
    public void runGetDiagnosticsFilesTest() throws Exception {
        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = mock(RetrieveDiagnosticsResponse.class);
        RetrieveDiagnosticsCmd retrieveDiagnosticsCmd = mock(RetrieveDiagnosticsCmd.class);
        RetrieveDiagnosticsServiceImpl diagnosticsService = mock(RetrieveDiagnosticsServiceImpl.class);
        when(diagnosticsService.getDiagnosticsFiles(retrieveDiagnosticsCmd)).thenReturn(retrieveDiagnosticsResponse);
    }

    @Test
    public void runcreateRetrieveDiagnosticsResponseTest() throws Exception {
        VMInstanceVO vmInstanceMock = mock(VMInstanceVO.class);
        HostVO hostMock = mock(HostVO.class);
        when(vmInstanceMock.getId()).thenReturn(1L);
        when(vmInstanceMock.getInstanceName()).thenReturn("sysVm");
        when(vmInstanceMock.getHostId()).thenReturn(2L);
        when(vmInstanceMock.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(hostMock.getId()).thenReturn(1L);
        RetrieveDiagnosticsResponse retrieveDiagnosticsResponse = mock(RetrieveDiagnosticsResponse.class);
        RetrieveDiagnosticsServiceImpl diagnosticsService = mock(RetrieveDiagnosticsServiceImpl.class);
        when(diagnosticsService.createRetrieveDiagnosticsResponse(hostMock)).thenReturn(retrieveDiagnosticsResponse);
    }

    @Test
    public void runGetAllDefaultFilesForEachSystemVmTest() throws Exception {
        final String msCSVList = "agent.log,management.log,cloud.log";
        final String[] msList = msCSVList.split(",");
        RetrieveDiagnosticsVO retrieveDiagnosticsVOMock = mock(RetrieveDiagnosticsVO.class);
        RetrieveDiagnosticsServiceImpl diagnosticsService = mock(RetrieveDiagnosticsServiceImpl.class);
        when(diagnosticsService.getAllDefaultFilesForEachSystemVm(retrieveDiagnosticsVOMock.getType())).thenReturn(msList);
    }

    @Test
    public void runGetDefaultFilesForVmTest() throws Exception {
        RetrieveDiagnosticsServiceImpl diagnosticsService = new RetrieveDiagnosticsServiceImpl();
        DiagnosticsKey key = new DiagnosticsKey("ConsoleProxy", "LOGFILES", "agent.log. management.log,cloud.log", "");
        try {

            String[] allDefaultDiagnosticsTypeKeys  = diagnosticsService.getDefaultFilesForVm(key.getDiagnosticsClassType(), "myVm");
            Assert.assertNotNull(allDefaultDiagnosticsTypeKeys);
        } catch (Exception e) {
            LOGGER.info("exception in testing runGetDefaultFilesForVmTest message: " + e.toString());
        } finally {

        }
    }

    @Test
    public void runRetrieveDiagnosticsFilesTest() throws Exception {
        RetrieveDiagnosticsServiceImpl diagnosticsService = new RetrieveDiagnosticsServiceImpl();
        try {
            String[] allDefaultDiagnosticsTypeKeys  = diagnosticsService.getDefaultFilesForVm("SecondaryStorageVm", "myVm");
            Assert.assertNotNull(allDefaultDiagnosticsTypeKeys);
        } catch (Exception e) {
            LOGGER.info("exception in testing runRetrieveDiagnosticsFilesTest message: " + e.toString());
        } finally {

        }
    }

}
