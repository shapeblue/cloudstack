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

import com.cloud.dc.DataCenter;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.network.Network;
import com.cloud.network.NetworkMigrationResponder;
import com.cloud.network.Networks;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.guru.GuestNetworkGuru;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.Account;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

public class OvnGuestNetworkGuru extends GuestNetworkGuru implements NetworkMigrationResponder {
    public OvnGuestNetworkGuru() {
        super();
        _isolationMethods = new PhysicalNetwork.IsolationMethod[] {new PhysicalNetwork.IsolationMethod("OVN")};
    }

    @Override
    public boolean canHandle(NetworkOffering offering, DataCenter.NetworkType networkType, PhysicalNetwork physicalNetwork) {
        return networkType == DataCenter.NetworkType.Advanced
                && isMyTrafficType(offering.getTrafficType())
                && isMyIsolationMethod(physicalNetwork)
                && networkOfferingServiceMapDao.isProviderForNetworkOffering(offering.getId(), Network.Provider.Ovn);
    }

    @Override
    public Network design(NetworkOffering offering, DeploymentPlan plan, Network userSpecified, String name, Long vpcId, Account owner) {
        PhysicalNetworkVO physicalNetwork = _physicalNetworkDao.findById(plan.getPhysicalNetworkId());
        DataCenter dataCenter = _dcDao.findById(plan.getDataCenterId());
        if (!canHandle(offering, dataCenter.getNetworkType(), physicalNetwork)) {
            logger.debug("Refusing to design this network");
            return null;
        }
        NetworkVO network = (NetworkVO) super.design(offering, plan, userSpecified, name, vpcId, owner);
        if (network == null) {
            return null;
        }
        network.setBroadcastDomainType(Networks.BroadcastDomainType.OVN);
        // Broadcast URI is deferred to implement(); the network has no persisted ID yet here.
        return network;
    }

    @Override
    public Network implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context)
            throws InsufficientVirtualNetworkCapacityException {
        Network implemented = super.implement(network, offering, dest, context);
        if (implemented == null) {
            return null;
        }
        if (implemented instanceof NetworkVO) {
            NetworkVO impl = (NetworkVO) implemented;
            impl.setBroadcastDomainType(Networks.BroadcastDomainType.OVN);
            impl.setBroadcastUri(Networks.BroadcastDomainType.OVN.toUri(String.format("cs-net-%d", network.getId())));
        }
        return implemented;
    }

    @Override
    public boolean prepareMigration(NicProfile nic, Network network, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) {
        return true;
    }

    @Override
    public void rollbackMigration(NicProfile nic, Network network, VirtualMachineProfile vm, ReservationContext src, ReservationContext dst) {
        // No OVN resources are allocated during migration preparation yet.
    }

    @Override
    public void commitMigration(NicProfile nic, Network network, VirtualMachineProfile vm, ReservationContext src, ReservationContext dst) {
        // No OVN resources are committed on migration yet.
    }
}
