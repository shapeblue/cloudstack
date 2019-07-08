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
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.admin.resource.StartRollingMaintenanceCmd;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
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
    public boolean startRollingMaintenance(StartRollingMaintenanceCmd cmd) {
        Long podId = cmd.getPodId();
        Long clusterId = cmd.getClusterId();
        Long zoneId = cmd.getZoneId();
        Long hostId = cmd.getHostId();
        ResourceType type = getResourceType(podId, clusterId, zoneId, hostId);

        switch (type) {
            case Pod:
                HostPodVO podVO = podDao.findById(podId);
                break;
            case Cluster:
                ClusterVO clusterVO = clusterDao.findById(clusterId);
                if (clusterVO == null) {
                    throw new CloudRuntimeException("Could not find cluster with ID: " + clusterId);
                }
                return rollingMaintenanceCluster(clusterVO);
            case Zone:
                DataCenterVO zoneVO = dataCenterDao.findById(zoneId);
                break;
            case Host:
                HostVO hostVO = hostDao.findById(hostId);
                break;
            default:
                throw new CloudRuntimeException("Unknown resource type: " + type);
        }
        return false;
    }

    protected boolean rollingMaintenanceCluster(Cluster cluster) {
        if (cluster.getHypervisorType() != Hypervisor.HypervisorType.KVM) {
            throw new CloudRuntimeException(unsopportedHypervisorErrorMsg);
        }
        List<HostVO> clusterHosts = hostDao.listByClusterAndHypervisorType(cluster.getId(), cluster.getHypervisorType());
        for (HostVO host: clusterHosts) {
            rollingMaintenanceHost(host);
        }
        return true;
    }

    protected boolean rollingMaintenanceHost(Host host) {
        s_logger.debug("Rolling maintenance for host " + host.getId());
        RollingMaintenanceCommand cmd = new RollingMaintenanceCommand();
        try {
            agentManager.send(host.getId(), cmd);
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            String msg = "Error while starting rolling maintenance on host " + host.getId() + ":" + e.getMessage();
            s_logger.error(msg, e);
            throw new CloudRuntimeException(msg);
        }
        return true;
    }
}
