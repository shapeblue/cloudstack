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

import com.cloud.agent.api.to.LoadBalancerTO;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.element.DhcpServiceProvider;
import com.cloud.network.element.DnsServiceProvider;
import com.cloud.network.element.FirewallServiceProvider;
import com.cloud.network.element.IpDeployer;
import com.cloud.network.element.LoadBalancingServiceProvider;
import com.cloud.network.element.NetworkACLServiceProvider;
import com.cloud.network.element.PortForwardingServiceProvider;
import com.cloud.network.element.StaticNatServiceProvider;
import com.cloud.network.element.VpcProvider;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.LoadBalancerContainer;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.network.rules.StaticNat;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.StaticRouteProfile;
import com.cloud.network.vpc.Vpc;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OvnElement extends AdapterBase implements DhcpServiceProvider, DnsServiceProvider, VpcProvider,
        StaticNatServiceProvider, IpDeployer, PortForwardingServiceProvider, FirewallServiceProvider,
        NetworkACLServiceProvider, LoadBalancingServiceProvider {

    private final Map<Network.Service, Map<Network.Capability, String>> capabilities = initCapabilities();

    protected static Map<Network.Service, Map<Network.Capability, String>> initCapabilities() {
        Map<Network.Service, Map<Network.Capability, String>> capabilities = new HashMap<>();

        Map<Network.Capability, String> dhcpCapabilities = new HashMap<>();
        dhcpCapabilities.put(Network.Capability.DhcpAccrossMultipleSubnets, "true");
        capabilities.put(Network.Service.Dhcp, dhcpCapabilities);

        Map<Network.Capability, String> dnsCapabilities = new HashMap<>();
        dnsCapabilities.put(Network.Capability.AllowDnsSuffixModification, "true");
        capabilities.put(Network.Service.Dns, dnsCapabilities);

        Map<Network.Capability, String> sourceNatCapabilities = new HashMap<>();
        sourceNatCapabilities.put(Network.Capability.SupportedSourceNatTypes, "peraccount");
        capabilities.put(Network.Service.SourceNat, sourceNatCapabilities);

        capabilities.put(Network.Service.StaticNat, null);
        capabilities.put(Network.Service.PortForwarding, null);
        capabilities.put(Network.Service.NetworkACL, null);
        capabilities.put(Network.Service.Gateway, null);

        Map<Network.Capability, String> firewallCapabilities = new HashMap<>();
        firewallCapabilities.put(Network.Capability.SupportedProtocols, "tcp,udp,icmp");
        firewallCapabilities.put(Network.Capability.SupportedEgressProtocols, "tcp,udp,icmp,all");
        firewallCapabilities.put(Network.Capability.SupportedTrafficDirection, "ingress,egress");
        capabilities.put(Network.Service.Firewall, firewallCapabilities);

        Map<Network.Capability, String> lbCapabilities = new HashMap<>();
        lbCapabilities.put(Network.Capability.SupportedLBAlgorithms, "roundrobin,leastconn");
        lbCapabilities.put(Network.Capability.SupportedLBIsolation, "dedicated");
        lbCapabilities.put(Network.Capability.SupportedProtocols, "tcp,udp");
        lbCapabilities.put(Network.Capability.LbSchemes, String.join(",", LoadBalancerContainer.Scheme.Internal.name(), LoadBalancerContainer.Scheme.Public.name()));
        capabilities.put(Network.Service.Lb, lbCapabilities);

        capabilities.put(Network.Service.Connectivity, null);
        return capabilities;
    }

    @Override
    public Map<Network.Service, Map<Network.Capability, String>> getCapabilities() {
        return capabilities;
    }

    @Override
    public Network.Provider getProvider() {
        return Network.Provider.Ovn;
    }

    @Override
    public boolean implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        return true;
    }

    @Override
    public boolean release(Network network, NicProfile nic, VirtualMachineProfile vm, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean destroy(Network network, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return true;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    @Override
    public boolean verifyServicesCombination(Set<Network.Service> services) {
        return true;
    }

    @Override
    public boolean addDhcpEntry(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean configDhcpSupportForSubnet(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean removeDhcpSupportForSubnet(Network network) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean setExtraDhcpOptions(Network network, long nicId, Map<Integer, String> dhcpOptions) {
        return true;
    }

    @Override
    public boolean removeDhcpEntry(Network network, NicProfile nic, VirtualMachineProfile vmProfile) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean addDnsEntry(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean configDnsSupportForSubnet(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean removeDnsSupportForSubnet(Network network) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyIps(Network network, List<? extends PublicIpAddress> ipAddress, Set<Network.Service> services) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public IpDeployer getIpDeployer(Network network) {
        return this;
    }

    @Override
    public boolean applyStaticNats(Network config, List<? extends StaticNat> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyPFRules(Network network, List<PortForwardingRule> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyFWRules(Network network, List<? extends FirewallRule> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyNetworkACLs(Network config, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean reorderAclRules(Vpc vpc, List<? extends Network> networks, List<? extends NetworkACLItem> networkACLItems) {
        return true;
    }

    @Override
    public boolean applyLBRules(Network network, List<LoadBalancingRule> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean validateLBRule(Network network, LoadBalancingRule rule) {
        return true;
    }

    @Override
    public List<LoadBalancerTO> updateHealthChecks(Network network, List<LoadBalancingRule> lbrules) {
        return null;
    }

    @Override
    public boolean handlesOnlyRulesInTransitionState() {
        return false;
    }

    @Override
    public boolean implementVpc(Vpc vpc, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        return true;
    }

    @Override
    public boolean shutdownVpc(Vpc vpc, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean createPrivateGateway(PrivateGateway gateway) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean deletePrivateGateway(PrivateGateway privateGateway) throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyStaticRoutes(Vpc vpc, List<StaticRouteProfile> routes) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyACLItemsToPrivateGw(PrivateGateway gateway, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean updateVpcSourceNatIp(Vpc vpc, IpAddress address) {
        return true;
    }
}
