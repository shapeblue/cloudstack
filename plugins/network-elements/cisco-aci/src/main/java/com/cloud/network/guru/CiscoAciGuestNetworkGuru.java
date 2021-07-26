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

package com.cloud.network.guru;

import java.util.Map;

import javax.inject.Inject;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Service;
import com.cloud.network.Network.State;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkProfile;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetwork.IsolationMethod;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.resource.ResourceManager;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

public class CiscoAciGuestNetworkGuru extends GuestNetworkGuru implements NetworkGuruAdditionalFunctions {

    private static final Logger LOGGER = Logger.getLogger(CiscoAciGuestNetworkGuru.class);

    @Inject
    protected NetworkModel networkModel;
    @Inject
    protected NetworkDao networkDao;
    @Inject
    protected DataCenterDao zoneDao;
    @Inject
    protected PhysicalNetworkDao physicalNetworkDao;
    @Inject
    protected AccountDao accountDao;
    @Inject
    protected HostDao hostDao;
    @Inject
    protected ResourceManager resourceMgr;
    @Inject
    protected AgentManager agentMgr;
    @Inject
    protected HostDetailsDao hostDetailsDao;
    @Inject
    protected NetworkOfferingServiceMapDao ntwkOfferingSrvcDao;

    public CiscoAciGuestNetworkGuru() {
        super();
        _isolationMethods = new IsolationMethod[] { new IsolationMethod("VLAN", "CiscoAci"), new IsolationMethod("VXLAN","CiscoAci") };
    }

    @Override
    protected boolean canHandle(final NetworkOffering offering, final NetworkType networkType, final PhysicalNetwork physicalNetwork) {
        // This guru handles only Guest Isolated network that supports Source nat service
        if (networkType == NetworkType.Advanced && isMyTrafficType(offering.getTrafficType())
                && supportedGuestTypes(offering, Network.GuestType.Isolated, Network.GuestType.L2, Network.GuestType.Shared)
                && isMyIsolationMethod(physicalNetwork) && ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.Connectivity)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean supportedGuestTypes(NetworkOffering offering, GuestType... types) {
        for (GuestType guestType : types) {
            if (offering.getGuestType().equals(guestType)){
                return true;
            }
        }
        return false;
    }

    @Override
    public Network design(final NetworkOffering offering, final DeploymentPlan plan, final Network userSpecified, final Account owner) {
        // Check of the isolation type of the related physical network is supported
        final PhysicalNetworkVO physnet = physicalNetworkDao.findById(plan.getPhysicalNetworkId());
        final DataCenter dc = _dcDao.findById(plan.getDataCenterId());
        if (!canHandle(offering, dc.getNetworkType(), physnet)) {
            LOGGER.debug("Refusing to design this network");
            return null;
        }

        // TODO: find ACI controller and do some checks if what is request

        LOGGER.debug("Physical isolation type is supported, asking GuestNetworkGuru to design this network");
        final NetworkVO networkObject = (NetworkVO) super.design(offering, plan, userSpecified, owner);
        if (networkObject == null) {
            return null;
        }
        networkObject.setBroadcastDomainType(BroadcastDomainType.Lswitch);
        if (offering.getGuestType().equals(GuestType.Shared)){
            networkObject.setState(State.Allocated);
        }

        return networkObject;
    }

    @Override
    public Network implement(final Network network, final NetworkOffering offering, final DeployDestination dest, final ReservationContext context)
            throws InsufficientVirtualNetworkCapacityException {
        assert network.getState() == State.Implementing : "Why are we implementing " + network;

        final long dcId = dest.getDataCenter().getId();

        Long physicalNetworkId = network.getPhysicalNetworkId();

        // physical network id can be null in Guest Network in Basic zone, so locate the physical network
        if (physicalNetworkId == null) {
            physicalNetworkId = networkModel.findPhysicalNetworkId(dcId, offering.getTags(), offering.getTrafficType());
        }

        final NetworkVO implemented = new NetworkVO(network.getTrafficType(), network.getMode(), network.getBroadcastDomainType(), network.getNetworkOfferingId(),
                State.Allocated, network.getDataCenterId(), physicalNetworkId, offering.isRedundantRouter());

        if (network.getGateway() != null) {
            implemented.setGateway(network.getGateway());
        }

        if (network.getCidr() != null) {
            implemented.setCidr(network.getCidr());
        }

        // Name is either the given name or the uuid
        String name = network.getName();
        if (name == null || name.isEmpty()) {
            name = ((NetworkVO) network).getUuid();
        }

        // TODO: find ACI controller/client and implement EPG for the VLAN

        // FIXME: fix the implemented object before returning

        return implemented;
    }

    @Override
    public void reserve(final NicProfile nic, final Network network, final VirtualMachineProfile vm, final DeployDestination dest, final ReservationContext context)
            throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        // TODO: should this create/reserve an EPG?
        super.reserve(nic, network, vm, dest, context);
    }

    @Override
    public boolean release(final NicProfile nic, final VirtualMachineProfile vm, final String reservationId) {
        // TODO: should this release any existing EPG?
        return super.release(nic, vm, reservationId);
    }

    @Override
    public void shutdown(final NetworkProfile profile, final NetworkOffering offering) {
        final NetworkVO networkObject = networkDao.findById(profile.getId());

        //TODO: do broadcast etc checks and release EPG for the VLAN?

        super.shutdown(profile, offering);
    }

    @Override
    public boolean trash(final Network network, final NetworkOffering offering) {
        // TODO: check and remove the EPG?

        return super.trash(network, offering);
    }

    @Override
    public void finalizeNetworkDesign(long networkId, String vlanIdAsUUID) {
        // TODO: use this to create/maintaing mapping of EPGs
    }

    @Override
    public Map<String, ? extends Object> listAdditionalNicParams(String nicUuid) {
        // TODO: how is this used?
        return null;
    }
}
