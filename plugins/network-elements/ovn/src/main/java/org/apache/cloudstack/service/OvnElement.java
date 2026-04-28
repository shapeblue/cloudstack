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
import com.cloud.dc.DataCenter;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.element.DhcpServiceProvider;
import com.cloud.network.element.DnsServiceProvider;
import com.cloud.network.element.FirewallServiceProvider;
import com.cloud.network.element.IpDeployer;
import com.cloud.network.element.LoadBalancingServiceProvider;
import com.cloud.network.element.NetworkACLServiceProvider;
import com.cloud.network.dao.OvnProviderDao;
import com.cloud.network.element.OvnProviderVO;
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
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

public class OvnElement extends AdapterBase implements DhcpServiceProvider, DnsServiceProvider, VpcProvider,
        StaticNatServiceProvider, IpDeployer, PortForwardingServiceProvider, FirewallServiceProvider,
        NetworkACLServiceProvider, LoadBalancingServiceProvider {

    private final Map<Network.Service, Map<Network.Capability, String>> capabilities = initCapabilities();
    private final OvnNbClient ovnNbClient = new OvnNbClient();

    @Inject
    OvnProviderDao ovnProviderDao;

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
        if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN) {
            OvnProviderVO provider = getProviderForNetwork(network);
            String logicalSwitchName = getLogicalSwitchName(network);
            Map<String, String> externalIds = new HashMap<>();
            externalIds.put("cloudstack_network_id", String.valueOf(network.getId()));
            externalIds.put("cloudstack_network_uuid", network.getUuid());
            externalIds.put("cloudstack_zone_id", String.valueOf(network.getDataCenterId()));
            try {
                ovnNbClient.createLogicalSwitch(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), logicalSwitchName, externalIds);
                createDhcpOptionsForNetwork(provider, network);
            } catch (CloudRuntimeException e) {
                throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
            }
        }
        return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN) {
            OvnProviderVO provider = getProviderForNetwork(network);
            String lsName = getLogicalSwitchName(network);
            String lspName = getLogicalSwitchPortName(nic);
            Map<String, String> externalIds = new HashMap<>();
            externalIds.put("cloudstack_nic_id", String.valueOf(nic.getId()));
            externalIds.put("cloudstack_nic_uuid", nic.getUuid());
            externalIds.put("cloudstack_vm_id", String.valueOf(vm.getId()));
            externalIds.put("cloudstack_vm_uuid", vm.getUuid());
            try {
                ovnNbClient.createLogicalSwitchPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        lsName, lspName, nic.getMacAddress(), nic.getIPv4Address(), externalIds);
                String dhcpUuid = createDhcpOptionsForNetwork(provider, network);
                if (dhcpUuid != null && nic.getIPv4Address() != null) {
                    ovnNbClient.setLspDhcpv4Options(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            lspName, dhcpUuid);
                }
            } catch (CloudRuntimeException e) {
                throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
            }
        }
        return true;
    }

    /**
     * Idempotently creates the DHCP_Options row for an OVN-backed Network. Returns the UUID, or null
     * when the network has no IPv4 CIDR (in which case there is nothing to serve via OVN DHCP).
     */
    protected String createDhcpOptionsForNetwork(OvnProviderVO provider, Network network) {
        String cidr = network.getCidr();
        if (cidr == null || cidr.isEmpty()) {
            return null;
        }
        String gateway = network.getGateway();
        Map<String, String> options = new HashMap<>();
        if (gateway != null && !gateway.isEmpty()) {
            options.put("server_id", gateway);
            options.put("router", gateway);
        }
        // server_mac just needs to be a stable, locally administered MAC unique within this LS.
        options.put("server_mac", buildServerMac(network.getId()));
        options.put("lease_time", "86400");
        options.put("mtu", "1442");
        StringBuilder dns = new StringBuilder("{");
        if (network.getDns1() != null && !network.getDns1().isEmpty()) {
            dns.append(network.getDns1());
        }
        if (network.getDns2() != null && !network.getDns2().isEmpty()) {
            if (dns.length() > 1) dns.append(",");
            dns.append(network.getDns2());
        }
        dns.append("}");
        if (dns.length() > 2) {
            options.put("dns_server", dns.toString());
        }
        Map<String, String> externalIds = new HashMap<>();
        externalIds.put("cloudstack_network_id", String.valueOf(network.getId()));
        externalIds.put("cloudstack_network_uuid", network.getUuid());
        return ovnNbClient.createDhcpOptions(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                cidr, options, externalIds);
    }

    private static String buildServerMac(long networkId) {
        return String.format("fa:16:3e:%02x:%02x:%02x",
                (int) ((networkId >> 16) & 0xff),
                (int) ((networkId >> 8) & 0xff),
                (int) (networkId & 0xff));
    }

    @Override
    public boolean release(Network network, NicProfile nic, VirtualMachineProfile vm, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN) {
            OvnProviderVO provider = getProviderForNetwork(network);
            String lsName = getLogicalSwitchName(network);
            String lspName = getLogicalSwitchPortName(nic);
            try {
                ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        lsName, lspName);
            } catch (CloudRuntimeException e) {
                throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
            }
        }
        return true;
    }

    /**
     * Returns the OVN Logical_Switch_Port name for the given NIC. Must match the value the KVM
     * agent stamps as {@code external_ids:iface-id} on the OVS port — see {@code OvsVifDriver}'s
     * OVN branch which uses {@link com.cloud.agent.api.to.NicTO#getUuid()} for the same purpose.
     */
    protected String getLogicalSwitchPortName(NicProfile nic) {
        return nic.getUuid();
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        if (cleanup && network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN) {
            destroy(network, context);
        }
        return true;
    }

    @Override
    public boolean destroy(Network network, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.OVN) {
            OvnProviderVO provider = getProviderForNetwork(network);
            try {
                ovnNbClient.deleteDhcpOptions(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), String.valueOf(network.getId()));
                ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), getLogicalSwitchName(network));
            } catch (CloudRuntimeException e) {
                throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
            }
        }
        return true;
    }

    protected OvnProviderVO getProviderForNetwork(Network network) throws ResourceUnavailableException {
        OvnProviderVO provider = ovnProviderDao.findByZoneId(network.getDataCenterId());
        if (provider == null) {
            throw new ResourceUnavailableException(String.format("No OVN provider configured for zone %s", network.getDataCenterId()),
                    DataCenter.class, network.getDataCenterId());
        }
        return provider;
    }

    protected String getLogicalSwitchName(Network network) {
        return String.format("cs-net-%d", network.getId());
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
