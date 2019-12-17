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
package org.apache.cloudstack.hyperv.discoverer;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.ConfigurationException;

import org.apache.cloudstack.hyperv.HypervClient;
import org.apache.cloudstack.hyperv.resource.HypervDirectConnectResource;
import org.apache.log4j.Logger;

import com.cloud.agent.Listener;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.SetupAnswer;
import com.cloud.agent.api.SetupCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.exception.ConnectionException;
import com.cloud.exception.DiscoveryException;
import com.cloud.host.Host;
import com.cloud.host.HostEnvironment;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.Discoverer;
import com.cloud.resource.DiscovererBase;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.utils.Pair;

public class HypervServerDiscoverer extends DiscovererBase implements Discoverer, Listener, ResourceStateAdapter {
    private static final Logger LOG = Logger.getLogger(HypervServerDiscoverer.class);

    @Override
    public final boolean processAnswers(final long agentId, final long seq, final Answer[] answers) {
        return false;
    }

    @Override
    public final boolean processCommands(final long agentId, final long seq, final Command[] commands) {
        return false;
    }

    @Override
    public final AgentControlAnswer processControlCommand(final long agentId, final AgentControlCommand cmd) {
        return null;
    }

    @Override
    public void processHostAdded(long hostId) {
    }

    @Override
    public final void processConnect(final Host agent, final StartupCommand cmd, final boolean forRebalance) throws ConnectionException {
        // Limit the commands we can process
        if (!(cmd instanceof StartupRoutingCommand)) {
            return;
        }

        StartupRoutingCommand startup = (StartupRoutingCommand)cmd;

        // assert
        if (startup.getHypervisorType() != HypervisorType.Hyperv) {
            LOG.debug("Not Hyper-V hypervisor, so moving on.");
            return;
        }

        long agentId = agent.getId();
        HostVO host = _hostDao.findById(agentId);

        // Our Hyper-V machines are not participating in pools, and the pool id
        // we provide them is not persisted.
        // This means the pool id can vary.
        ClusterVO cluster = _clusterDao.findById(host.getClusterId());
        if (cluster.getGuid() == null) {
            cluster.setGuid(startup.getPool());
            _clusterDao.update(cluster.getId(), cluster);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Setting up host " + agentId);
        }

        HostEnvironment env = new HostEnvironment();
        SetupCommand setup = new SetupCommand(env);
        if (!host.isSetup()) {
            setup.setNeedSetup(true);
        }

        try {
            SetupAnswer answer = null;
            if (answer != null && answer.getResult()) {
                host.setSetup(true);
                // TODO: clean up magic numbers below
                host.setLastPinged((System.currentTimeMillis() >> 10) - 5 * 60);
                _hostDao.update(host.getId(), host);
                if (answer.needReconnect()) {
                    throw new ConnectionException(false, "Reinitialize agent after setup.");
                }
                return;
            } else {
                String reason = answer.getDetails();
                if (reason == null) {
                    reason = " details were null";
                }
                LOG.warn("Unable to setup agent " + agentId + " due to " + reason);
            }
            // Error handling borrowed from XcpServerDiscoverer, may need to be
            // updated.
        } catch (Exception e) {
            LOG.warn("Unable to setup agent " + agentId + " because it became unavailable.", e);
        }
        throw new ConnectionException(true, "Reinitialize agent after setup.");
    }

    @Override
    public final boolean processDisconnect(final long agentId, final Status state) {
        return false;
    }

    @Override
    public void processHostAboutToBeRemoved(long hostId) {
    }

    @Override
    public void processHostRemoved(long hostId, long clusterId) {
    }

    @Override
    public final boolean isRecurring() {
        return false;
    }

    @Override
    public final int getTimeout() {
        return 0;
    }

    @Override
    public final boolean processTimeout(final long agentId, final long seq) {
        return false;
    }

    @Override
    public final Map<? extends ServerResource, Map<String, String>> find(final long dcId, final Long podId, final Long clusterId,
                                                                         final URI uri, final String username, final String password,
                                                                         final List<String> hostTags) throws DiscoveryException {

        if (LOG.isInfoEnabled()) {
            LOG.info("Discover Hyper-V host in zone: " + dcId + ", pod: " + podId + ", cluster: " + clusterId + ", uri host: " + uri.getHost());
        }

        ClusterVO cluster = _clusterDao.findById(clusterId);
        if (cluster == null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("No cluster in database for cluster id " + clusterId);
            }
            return null;
        }
        if (cluster.getHypervisorType() != HypervisorType.Hyperv) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Cluster " + clusterId + "is not for Hyperv hypervisors");
            }
            return null;
        }

        if (!uri.getScheme().equals("http")) {
            String msg = "Only http scheme is allowed for the host, provided URI: " + uri;
            LOG.debug(msg);
            return null;
        }

        try {
            String hostname = uri.getHost();
            InetAddress ia = InetAddress.getByName(hostname);
            String hostIp = ia.getHostAddress();

            HypervClient client = new HypervClient(hostIp, username, password);
            Pair<Long, Long> cpuInfo = client.getHostCpuInfo();
            Long memory = client.getHostMemory();

            String guidWithTail = calcServerResourceGuid(hostIp) + "-HypervResource";

            if (_resourceMgr.findHostByGuid(guidWithTail) != null) {
                LOG.debug("Skipping " + hostIp + " because " + guidWithTail + " is already in the database.");
                return null;
            }

            LOG.info("Creating" + HypervDirectConnectResource.class.getName() + " HypervDirectConnectResource for zone/pod/cluster " + dcId + "/" + podId + "/" +
                clusterId);

            if (cluster.getGuid() == null) {
                cluster.setGuid(UUID.nameUUIDFromBytes(String.valueOf(clusterId).getBytes(StandardCharsets.UTF_8)).toString());
                _clusterDao.update(clusterId, cluster);
            }

            // Settings required by all server resources managing a hypervisor
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("zone", Long.toString(dcId));
            params.put("pod", Long.toString(podId));
            params.put("cluster", Long.toString(clusterId));
            params.put("guid", guidWithTail);
            params.put("ipaddress", hostIp);
            params.put("cpucore", Long.toString(cpuInfo.first()));
            params.put("cpuspeed", Long.toString(cpuInfo.second()));
            params.put("memory", Long.toString(memory));

            // Hyper-V specific settings
            Map<String, String> details = new HashMap<String, String>();
            details.put("url", uri.getHost());
            details.put("username", username);
            details.put("password", password);
            details.put("cluster.guid", cluster.getGuid());

            params.putAll(details);

            HypervDirectConnectResource resource = new HypervDirectConnectResource(client);
            resource.configure(hostIp, params);

            // Assert
            // TODO: test by using bogus URL and bogus virtual path in URL
            ReadyCommand ping = new ReadyCommand();
            Answer pingAns = resource.executeRequest(ping);
            if (pingAns == null || !pingAns.getResult()) {
                String errMsg = "Agent not running, or no route to agent on at " + uri;
                LOG.debug(errMsg);
                throw new DiscoveryException(errMsg);
            }

            Map<HypervDirectConnectResource, Map<String, String>> resources = new HashMap<HypervDirectConnectResource, Map<String, String>>();
            resources.put(resource, details);
            return resources;
        } catch (ConfigurationException e) {
            LOG.warn("Unable to instantiate " + uri.getHost(), e);
        } catch (Exception e) {
            LOG.warn("Failed to add Hyper-V host: ", e);
        }
        return null;
    }

    public static String calcServerResourceGuid(final String uuidSeed) {
        String guid = UUID.nameUUIDFromBytes(uuidSeed.getBytes(Charset.forName("UTF-8"))).toString();
        return guid;
    }

    @Override
    public final boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);

        _resourceMgr.registerResourceStateAdapter(this.getClass().getSimpleName(), this);
        return true;
    }

    // end of Adapter

    @Override
    public void postDiscovery(final List<HostVO> hosts, final long msId) throws DiscoveryException {
    }

    @Override
    public final Hypervisor.HypervisorType getHypervisorType() {
        return Hypervisor.HypervisorType.Hyperv;
    }

    @Override
    public final boolean matchHypervisor(final String hypervisor) {
        if (hypervisor == null) {
            return true;
        }
        return Hypervisor.HypervisorType.Hyperv.toString().equalsIgnoreCase(hypervisor);
    }

    @Override
    public final HostVO createHostVOForConnectedAgent(final HostVO host, final StartupCommand[] cmd) {
        return null;
    }

    @Override
    public final HostVO createHostVOForDirectConnectAgent(final HostVO host, final StartupCommand[] startup, final ServerResource resource,
        final Map<String, String> details, final List<String> hostTags) {
        StartupCommand firstCmd = startup[0];
        if (!(firstCmd instanceof StartupRoutingCommand)) {
            return null;
        }

        StartupRoutingCommand ssCmd = ((StartupRoutingCommand)firstCmd);
        if (ssCmd.getHypervisorType() != HypervisorType.Hyperv) {
            return null;
        }

        LOG.info("Host: " + host.getName() + " connected with hypervisor type: " + HypervisorType.Hyperv + ". Checking CIDR...");

        HostPodVO pod = null;//_podDao.findById(host.getPodId());
        DataCenterVO dc = _dcDao.findById(host.getDataCenterId());

        _resourceMgr.checkCIDR(pod, dc, ssCmd.getPrivateIpAddress(), ssCmd.getPrivateNetmask());

        return _resourceMgr.fillRoutingHostVO(host, ssCmd, HypervisorType.Hyperv, details, hostTags);
    }

    // TODO: add test for method
    @Override
    public final DeleteHostAnswer deleteHost(final HostVO host, final boolean isForced, final boolean isForceDeleteStorage) throws UnableDeleteHostException {
        // assert
        if (host.getType() != Host.Type.Routing || host.getHypervisorType() != HypervisorType.Hyperv) {
            return null;
        }
        _resourceMgr.deleteRoutingHost(host, isForced, isForceDeleteStorage);
        return new DeleteHostAnswer(true);
    }

    @Override
    public final boolean stop() {
        _resourceMgr.unregisterResourceStateAdapter(this.getClass().getSimpleName());
        return super.stop();
    }

}
