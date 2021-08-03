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

package com.cloud.agent.resource.virtualnetwork;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.GetRouterMonitorResultsCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.ExecutionResult;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.vm.DomainRouterVO;
import org.apache.log4j.Logger;
import org.joda.time.Duration;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.HashMap;
import java.util.Map;

public class VirtualRouterDeployerBase extends ManagerBase implements Manager, VirtualRouterDeployer {
    private static final Logger s_logger = Logger.getLogger(VirtualRouterDeployerBase.class);
    VirtualRoutingResource _resource;

    @Inject
    protected AgentManager _agentMgr;

    @Override
    public boolean configure(final String name, Map<String, Object> params) throws ConfigurationException {
        s_logger.debug("Configuring VirtualRouterDeployerBase");

        _resource = new VirtualRoutingResource(this);
        try {
            _resource.configure(name, new HashMap<String, Object>());
        } catch (final ConfigurationException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public ExecutionResult executeInVR(final String hostIp, String routerIp, String script, String args) {
        return null;
    }

    @Override
    public ExecutionResult executeInVR(final String hostIp, String routerIp, String script, String args, Duration timeout) {
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult createFileInVR(final String hostIp, String routerIp, String path, String filename, String content) {
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult prepareCommand(NetworkElementCommand cmd) {
        return new ExecutionResult(true, null);
    }

    @Override
    public ExecutionResult cleanupCommand(NetworkElementCommand cmd) {
        return new ExecutionResult(true, null);
    }

    public Answer executeRequest(final DomainRouterVO router, final NetworkElementCommand cmd) {
        if (router.getHypervisorType() == Hypervisor.HypervisorType.VMware) {
            return _agentMgr.easySend(router.getHostId(), cmd);
        } else if (router.getHypervisorType() == Hypervisor.HypervisorType.KVM || router.getHypervisorType() == Hypervisor.HypervisorType.XenServer) {
            if (cmd != null && cmd instanceof GetRouterMonitorResultsCommand) {
                return _resource.executeRequest(cmd);
            }
        }
        return _agentMgr.easySend(router.getHostId(), cmd);
    }
}
