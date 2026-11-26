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

package org.apache.cloudstack.kvm.ha;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckOnHostAnswer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceManager;

@RunWith(MockitoJUnitRunner.class)
public class KVMHostActivityCheckerTest {

    private static final long CLUSTER_ID = 1L;

    @Mock
    private AgentManager agentManager;
    @Mock
    private ResourceManager resourceManager;
    @InjectMocks
    private KVMHostActivityChecker checker;

    private final Host host = mock(Host.class);
    private final HostVO firstNeighbour = mock(HostVO.class);
    private final HostVO secondNeighbour = mock(HostVO.class);

    @Before
    public void setUp() {
        when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        when(host.getId()).thenReturn(10L);
        when(host.getClusterId()).thenReturn(CLUSTER_ID);
        when(firstNeighbour.getId()).thenReturn(11L);
        when(firstNeighbour.getHypervisorType()).thenReturn(HypervisorType.KVM);
        when(secondNeighbour.getId()).thenReturn(12L);
        when(secondNeighbour.getHypervisorType()).thenReturn(HypervisorType.KVM);
        when(resourceManager.listHostsInClusterByStatus(CLUSTER_ID, Status.Up)).thenReturn(Arrays.asList(firstNeighbour, secondNeighbour));
    }

    private Answer answer(Boolean alive) {
        return new CheckOnHostAnswer(mock(CheckOnHostCommand.class), alive, "details");
    }

    /** The host does not answer for itself: it is not reachable. */
    private void hostIsNotReachable() {
        when(agentManager.easySend(eq(10L), any(CheckOnHostCommand.class))).thenReturn(null);
    }

    private void neighbourAnswers(HostVO neighbour, Answer answer) {
        when(agentManager.easySend(eq(neighbour.getId()), any(CheckOnHostCommand.class))).thenReturn(answer);
    }

    @Test
    public void testNeighbourWhichCanNotTellIsSkippedAndTheNextOneDecides() {
        hostIsNotReachable();
        neighbourAnswers(firstNeighbour, answer(null));
        neighbourAnswers(secondNeighbour, answer(false));

        // the first neighbour monitors no pool, e.g. an RBD host without the bindings: it must not keep the others from deciding
        assertEquals(Status.Down, checker.getHostAgentStatus(host));
    }

    @Test
    public void testNeighbourWhichCanNotTellDoesNotMakeTheHostUp() {
        hostIsNotReachable();
        neighbourAnswers(firstNeighbour, answer(null));
        neighbourAnswers(secondNeighbour, answer(true));

        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
    }

    @Test
    public void testNeighbourWhichSaysAliveIsStillEnough() {
        hostIsNotReachable();
        neighbourAnswers(firstNeighbour, answer(true));

        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
        verify(agentManager, never()).easySend(eq(12L), any(CheckOnHostCommand.class));
    }

    @Test
    public void testHostIsNotDeadIfNoNeighbourCanTell() {
        hostIsNotReachable();
        neighbourAnswers(firstNeighbour, answer(null));
        neighbourAnswers(secondNeighbour, answer(null));

        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
    }

    @Test
    public void testHostWhichCanNotTellItsOwnStatusIsUpAndNeighboursAreNotAsked() {
        // it answered, so it is there, as before when a host with no pool to check answered that its heart is beating
        when(agentManager.easySend(eq(10L), any(CheckOnHostCommand.class))).thenReturn(answer(null));

        assertEquals(Status.Up, checker.getHostAgentStatus(host));
        verify(agentManager, never()).easySend(eq(11L), any(CheckOnHostCommand.class));
    }
}
