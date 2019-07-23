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
package com.cloud.resource;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.RollingMaintenanceCommand;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.org.Cluster;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.admin.resource.StartRollingMaintenanceCmd;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RollingMaintenanceManagerImpl extends ManagerBase implements RollingMaintenanceManager {

    @Inject
    private HostPodDao podDao;
    @Inject
    private ClusterDao clusterDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private AgentManager agentManager;
    @Inject
    private ResourceManager resourceManager;

    private static final long SLEEP_STEP = 100L;
    public static final Logger s_logger = Logger.getLogger(RollingMaintenanceManagerImpl.class.getName());

    private static final String unsopportedHypervisorErrorMsg = "Rolling maintenance is currently supported on KVM only";

    private ResourceType getResourceType(Long podId, Long clusterId, Long zoneId, Long hostId) {
        List<Long> parameters = Arrays.asList(podId, clusterId, zoneId, hostId);
        long numberParametersSet = parameters.stream().filter(Objects::nonNull).count();
        if (numberParametersSet == 0L || numberParametersSet > 1L) {
            throw new CloudRuntimeException("Parameters podId, clusterId, zoneId, hostId are mutually exclusive, " +
                    "please set only one of them");
        }

        return podId != null ? ResourceType.Pod :
               clusterId != null ? ResourceType.Cluster :
               zoneId != null ? ResourceType.Zone : ResourceType.Host;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        return true;
    }

    @Override
    public Pair<Boolean, String> startRollingMaintenance(StartRollingMaintenanceCmd cmd) {
        Long podId = cmd.getPodId();
        Long clusterId = cmd.getClusterId();
        Long zoneId = cmd.getZoneId();
        Long hostId = cmd.getHostId();
        ResourceType type = getResourceType(podId, clusterId, zoneId, hostId);
        long timeout = 30 * 1000;

        switch (type) {
            case Pod:
                HostPodVO podVO = podDao.findById(podId);
                break;
            case Cluster:
                ClusterVO clusterVO = clusterDao.findById(clusterId);
                if (clusterVO == null) {
                    throw new CloudRuntimeException("Could not find cluster with ID: " + clusterId);
                }
                return rollingMaintenanceCluster(clusterVO, timeout);
            case Zone:
                DataCenterVO zoneVO = dataCenterDao.findById(zoneId);
                break;
            case Host:
                HostVO hostVO = hostDao.findById(hostId);
                return rollingMaintenanceHost(hostVO, timeout);
            default:
                throw new CloudRuntimeException("Unknown resource type: " + type);
        }
        return new Pair<>(false, "Error");
    }

    protected Pair<Boolean, String> rollingMaintenanceCluster(Cluster cluster, long timeout) {
        if (cluster.getHypervisorType() != Hypervisor.HypervisorType.KVM) {
            throw new CloudRuntimeException(unsopportedHypervisorErrorMsg);
        }
        List<HostVO> clusterHosts = hostDao.listByClusterAndHypervisorType(cluster.getId(), cluster.getHypervisorType());
        for (HostVO host: clusterHosts) {
            Pair<Boolean, String> result = rollingMaintenanceHost(host, timeout);
            if (!result.first()) {
                return result;
            }
        }
        return new Pair<> (true, "OK");
    }

    protected Pair<Boolean, String> rollingMaintenanceHostStage(Host host, Stage stage, long timeout) throws AgentUnavailableException {
        if (stage == Stage.Maintenance) {
            s_logger.debug("Trying to set the host " + host.getId() + " into maintenance");
            resourceManager.maintain(host.getId());
        }

        //TODO: create global setting for timeout

        long sleepTime = 0;
        boolean completed = false;

        try {
            RollingMaintenanceCommand cmd;
            Answer answer = null;
            while (!completed) {
                Thread.sleep(SLEEP_STEP);
                sleepTime += SLEEP_STEP;
                if (sleepTime >= timeout) {
                    s_logger.info("Rolling maintenance timeout for host " + host + ", aborting");
                    cmd = new RollingMaintenanceCommand(stage);
                    cmd.setTerminate(true);
                    answer = agentManager.send(host.getId(), cmd);
                    completed = true;
                }
                else if (sleepTime % 5000 == 0L) {
                    s_logger.debug("Waiting for rolling maintenance stage " + stage + " to be completed on host " + host);
                    cmd = new RollingMaintenanceCommand(stage);
                    answer = agentManager.send(host.getId(), cmd);
                    completed = answer.getResult();
                }
            }
            return new Pair<>(answer.getResult(), answer.getDetails());
        } catch (AgentUnavailableException | OperationTimedoutException | InterruptedException e) {
            String msg = "Error while starting rolling maintenance on host " + host.getId() + ":" + e.getMessage();
            s_logger.error(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    /**
     * Rolling maintenance is perform in 3 stages:
     * - Pre-flight
     * - Pre-maintenance
     * - Maintenance
     */
    protected Pair<Boolean, String> rollingMaintenanceHost(Host host, long timeout) {
        s_logger.debug("Starting rolling maintenance on host " + host.getId());

        Stage stage = Stage.PreFlight;
        try {
            while (stage != null) {
                Pair<Boolean, String> result = null;
                result = rollingMaintenanceHostStage(host, stage, timeout);
                if (!result.first()) {
                    String msg = "Error on rolling maintenance for host: " + host.getId() + ", stage: " + stage
                            + ", details: " + result.second();
                    s_logger.error(msg);
                    return new Pair<>(false, msg);
                }
                stage = stage.next();
            }
        } catch (AgentUnavailableException e) {
            String msg = "Unable to perform rolling maintenance stage " + stage + " on host " + host.getId()
                    + ": " + e.getMessage();
            s_logger.error(msg, e);
            return new Pair<>(false, msg);
        }
        return new Pair<>(true, "OK");
    }
}
