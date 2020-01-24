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
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package org.apache.cloudstack.compute.maas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.compute.maas.MaasObject.MaasNode;
import org.apache.cloudstack.compute.maas.api.ListMaasServiceOfferingsCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;

import com.cloud.api.query.dao.ServiceOfferingJoinDao;
import com.cloud.api.query.vo.ServiceOfferingJoinVO;
import com.cloud.configuration.Config;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.cloud.utils.db.SearchCriteria;
import com.google.common.base.Strings;

public class MaasManagerImpl extends ManagerBase implements MaasManager, Configurable {
    public static final Logger LOGGER = Logger.getLogger(MaasManagerImpl.class.getName());

    @Inject private DataCenterDao dcDao;
    @Inject private ClusterDao clusterDao;
    @Inject protected ConfigurationDao configDao;
    @Inject private ClusterDetailsDao clusterDetailsDao;
    @Inject private ServiceOfferingJoinDao svcOfferingJoinDao;

    @Override
    public String getConfigComponentName() {
        return MaasManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {};
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmds = new ArrayList<Class<?>>();
        cmds.add(ListMaasServiceOfferingsCmd.class);
        return cmds;
    }

    @Override
    public List<MaasServiceOfferingsResponse> listMaasServiceOfferings(ListMaasServiceOfferingsCmd cmd) throws ConfigurationException, IOException {
        List<MaasServiceOfferingsResponse> responses = new ArrayList<>();

        if (Strings.isNullOrEmpty(cmd.getPoolName())) {
            return responses;
        }

        SearchCriteria<ServiceOfferingJoinVO> sc = svcOfferingJoinDao.createSearchBuilder().create();
        sc.setParameters("deploymentPlanner", "BareMetalPlanner");
        List<ServiceOfferingJoinVO> offerings = svcOfferingJoinDao.search(sc, null);

        if (offerings == null || offerings.size() == 0) {
            return responses;
        }

        List<MaasNode> nodes = new ArrayList<>();

        for (ClusterVO c : getMaasClusters()) {
            MaasApiClient client = getMaasApiClient(c.getId());
            nodes.addAll(client.getMaasNodes(cmd.getPoolName()));
        }

        offerings.forEach(svc -> {
            int available = 0;
            int total = 0;
            int erasing = 0;

            for (MaasNode node : nodes) {
                if (node.getCpuCount() == svc.getCpu() && node.getCpuSpeed().intValue() == svc.getSpeed() && node.getMemory().intValue() == svc.getRamSize()) {
                    total++;

                    if (node.getStatusName().equals(MaasObject.MaasState.Ready.toString())) {
                        available++;
                    }
                    //TODO
//                    if (node.getStatusName().equals(MaasObject.MaasState.Erasing.toString())) {
//                        erasing++;
//                    }
                }
            };

            MaasServiceOfferingsResponse response = new MaasServiceOfferingsResponse();

            response.setOfferingId(svc.getUuid());
            response.setOfferingName(svc.getName());
            response.setAvailable(available);
            response.setTotal(total);
            response.setErasing(erasing);

            responses.add(response);
        });

        return responses;
    }

    @Override
    public MaasApiClient getMaasApiClient(long clusterId) throws ConfigurationException {
        Map<String, String> clusterDetails = clusterDetailsDao.findDetails(clusterId);
        String maasUrl = clusterDetails.get("baremetalMaasHost");
        String maasApiKey = DBEncryptionUtil.decrypt(clusterDetails.get("baremetalMaaSKey"));

        String[] maasAddress = maasUrl.split(":");
        String maasScheme = null;
        String maasIp = null;
        Integer maasPort = -1;

        try {
            // scheme://ip_or_dns:port
            if (maasAddress.length == 3) {
                maasScheme = maasAddress[0];
                maasIp = maasAddress[1].replace("/", "");
                maasPort = Integer.parseInt(maasAddress[2].replace("/", ""));
            }

            // scheme://ip_or_dns OR ip_or_dns:port
            else if (maasAddress.length == 2) {
                if (maasAddress[0].equalsIgnoreCase("http") || maasAddress[0].equalsIgnoreCase("https")) {
                    maasScheme = maasAddress[0];
                    maasIp = maasAddress[1].replace("/", "");
                } else {
                    maasIp = maasAddress[0].replace("/", "");
                    maasPort = Integer.parseInt(maasAddress[1].replace("/", ""));
                }
            }

            // ip_or_dns
            else if (maasAddress.length == 1) {
                maasIp = maasAddress[0];
            }

            else {
                throw new ConfigurationException(maasUrl + " is not a valid URL for MaaS server");
            }
        } catch (NumberFormatException e) {
            if (maasAddress.length == 3) {
                LOGGER.warn(maasAddress[2].replace("/", "") + " is not a valid port number", e);
            } else if (maasAddress.length == 2) {
                LOGGER.warn(maasAddress[1].replace("/", "") + " is not a valid port number", e);
            }

            throw e;
        }

        String[] maasSecrets = maasApiKey.split(":");

        if (maasSecrets.length != 3) {
            LOGGER.warn("MaaS API key is malformed");
            throw new ConfigurationException("MaaS API key is malformed");
        }

        String maasConsumerKey = maasSecrets[0];
        String maasKey = maasSecrets[1];
        String maasSercret = maasSecrets[2];

        int timeout = Integer.parseInt(configDao.getValue(Config.BaremetalProvisionDoneNotificationTimeout.key()));

        return new MaasApiClient(maasScheme, maasIp, maasPort, maasKey,  maasSercret, maasConsumerKey, timeout);
    }

    private Set<ClusterVO> getMaasClusters() {
        Set<ClusterVO> clusters = new HashSet<>();

        dcDao.listAllZones().forEach(dc -> {
            clusterDao.listClustersByDcId(dc.getId())
                .stream()
                .filter(c -> c.getHypervisorType().equals(HypervisorType.BareMetal))
                .forEach(c -> clusters.add(c));
        });

        return clusters;
    }
}
