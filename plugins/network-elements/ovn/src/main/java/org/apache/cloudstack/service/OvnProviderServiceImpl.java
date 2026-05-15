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
package org.apache.cloudstack.service;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.OvnProviderDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.element.OvnProviderVO;
import com.cloud.network.ovn.OvnProvider;
import com.cloud.network.ovn.OvnService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.command.AddOvnProviderCmd;
import org.apache.cloudstack.api.command.CreateMeshNetworkCmd;
import org.apache.cloudstack.api.command.DeleteOvnProviderCmd;
import org.apache.cloudstack.api.command.DeleteMeshNetworkCmd;
import org.apache.cloudstack.api.command.DisableMeshNetworkCmd;
import org.apache.cloudstack.api.command.EnableMeshNetworkCmd;
import org.apache.cloudstack.api.command.ListOvnProvidersCmd;
import org.apache.cloudstack.api.command.ListMeshNetworksCmd;
import org.apache.cloudstack.api.command.UpdateMeshNetworkCmd;
import org.apache.cloudstack.api.response.OvnProviderResponse;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OvnProviderServiceImpl implements OvnProviderService {
    protected Logger logger = LogManager.getLogger(getClass());

    @Inject
    DataCenterDao dataCenterDao;
    @Inject
    OvnProviderDao ovnProviderDao;
    @Inject
    PhysicalNetworkDao physicalNetworkDao;
    @Inject
    NetworkDao networkDao;
    @Inject
    OvnService ovnService;

    @Override
    public OvnProvider addProvider(AddOvnProviderCmd cmd) {
        validateProvider(cmd);
        final long zoneId = cmd.getZoneId();
        return Transaction.execute((TransactionCallback<OvnProviderVO>) status -> {
            OvnProviderVO provider = new OvnProviderVO.Builder()
                    .setZoneId(zoneId)
                    .setName(cmd.getName())
                    .setNbConnection(cmd.getNbConnection())
                    .setSbConnection(cmd.getSbConnection())
                    .setCaCertPath(cmd.getCaCertPath())
                    .setClientCertPath(cmd.getClientCertPath())
                    .setClientPrivateKeyPath(cmd.getClientPrivateKeyPath())
                    .setExternalBridge(cmd.getExternalBridge())
                    .setLocalnetName(cmd.getLocalnetName())
                    .setIcNbConnection(cmd.getIcNbConnection())
                    .setIcSbConnection(cmd.getIcSbConnection())
                    .setAvailabilityZoneName(cmd.getAvailabilityZoneName())
                    .build();
            return ovnProviderDao.persist(provider);
        });
    }

    protected void validateProvider(AddOvnProviderCmd cmd) {
        DataCenterVO zone = dataCenterDao.findById(cmd.getZoneId());
        if (zone == null) {
            throw new InvalidParameterValueException(String.format("Failed to find zone with id: %s", cmd.getZoneId()));
        }
        if (ovnProviderDao.findByZoneId(cmd.getZoneId()) != null) {
            throw new InvalidParameterValueException(String.format("OVN provider already exists for zone: %s", cmd.getZoneId()));
        }
        if (!ovnService.isValidConnectionString(cmd.getNbConnection())) {
            throw new InvalidParameterValueException("Invalid OVN Northbound connection string");
        }
        if (StringUtils.isNotBlank(cmd.getSbConnection()) && !ovnService.isValidConnectionString(cmd.getSbConnection())) {
            throw new InvalidParameterValueException("Invalid OVN Southbound connection string");
        }
        boolean sslRequired = cmd.getNbConnection().startsWith("ssl:")
                || (StringUtils.isNotBlank(cmd.getSbConnection()) && cmd.getSbConnection().startsWith("ssl:"));
        if (sslRequired && StringUtils.isAnyBlank(cmd.getCaCertPath(), cmd.getClientCertPath(), cmd.getClientPrivateKeyPath())) {
            throw new InvalidParameterValueException("OVN SSL connections require CA certificate, client certificate, and client private key paths");
        }
        try {
            ovnService.verifyNbConnection(cmd.getNbConnection(), cmd.getCaCertPath(), cmd.getClientCertPath(), cmd.getClientPrivateKeyPath());
        } catch (CloudRuntimeException e) {
            logger.warn("OVN NB health check failed for zone {}: {}", cmd.getZoneId(), e.getMessage());
            throw new InvalidParameterValueException("OVN NB endpoint is unreachable: " + e.getMessage());
        }
    }

    @Override
    public List<BaseResponse> listOvnProviders(Long zoneId) {
        List<BaseResponse> responseList = new ArrayList<>();
        if (zoneId != null) {
            OvnProviderVO provider = ovnProviderDao.findByZoneId(zoneId);
            if (provider != null) {
                responseList.add(createOvnProviderResponse(provider));
            }
            return responseList;
        }
        for (OvnProviderVO provider : ovnProviderDao.listAll()) {
            responseList.add(createOvnProviderResponse(provider));
        }
        return responseList;
    }

    @Override
    public boolean deleteOvnProvider(Long providerId) {
        OvnProviderVO provider = ovnProviderDao.findById(providerId);
        if (provider == null) {
            throw new InvalidParameterValueException(String.format("Failed to find OVN provider with id: %s", providerId));
        }
        validateNetworkState(provider.getZoneId());
        ovnProviderDao.remove(providerId);
        return true;
    }

    protected void validateNetworkState(long zoneId) {
        List<PhysicalNetworkVO> physicalNetworks = physicalNetworkDao.listByZone(zoneId);
        for (PhysicalNetworkVO physicalNetwork : physicalNetworks) {
            for (NetworkVO network : networkDao.listByPhysicalNetwork(physicalNetwork.getId())) {
                if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN
                        && network.getState() != Network.State.Shutdown
                        && network.getState() != Network.State.Destroy) {
                    throw new CloudRuntimeException("This OVN provider cannot be deleted as there are one or more logical networks provisioned by CloudStack on it.");
                }
            }
        }
    }

    @Override
    public OvnProviderResponse createOvnProviderResponse(OvnProvider provider) {
        DataCenterVO zone = dataCenterDao.findById(provider.getZoneId());
        if (Objects.isNull(zone)) {
            throw new CloudRuntimeException(String.format("Failed to find zone with id %s", provider.getZoneId()));
        }
        OvnProviderResponse response = new OvnProviderResponse();
        response.setName(provider.getName());
        response.setUuid(provider.getUuid());
        response.setZoneId(zone.getUuid());
        response.setZoneName(zone.getName());
        response.setNbConnection(provider.getNbConnection());
        response.setSbConnection(provider.getSbConnection());
        response.setCaCertPath(provider.getCaCertPath());
        response.setClientCertPath(provider.getClientCertPath());
        response.setClientPrivateKeyPath(provider.getClientPrivateKeyPath());
        response.setExternalBridge(provider.getExternalBridge());
        response.setLocalnetName(provider.getLocalnetName());
        response.setIcNbConnection(provider.getIcNbConnection());
        response.setIcSbConnection(provider.getIcSbConnection());
        response.setAvailabilityZoneName(provider.getAvailabilityZoneName());
        response.setObjectName("ovnProvider");
        return response;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        if (Boolean.TRUE.equals(NetworkOrchestrationService.OVN_ENABLED.value())) {
            cmdList.add(AddOvnProviderCmd.class);
            cmdList.add(ListOvnProvidersCmd.class);
            cmdList.add(DeleteOvnProviderCmd.class);
            cmdList.add(CreateMeshNetworkCmd.class);
            cmdList.add(UpdateMeshNetworkCmd.class);
            cmdList.add(DeleteMeshNetworkCmd.class);
            cmdList.add(EnableMeshNetworkCmd.class);
            cmdList.add(DisableMeshNetworkCmd.class);
            cmdList.add(ListMeshNetworksCmd.class);
        }
        return cmdList;
    }
}
