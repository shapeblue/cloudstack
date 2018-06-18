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

import com.cloud.agent.AgentManager;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import junit.framework.TestCase;
import org.apache.cloudstack.api.command.admin.diagnostics.ExecuteDiagnosticsCmd;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class DiagnosticsServiceImplTest extends TestCase {

    @Mock
    private AgentManager agentManager;
    @Mock
    private VMInstanceDao instanceDao;
    @Mock
    private ExecuteDiagnosticsCmd diagnosticsCmd;
    @Mock
    private DiagnosticsCommand command;
    @Mock
    private VMInstanceVO instanceVO;
    @Mock
    private NicDao nicDao;
    @Mock
    private NicVO nicVO;
    @Mock
    private VirtualMachineManager vmManager;

    @InjectMocks
    private DiagnosticsServiceImpl diagnosticsService = new DiagnosticsServiceImpl();


    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(diagnosticsCmd);
        Mockito.reset(agentManager);
        Mockito.reset(instanceDao);
        Mockito.reset(instanceVO);
        Mockito.reset(nicVO);
        Mockito.reset(command);
    }

    @Test
    public void testRunDiagnosticsCommandTrue() throws Exception {
        Mockito.when(diagnosticsCmd.getType()).thenReturn(DiagnosticsType.PING);
        Mockito.when(diagnosticsCmd.getAddress()).thenReturn("8.8.8.8");
        Mockito.when(diagnosticsCmd.getId()).thenReturn(1L);
        Mockito.when(instanceDao.findByIdTypes(Mockito.anyLong(), Mockito.any(VirtualMachine.Type.class),
                Mockito.any(VirtualMachine.Type.class), Mockito.any(VirtualMachine.Type.class))).thenReturn(instanceVO);
        Mockito.when(nicDao.getControlNicForVM(Mockito.anyLong())).thenReturn(nicVO);


        Mockito.when(agentManager.easySend(Mockito.anyLong(), Mockito.any(DiagnosticsCommand.class))).thenReturn(new DiagnosticsAnswer(command, true, "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n" +
                "64 bytes from 8.8.8.8: icmp_seq=1 ttl=125 time=7.88 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=2 ttl=125 time=251 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=3 ttl=125 time=64.9 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=4 ttl=125 time=50.7 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=5 ttl=125 time=67.9 ms\n" +
                "\n" +
                "--- 8.8.8.8 ping statistics ---\n" +
                "5 packets transmitted, 5 received, 0% packet loss, time 4003ms\n" +
                "rtt min/avg/max/mdev = 7.881/88.587/251.410/84.191 ms}\n" +
                "}\n" +
                "0\n"));

        Map<String, String> detailsMap = diagnosticsService.runDiagnosticsCommand(diagnosticsCmd);

        String stdout = "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n" +
                "64 bytes from 8.8.8.8: icmp_seq=1 ttl=125 time=7.88 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=2 ttl=125 time=251 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=3 ttl=125 time=64.9 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=4 ttl=125 time=50.7 ms\n" +
                "64 bytes from 8.8.8.8: icmp_seq=5 ttl=125 time=67.9 ms\n" +
                "\n" +
                "--- 8.8.8.8 ping statistics ---\n" +
                "5 packets transmitted, 5 received, 0% packet loss, time 4003ms\n" +
                "rtt min/avg/max/mdev = 7.881/88.587/251.410/84.191 ms";

        assertEquals(3, detailsMap.size());
        assertEquals("Mismatch between actual and expected STDERR", "", detailsMap.get("STDERR"));
        assertEquals("Mismatch between actual and expected EXITCODE", "0", detailsMap.get("EXITCODE"));
        assertEquals("Mismatch between actual and expected STDOUT", stdout, detailsMap.get("STDOUT"));
    }

    @Test
    public void testRunDiagnosticsCommandFalse() throws Exception {
        Mockito.when(diagnosticsCmd.getType()).thenReturn(DiagnosticsType.PING);
        Mockito.when(diagnosticsCmd.getAddress()).thenReturn("8.8.8.");
        Mockito.when(diagnosticsCmd.getId()).thenReturn(1L);
        Mockito.when(instanceDao.findByIdTypes(Mockito.anyLong(), Mockito.any(VirtualMachine.Type.class),
                Mockito.any(VirtualMachine.Type.class), Mockito.any(VirtualMachine.Type.class))).thenReturn(instanceVO);
        Mockito.when(nicDao.getControlNicForVM(Mockito.anyLong())).thenReturn(nicVO);

        Mockito.when(agentManager.easySend(Mockito.anyLong(), Mockito.any(DiagnosticsCommand.class))).thenReturn(new DiagnosticsAnswer(command, false, "}\n" +
                "ping: unknown host}\n" +
                "1\n"));

        Map<String, String> detailsMap = diagnosticsService.runDiagnosticsCommand(diagnosticsCmd);

        assertEquals(3, detailsMap.size());
        assertEquals("Mismatch between actual and expected STDERR", "ping: unknown host", detailsMap.get("STDERR"));
        assertTrue("Mismatch between actual and expected EXITCODE", !detailsMap.get("EXITCODE").equalsIgnoreCase("0"));
        assertEquals("Mismatch between actual and expected STDOUT", "", detailsMap.get("STDOUT"));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testRunDiagnosticsThrowsInvalidParamException() throws Exception {
        Mockito.when(diagnosticsCmd.getType()).thenReturn(DiagnosticsType.PING);
        Mockito.when(diagnosticsCmd.getAddress()).thenReturn("8.8.8.");
        Mockito.when(diagnosticsCmd.getId()).thenReturn(1L);
        Mockito.when(instanceDao.findByIdTypes(Mockito.anyLong(), Mockito.any(VirtualMachine.Type.class),
                Mockito.any(VirtualMachine.Type.class), Mockito.any(VirtualMachine.Type.class))).thenReturn(null);

        Map<String, String> detailsMap = diagnosticsService.runDiagnosticsCommand(diagnosticsCmd);
    }
}