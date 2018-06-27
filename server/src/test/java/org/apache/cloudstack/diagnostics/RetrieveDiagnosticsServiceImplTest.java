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

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.ExecuteScriptCommand;
import com.cloud.agent.api.RetrieveFilesCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.host.HostVO;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import junit.framework.TestCase;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
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
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class RetrieveDiagnosticsServiceImplTest extends TestCase {
    private static final Logger LOGGER = Logger.getLogger(RetrieveDiagnosticsServiceImplTest.class);

    @Mock
    private AgentManager _agentManager;
    @Mock
    private VMInstanceDao instanceDao;
    @Mock
    private RetrieveDiagnosticsCmd retrieveDiagnosticsCmd;
    @Mock
    private RetrieveFilesCommand retrieveFilesCommand;
    @Mock
    private ExecuteScriptCommand executeScriptCommand;
    @Mock
    private VMInstanceVO instanceVO;
    @Mock
    private VirtualMachineManager vmManager;
    @Mock
    private NetworkOrchestrationService networkManager;
    @Mock
    private RetrieveDiagnosticsVO retrieveDiagnosticsVOMock;

    @Mock
    private RetrieveDiagnosticsDao retrieveDiagnosticsDao;

    @Mock
    private HostVO hostMock;

    @Mock
    private VMInstanceVO vmInstanceMock;

    @InjectMocks
    private RetrieveDiagnosticsServiceImpl retrieveDiagnosticsService = new RetrieveDiagnosticsServiceImpl();
    @InjectMocks
    ConfigurationManagerImpl configurationMgr = new ConfigurationManagerImpl();
    @InjectMocks
    RetrieveDiagnosticsVO retrieveDiagnosticsVO;

    RetrieveDiagnosticsCmd diagnosticsCmd = new RetrieveDiagnosticsCmd();

    private final String msCSVList = "/var/log/agent.log,/usr/data/management.log,/cloudstack/cloud.log,[IPTABLES]";
    private final List<String> msListDiagnosticsFiles = Arrays.asList(msCSVList.replace(" ","").split(","));
    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    private final String type = "LOGFILES";



    @Before
    public void setUp() throws Exception {
        Mockito.when(retrieveDiagnosticsCmd.getId()).thenReturn(2L);
        Mockito.when(retrieveDiagnosticsCmd.getType()).thenReturn("LOGFILES");
        Mockito.when(instanceDao.findByIdTypes(Mockito.anyLong(), Mockito.any(VirtualMachine.Type.class), Mockito.any(VirtualMachine.Type.class),
                Mockito.any(VirtualMachine.Type.class))).thenReturn(instanceVO);
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(retrieveDiagnosticsCmd);
        Mockito.reset(_agentManager);
        Mockito.reset(instanceDao);
        Mockito.reset(instanceVO);
        Mockito.reset(retrieveFilesCommand);
        Mockito.reset(executeScriptCommand);

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
        when(diagnosticsService.getDiagnosticsFiles(retrieveDiagnosticsCmd)).thenReturn(null);
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
        //when(diagnosticsService.createRetrieveDiagnosticsResponse()).thenReturn(retrieveDiagnosticsResponse);
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
    public void runRetrieveDiagnosticsFilesTrueTest() throws Exception {
        Mockito.when(retrieveDiagnosticsCmd.getType()).thenReturn(type);
        Mockito.when(retrieveDiagnosticsCmd.getOptionalListOfFiles()).thenReturn(msCSVList);
        Map<String, String> accessDetailsMap = new HashMap<>();
        accessDetailsMap.put(NetworkElementCommand.ROUTER_IP, "192.20.120.12");
        Mockito.when(networkManager.getSystemVMAccessDetails(Mockito.any(VMInstanceVO.class))).thenReturn(accessDetailsMap);
        final String details = "Copied files : "

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
