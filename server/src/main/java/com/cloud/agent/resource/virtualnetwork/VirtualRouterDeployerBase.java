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
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.resource.ResourceManager;
import com.cloud.utils.ExecutionResult;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.ssh.SSHCmdHelper;
import com.cloud.utils.ssh.SshHelper;
import com.cloud.vm.DomainRouterVO;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;
import org.joda.time.Duration;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.HashMap;
import java.util.Map;

public class VirtualRouterDeployerBase extends ManagerBase implements Manager, VirtualRouterDeployer {
    private static final Logger s_logger = Logger.getLogger(VirtualRouterDeployerBase.class);
    VirtualRoutingResource _resource;

    @Inject private AgentManager _agentMgr;
    @Inject private ConfigurationDao _configDao;
    @Inject private HostDao _hostDao;

    private static final String SSH_COMMAND = "ssh -p 3922 -i /root/.ssh/id_rsa.cloud root@%s /opt/cloud/bin/%s %s";
    private static final String SCP_COMMAND = "scp -P 3922 -i /root/.ssh/id_rsa.cloud %s root@%s:/var/cache/cloud/";

    @Override
    public boolean configure(final String name, Map<String, Object> params) throws ConfigurationException {
        s_logger.debug("Configuring VirtualRouterDeployerBase");

        _resource = new VirtualRoutingResource(this);
        try {
            _resource.configure(name, new HashMap<String, Object>());
        } catch (final ConfigurationException e) {
            throw new CloudRuntimeException("Error while configuring VirtualRouterDeployerBase:", e);
        }

        return true;
    }

    @Override
    public ExecutionResult executeInVR(final Long hostId, String routerIp, String script, String args) {
        return executeInVR(hostId, routerIp, script, args, VRScripts.VR_SCRIPT_EXEC_TIMEOUT);
    }

    @Override
    public ExecutionResult executeInVR(final Long hostId, String routerIp, String script, String args, Duration timeout) {
        HostVO host = getHostDetails(hostId);
        final String password = host.getDetail("password");
        final String username = host.getDetail("username");
        final com.trilead.ssh2.Connection connection = SSHCmdHelper.acquireAuthorizedConnection(
                host.getPrivateIpAddress(), 22, username, password);
        if (connection == null) {
            throw new CloudRuntimeException(String.format("SSH to agent is enabled, but failed to connect to %s via IP address [%s].", host, host.getPrivateIpAddress()));
        }
        SSHCmdHelper.SSHCmdResult result = SSHCmdHelper.sshExecuteCmdWithResult(connection, String.format(SSH_COMMAND, routerIp, script, args == null ? "" : args));
        if (result.getReturnCode() != 0) {
            throw new CloudRuntimeException(String.format("Could not execute command in VR %s on %s due to: %s", routerIp, host, result.getStdErr()));
        }
        s_logger.debug("Execute command in VR result: " + result.getStdOut());
        return new ExecutionResult(true, result.getStdOut());
    }

    @Override
    public ExecutionResult createFileInVR(final Long hostId, String routerIp, String path, String filename, String content) {
        HostVO host = getHostDetails(hostId);
        final String password = host.getDetail("password");
        final String username = host.getDetail("username");
        try {
            SshHelper.scpTo(host.getPrivateIpAddress(), 22, username, null, password, VRScripts.CONFIG_CACHE_LOCATION, String.format(SCP_COMMAND, filename, routerIp), null);
        } catch (Exception e) {
            throw new CloudRuntimeException("Exception while creating file in VR:", e);
        }
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

    private HostVO getHostDetails(Long hostId) {
        final boolean sshToAgent = Boolean.parseBoolean(_configDao.getValue(ResourceManager.KvmSshToAgentEnabled.key()));
        if (!sshToAgent) {
            throw new CloudRuntimeException("SSH access is disabled, cannot execute command in VR");
        }
        HostVO host = _hostDao.findById(hostId);
        _hostDao.loadDetails(host);
        return host;
    }
}
