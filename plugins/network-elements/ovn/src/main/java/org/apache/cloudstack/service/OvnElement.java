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
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.VlanDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.OvnProviderDao;
import com.cloud.network.element.OvnProviderVO;
import com.cloud.vm.NicVO;
import com.cloud.vm.dao.NicDao;
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

    @Inject
    IPAddressDao ipAddressDao;

    @Inject
    VlanDao vlanDao;

    @Inject
    NicDao nicDao;

    @Inject
    com.cloud.host.dao.HostDao hostDao;

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
        // OVN Load_Balancer is L4-only. We only advertise what we actually deliver:
        //  - tcp/udp (sctp omitted - rarely used in CS UI)
        //  - round-robin and source-IP based hashing (no leastconn: OVN has no per-backend
        //    connection state)
        //  - public scheme for v1 (internal scheme deferred to a follow-up commit)
        // SSL offload, HTTP-aware LB, cookie stickiness etc. are L7 features that OVN cannot do
        // in the datapath - those tenants should pick a VirtualRouter offering instead.
        lbCapabilities.put(Network.Capability.SupportedLBAlgorithms, "roundrobin,source");
        lbCapabilities.put(Network.Capability.SupportedLBIsolation, "dedicated");
        lbCapabilities.put(Network.Capability.SupportedProtocols, "tcp,udp");
        lbCapabilities.put(Network.Capability.LbSchemes, LoadBalancerContainer.Scheme.Public.name());
        // OVN does L4 TCP probes via Load_Balancer_Health_Check. We accept HTTP/PING policies
        // but degrade to TCP probe of the same port (logged in applyLBHealthCheck).
        lbCapabilities.put(Network.Capability.HealthCheckPolicy, "true");
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
                createRouterAndAttachToGuest(provider, network);
                applySourceNatForNetwork(provider, network);
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

    /**
     * Returns the OVN Logical_Router name owning the network's tenant routing. For an isolated
     * network this is per-network ({@code cs-router-<networkId>}); for a VPC tier all networks
     * share the VPC's LR ({@code cs-vpc-<vpcId>}). PR-1 introduced this helper as the single
     * resolver for both cases — call sites should never branch on {@code network.getVpcId()}
     * themselves.
     */
    protected String getRouterNameForNetwork(Network network) {
        Long vpcId = network.getVpcId();
        return vpcId != null ? String.format("cs-vpc-%d", vpcId) : String.format("cs-router-%d", network.getId());
    }

    /**
     * Returns the public-side Logical_Switch that fronts the LR's external port. Per-network for
     * isolated ({@code cs-pub-<networkId>}), shared across all tiers of a VPC for VPC tiers
     * ({@code cs-vpc-pub-<vpcId>}).
     */
    protected String getPublicLogicalSwitchNameForNetwork(Network network) {
        Long vpcId = network.getVpcId();
        return vpcId != null ? String.format("cs-vpc-pub-%d", vpcId) : String.format("cs-pub-%d", network.getId());
    }

    /** Name of the LR-side router port attached to the public Logical_Switch. */
    protected String getPublicRouterPortNameForNetwork(Network network) {
        return "lrp-" + getPublicLogicalSwitchNameForNetwork(network);
    }

    /**
     * Name of the LSP on the public LS that pairs with the public LRP. OVN names router-type
     * Logical_Switch_Ports as {@code lsp-<lrp_name>}; firewall ACL matches and gARP announcements
     * target this LSP.
     */
    protected String getPublicRouterSwitchPortNameForNetwork(Network network) {
        return "lsp-" + getPublicRouterPortNameForNetwork(network);
    }

    /**
     * Creates the LR for the network and wires it to the guest Logical_Switch with an internal-only
     * router port whose IP is the network gateway. External attachment / NAT rules are added later
     * in {@link #applyIps(Network, java.util.List, java.util.Set)} when CloudStack provisions a
     * source NAT IP for the network.
     */
    protected void createRouterAndAttachToGuest(OvnProviderVO provider, Network network) {
        if (network.getCidr() == null || network.getGateway() == null) {
            return;
        }
        String routerName = getRouterNameForNetwork(network);
        Map<String, String> lrExternalIds = new HashMap<>();
        lrExternalIds.put("cloudstack_network_id", String.valueOf(network.getId()));
        lrExternalIds.put("cloudstack_network_uuid", network.getUuid());
        lrExternalIds.put("cloudstack_zone_id", String.valueOf(network.getDataCenterId()));
        ovnNbClient.createLogicalRouter(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, lrExternalIds);
        String prefix = network.getCidr().contains("/")
                ? network.getCidr().substring(network.getCidr().indexOf('/'))
                : "/24";
        String lrpNetwork = network.getGateway() + prefix;
        ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, getLogicalSwitchName(network),
                "lrp-" + getLogicalSwitchName(network), buildRouterMac(network.getId(), false),
                java.util.Collections.singletonList(lrpNetwork));
    }

    private static String buildRouterMac(long networkId, boolean external) {
        return String.format("fa:16:3e:%02x:%02x:%02x",
                external ? 0xfe : 0xfd,
                (int) ((networkId >> 8) & 0xff),
                (int) (networkId & 0xff));
    }

    /**
     * Looks up CloudStack-allocated source NAT public IPs for the network and provisions the
     * full external attachment (public LS + localnet port + LR external port + snat rule) for
     * each. Idempotent: re-running on an already-provisioned LR is a no-op.
     */
    protected void applySourceNatForNetwork(OvnProviderVO provider, Network network) {
        if (network.getCidr() == null) {
            return;
        }
        List<IPAddressVO> ips = ipAddressDao.listByAssociatedNetwork(network.getId(), true);
        if (ips == null || ips.isEmpty()) {
            return;
        }
        String routerName = getRouterNameForNetwork(network);
        String publicLs = getPublicLogicalSwitchNameForNetwork(network);
        String localnet = provider.getLocalnetName();
        String externalBridge = provider.getExternalBridge();
        String guestCidr = network.getCidr();
        for (IPAddressVO ipVo : ips) {
            if (!ipVo.isSourceNat() || ipVo.getAddress() == null) {
                continue;
            }
            String externalIp = ipVo.getAddress().addr();
            VlanVO vlan = vlanDao.findById(ipVo.getVlanId());
            String netmask = vlan != null && vlan.getVlanNetmask() != null ? vlan.getVlanNetmask() : "255.255.240.0";
            String externalGateway = vlan != null ? vlan.getVlanGateway() : null;
            Integer vlanTag = null;
            if (vlan != null && vlan.getVlanTag() != null && !"untagged".equalsIgnoreCase(vlan.getVlanTag())) {
                String tagPart = vlan.getVlanTag().replaceAll("^vlan://", "");
                try { vlanTag = Integer.parseInt(tagPart); } catch (NumberFormatException ignored) { }
            }
            // VlanVO is fetched lazily in DAO; for now we let CloudStack stamp the localnet port
            // without a vlan (admin can override via the localnet on br-ex if needed).
            Map<String, String> publicLsExt = new HashMap<>();
            publicLsExt.put("cloudstack_network_id", String.valueOf(network.getId()));
            publicLsExt.put("cloudstack_role", "public");
            ovnNbClient.createLogicalSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs, publicLsExt);
            ovnNbClient.addLocalnetPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs, "ln-" + publicLs, localnet != null ? localnet : externalBridge, vlanTag);
            String prefix = "/" + maskToPrefix(netmask != null ? netmask : "255.255.240.0");
            String publicLrpName = getPublicRouterPortNameForNetwork(network);
            ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, publicLs,
                    publicLrpName, buildRouterMac(network.getId(), true),
                    java.util.Collections.singletonList(externalIp + prefix));
            // Anchor the external LRP to a chassis so ovn-northd materialises lr_in_dnat /
            // lr_in_unsnat / lr_out_snat for the NAT rules attached to this router.
            String anchorChassis = pickAnchorChassis(provider, network);
            if (anchorChassis != null) {
                ovnNbClient.setLrpGatewayChassis(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        publicLrpName, anchorChassis, 10);
            }
            Map<String, String> natExt = new HashMap<>();
            natExt.put("cloudstack_network_id", String.valueOf(network.getId()));
            natExt.put("cloudstack_nat_kind", "source");
            ovnNbClient.addNatRule(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, "snat", externalIp, guestCidr, natExt);
            if (externalGateway != null && !externalGateway.isEmpty()) {
                ovnNbClient.addStaticRoute(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, "0.0.0.0/0", externalGateway);
            }
            applyNatAddressesAnnouncement(provider, network);
        }
    }

    /**
     * Tells ovn-controller (running on the gateway chassis for this LR) to announce the public
     * IPs of this network via gratuitous ARPs. Without this, the upstream switch / router only
     * learns our LR's MAC when it ARPs for one of those IPs - which races against any other
     * device on the public segment that may also claim the same address (legitimately or not).
     *
     * <p>The mechanism: ovn-controller, when it claims the cr-lrp Port_Binding for this LR's
     * gateway, looks at {@code options:nat-addresses} on the type=router LSP that peers the
     * external LRP. If it is set to the explicit {@code "<MAC> <IP> ..."} format, ovn-controller
     * emits gARP for each IP. The {@code router} keyword only covers {@code dnat_and_snat}
     * rules with {@code logical_port} set, so it skips plain SNAT - which is why we need the
     * explicit form here.</p>
     */
    protected void applyNatAddressesAnnouncement(OvnProviderVO provider, Network network) {
        String externalLrpLsp = getPublicRouterSwitchPortNameForNetwork(network);
        String routerMac = buildRouterMac(network.getId(), true);
        StringBuilder addresses = new StringBuilder(routerMac);
        boolean any = false;
        List<IPAddressVO> ips = ipAddressDao.listByAssociatedNetwork(network.getId(), true);
        if (ips != null) {
            for (IPAddressVO ipVo : ips) {
                if (ipVo.getAddress() == null || !ipVo.isSourceNat()) {
                    continue;
                }
                addresses.append(' ').append(ipVo.getAddress().addr());
                any = true;
            }
        }
        if (!any) {
            return;
        }
        Map<String, String> options = new HashMap<>();
        options.put("nat-addresses", addresses.toString());
        // Without this, ovn-controller would also gARP every Load_Balancer VIP on the LR; we have
        // no LBs yet, but this stays consistent with the Neutron OVN driver default.
        options.put("exclude-lb-vips-from-garp", "true");
        ovnNbClient.setLspOptions(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                externalLrpLsp, options);
    }

    /**
     * Wipes every OVN artifact tied to a public IP that CloudStack is releasing. Called from
     * applyIps when ip.state == Releasing, regardless of SourceNat flag. This catches things our
     * per-feature revoke callbacks miss:
     *
     * <ul>
     *   <li>Per-IP default-drop ACL ({@code cloudstack_fw_default=true cloudstack_fw_ip=&lt;ip&gt;}).
     *       Created by ensureFirewallDefaultDeny without a rule_id, so applyFWRules revoke would
     *       not delete it. Without explicit cleanup it stays for the next tenant of the IP.</li>
     *   <li>Any leftover {@code allow-related} ACL still tagged with this IP, in case a
     *       FirewallRule revoke arrived out of order.</li>
     *   <li>StaticNat dnat_and_snat NAT rows on this external IP that the StaticNat revoke
     *       callback may have skipped (defensive).</li>
     *   <li>Re-emits {@code nat-addresses} on the public LSP so the released IP stops being
     *       announced via gARP.</li>
     * </ul>
     */
    protected void cleanupPublicIpArtifacts(OvnProviderVO provider, Network network, String externalIp) {
        String publicLs = getPublicLogicalSwitchNameForNetwork(network);
        String routerName = getRouterNameForNetwork(network);
        // ACLs: matches both per-rule and the default-drop, since both carry cloudstack_fw_ip.
        ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                publicLs, "cloudstack_fw_ip", externalIp);
        // dnat_and_snat NATs (StaticNat) on this IP — defensive; applyStaticNats normally clears.
        ovnNbClient.removeNatRulesByExternalIp(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, "dnat_and_snat", externalIp);
        // Load_Balancer rows pinned to this IP — defensive; applyLBRules revoke normally clears.
        // We tag every LB row with cloudstack_lb_ip in programLBRule for exactly this lookup.
        ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                "cloudstack_lb_ip", externalIp);
        // Refresh gARP announcement so this IP is no longer claimed by us.
        applyNatAddressesAnnouncement(provider, network);
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
                // Wipe any Load_Balancer rows owned by this network before tearing down the LR/LS
                // they were attached to. If the network is destroyed without an explicit LB revoke
                // (e.g. force-delete path) the LB row would otherwise remain orphaned in NB DB.
                ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        "cloudstack_network_id", String.valueOf(network.getId()));
                ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), getPublicLogicalSwitchNameForNetwork(network));
                ovnNbClient.deleteLogicalRouter(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), getRouterNameForNetwork(network));
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
        if (network.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN
                || ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(network);
        String routerName = getRouterNameForNetwork(network);
        String publicLs = getPublicLogicalSwitchNameForNetwork(network);
        String localnet = provider.getLocalnetName();
        String guestCidr = network.getCidr();
        String externalBridge = provider.getExternalBridge();
        try {
            for (PublicIpAddress ip : ipAddress) {
                String externalIp = ip.getAddress() != null ? ip.getAddress().addr() : null;
                if (externalIp == null) {
                    continue;
                }
                // Releasing IP: drop every artifact tagged with that IP regardless of whether it
                // is SourceNat or not. CloudStack delivers fw/PF revoke through dedicated callbacks,
                // but the per-IP default-drop ACL we plant via ensureFirewallDefaultDeny carries
                // no rule_id - it would otherwise stay behind, blocking traffic if the same public
                // IP is later reassigned. Same idea for any leftover dnat_and_snat NAT row.
                if (ip.getState() == com.cloud.network.IpAddress.State.Releasing) {
                    cleanupPublicIpArtifacts(provider, network, externalIp);
                    if (ip.isSourceNat()) {
                        ovnNbClient.removeNatRule(provider.getNbConnection(),
                                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                                routerName, "snat", externalIp, guestCidr);
                    }
                    continue;
                }
                if (ip.isSourceNat() && Boolean.TRUE.equals(services.contains(Network.Service.SourceNat))) {
                    // Ensure public-side LS + localnet port + LR external attachment exist
                    Map<String, String> publicLsExt = new HashMap<>();
                    publicLsExt.put("cloudstack_network_id", String.valueOf(network.getId()));
                    publicLsExt.put("cloudstack_role", "public");
                    ovnNbClient.createLogicalSwitch(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            publicLs, publicLsExt);
                    Integer vlanTag = null;
                    try {
                        if (ip.getVlanTag() != null) vlanTag = Integer.valueOf(ip.getVlanTag());
                    } catch (NumberFormatException ignored) { /* vlan may be 'untagged' */ }
                    ovnNbClient.addLocalnetPort(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            publicLs, "ln-" + publicLs, localnet != null ? localnet : externalBridge, vlanTag);
                    String prefix = ip.getNetmask() != null ? "/" + maskToPrefix(ip.getNetmask()) : "/20";
                    ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, publicLs,
                            getPublicRouterPortNameForNetwork(network), buildRouterMac(network.getId(), true),
                            java.util.Collections.singletonList(externalIp + prefix));
                    Map<String, String> natExt = new HashMap<>();
                    natExt.put("cloudstack_network_id", String.valueOf(network.getId()));
                    natExt.put("cloudstack_nat_kind", "source");
                    ovnNbClient.addNatRule(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, "snat", externalIp, guestCidr, natExt);
                }
            }
            // Refresh nat-addresses on the gateway-side LSP so ovn-controller emits gARPs for
            // every current SourceNat IP. Idempotent and skipped when nothing changed.
            applyNatAddressesAnnouncement(provider, network);
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
        }
        return true;
    }

    /**
     * Picks a chassis name to host the centralised gateway pipeline for this network's LR and,
     * as a side-effect, prunes any {@code Gateway_Chassis} row on the network's public LRP that
     * points to a chassis no longer registered in SB. This fixes a real problem we hit when a
     * KVM host is destroyed and re-added: the host re-registers with a fresh OVS system-id, and
     * any old Gateway_Chassis row keeps pointing to the dead system-id - ovn-northd refuses to
     * claim the cr-lrp port and SNAT/DNAT silently break.
     *
     * <p>Returns the chassis system-id we want anchored, or {@code null} when SB has no live
     * chassis at all (the LRP simply has no anchor in that case and the caller falls through).</p>
     */
    protected String pickAnchorChassis(OvnProviderVO provider, Network network) {
        if (provider == null || provider.getSbConnection() == null || provider.getSbConnection().isEmpty()) {
            logger.warn("No OVN SB connection configured; cannot pick a Gateway_Chassis anchor for network {}", network);
            return null;
        }
        try {
            java.util.List<String> chassisNames = ovnNbClient.listSouthboundChassisNames(
                    provider.getSbConnection(), provider.getCaCertPath(),
                    provider.getClientCertPath(), provider.getClientPrivateKeyPath());
            if (chassisNames == null || chassisNames.isEmpty()) {
                logger.warn("OVN SB reports no registered Chassis yet; deferring Gateway_Chassis anchor for network {}", network);
                return null;
            }
            // Drop any stale Gateway_Chassis row on the public LRP whose chassis_name is not in
            // the live set. This must run BEFORE we hand back a name to the caller, because
            // setLrpGatewayChassis is idempotent on (lrp_name, chassis_name) and will not detect
            // a name change on its own.
            String publicLrpName = getPublicRouterPortNameForNetwork(network);
            try {
                ovnNbClient.pruneStaleGatewayChassis(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        publicLrpName, new java.util.HashSet<>(chassisNames));
            } catch (CloudRuntimeException e) {
                // LRP may not exist yet on the very first implement - that is fine, no rows to prune.
                logger.debug("Skipping Gateway_Chassis prune for {} ({})", publicLrpName, e.getMessage());
            }
            // Deterministic pick: sort by name and rotate by network id so several LRs do not
            // all pile on the same chassis. The Chassis row is keyed by the OVS system-id that
            // ovn-controller registers on each hypervisor.
            java.util.Collections.sort(chassisNames);
            return chassisNames.get((int) (Math.abs(network.getId()) % chassisNames.size()));
        } catch (Exception e) {
            logger.warn("Failed to query OVN SB for Chassis names while anchoring network {}: {}", network, e.getMessage());
            return null;
        }
    }

    private static int maskToPrefix(String netmask) {
        try {
            String[] parts = netmask.split("\\.");
            int bits = 0;
            for (String p : parts) {
                bits += Integer.bitCount(Integer.parseInt(p) & 0xff);
            }
            return bits;
        } catch (Exception e) {
            return 24;
        }
    }

    @Override
    public IpDeployer getIpDeployer(Network network) {
        return this;
    }

    @Override
    public boolean applyStaticNats(Network config, List<? extends StaticNat> rules) throws ResourceUnavailableException {
        if (config.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN || rules == null || rules.isEmpty()) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(config);
        String routerName = getRouterNameForNetwork(config);
        try {
            // Anchor the public LRP to a chassis so ovn-northd materialises the lr_in_dnat
            // pipeline. Without Gateway_Chassis, dnat_and_snat NAT rows are silently ignored
            // by lr_in_dnat. setLrpGatewayChassis is idempotent.
            String anchorChassis = pickAnchorChassis(provider, config);
            if (anchorChassis != null) {
                ovnNbClient.setLrpGatewayChassis(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        getPublicRouterPortNameForNetwork(config), anchorChassis, 10);
            }
            for (StaticNat rule : rules) {
                IPAddressVO ipVo = ipAddressDao.findById(rule.getSourceIpAddressId());
                if (ipVo == null || ipVo.getAddress() == null) {
                    continue;
                }
                String externalIp = ipVo.getAddress().addr();
                String logicalIp = rule.getDestIpAddress();
                if (rule.isForRevoke() || logicalIp == null || logicalIp.isEmpty()) {
                    ovnNbClient.removeNatRulesByExternalIp(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, "dnat_and_snat", externalIp);
                    continue;
                }
                Map<String, String> ext = new HashMap<>();
                ext.put("cloudstack_network_id", String.valueOf(config.getId()));
                ext.put("cloudstack_nat_kind", "static");
                NicVO targetNic = nicDao.findByIp4AddressAndNetworkId(logicalIp, config.getId());
                String distributedLogicalPort = targetNic != null ? targetNic.getUuid() : null;
                String distributedMac = buildRouterMac(config.getId(), true);
                ovnNbClient.addNatRule(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, "dnat_and_snat", externalIp, logicalIp, ext,
                        distributedMac, distributedLogicalPort);
            }
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, config.getDataCenterId());
        }
        return true;
    }

    /**
     * Cap on the size of a single PortForwarding rule's port range. CloudStack lets the user
     * declare arbitrarily large ranges; we expand each rule into one Load_Balancer.vips entry
     * per port, so very large ranges would balloon the NB row. 256 is enough for the common
     * case (small service ranges) without risking an unbounded transaction.
     */
    private static final int MAX_PF_RANGE = 256;

    @Override
    public boolean applyPFRules(Network network, List<PortForwardingRule> rules) throws ResourceUnavailableException {
        if (network.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN || rules == null || rules.isEmpty()) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(network);
        String routerName = getRouterNameForNetwork(network);
        String guestLs = getLogicalSwitchName(network);
        try {
            for (PortForwardingRule rule : rules) {
                programPortForwardingRule(provider, network, routerName, guestLs, rule);
            }
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
        }
        return true;
    }

    /**
     * Translates one CloudStack {@link PortForwardingRule} into an OVN {@code Load_Balancer} row.
     * Naming: {@code pf-<rule_id>-<protocol>}. The LB carries one VIP entry per port in the
     * source range mapped to the corresponding destination port. Backed by {@link
     * OvnNbClient#createOrReplaceLoadBalancer}, then attached to the network's Logical_Router and
     * its guest Logical_Switch (the LS attachment is the Neutron-recommended workaround for
     * RHBZ#2043543 — VMs talking to their own FIP need the LB visible on the LS too).
     *
     * <p>NAT-based PortForwarding was removed from OVN NB 24.03 (the {@code external_port} and
     * {@code protocol} columns are gone), and even where it existed it could not remap a public
     * port to a different internal port — which CloudStack semantics require. Load_Balancer
     * gives us both the port translation and the per-rule revoke story (delete the LB by
     * external_ids tag).</p>
     */
    protected void programPortForwardingRule(OvnProviderVO provider, Network network,
                                              String routerName, String guestLs, PortForwardingRule rule) {
        String ruleTag = String.valueOf(rule.getId());
        if (rule.getState() == FirewallRule.State.Revoke) {
            ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    "cloudstack_pf_rule_id", ruleTag);
            return;
        }

        IPAddressVO ipVo = ipAddressDao.findById(rule.getSourceIpAddressId());
        if (ipVo == null || ipVo.getAddress() == null) {
            logger.warn("PF rule {} references unknown source IP id {} - skipping", rule.getId(), rule.getSourceIpAddressId());
            return;
        }
        String externalIp = ipVo.getAddress().addr();
        String logicalIp = rule.getDestinationIpAddress() != null ? rule.getDestinationIpAddress().addr() : null;
        if (logicalIp == null || logicalIp.isEmpty()) {
            logger.warn("PF rule {} has no destination IP - skipping", rule.getId());
            return;
        }
        String protocol = rule.getProtocol() != null ? rule.getProtocol().toLowerCase() : "tcp";
        if (!"tcp".equals(protocol) && !"udp".equals(protocol) && !"sctp".equals(protocol)) {
            logger.warn("PF rule {} protocol [{}] is not supported by OVN Load_Balancer - skipping", rule.getId(), protocol);
            return;
        }

        int extStart = rule.getSourcePortStart() != null ? rule.getSourcePortStart() : 0;
        int extEnd = rule.getSourcePortEnd() != null ? rule.getSourcePortEnd() : extStart;
        int destStart = rule.getDestinationPortStart();
        int destEnd = rule.getDestinationPortEnd();
        int extRange = extEnd - extStart + 1;
        int destRange = destEnd - destStart + 1;
        if (extRange <= 0) {
            logger.warn("PF rule {} has invalid source port range [{}, {}]", rule.getId(), extStart, extEnd);
            return;
        }
        if (extRange > MAX_PF_RANGE) {
            logger.warn("PF rule {} source range size [{}] exceeds MAX_PF_RANGE [{}] - rejecting",
                    rule.getId(), extRange, MAX_PF_RANGE);
            return;
        }
        // CloudStack allows the destination range to be either equal in length to the source
        // range (1:1 mapping with a possible offset) or a single port (all source ports map to
        // the same internal port). Anything else is ambiguous.
        boolean destSinglePort = destRange == 1;
        if (destRange != extRange && !destSinglePort) {
            logger.warn("PF rule {} dest range [{}-{}] mismatches source range [{}-{}] - skipping",
                    rule.getId(), destStart, destEnd, extStart, extEnd);
            return;
        }

        Map<String, String> vips = new HashMap<>();
        for (int i = 0; i < extRange; i++) {
            int extPort = extStart + i;
            int destPort = destSinglePort ? destStart : destStart + i;
            vips.put(externalIp + ":" + extPort, logicalIp + ":" + destPort);
        }

        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_pf_rule_id", ruleTag);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        ext.put("cloudstack_nat_kind", "portforward");

        // hairpin_snat_ip lets a VM behind the FIP talk to its own public IP without ovn-northd
        // mis-routing the reply. Cost: a tiny extra rewrite. Neutron sets it unconditionally for
        // FIP-style LBs.
        Map<String, String> options = new HashMap<>();
        options.put("hairpin_snat_ip", externalIp);

        String lbName = "pf-" + ruleTag + "-" + protocol;
        ovnNbClient.createOrReplaceLoadBalancer(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                lbName, protocol, vips, ext, options);
        ovnNbClient.attachLoadBalancerToRouter(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, lbName);
        ovnNbClient.attachLoadBalancerToSwitch(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                guestLs, lbName);
    }

    @Override
    public boolean applyFWRules(Network network, List<? extends FirewallRule> rules) throws ResourceUnavailableException {
        if (network.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN || rules == null || rules.isEmpty()) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(network);
        String publicLs = getPublicLogicalSwitchNameForNetwork(network);
        String publicLrpLsp = getPublicRouterSwitchPortNameForNetwork(network);
        try {
            for (FirewallRule rule : rules) {
                programFirewallRule(provider, network, publicLs, publicLrpLsp, rule);
            }
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
        }
        return true;
    }

    /**
     * Translates a single {@link FirewallRule} into an OVN ACL row attached to the network's
     * public Logical_Switch. The default-deny scoped to the public IP is kept fresh on every
     * call so newly-allocated IPs only become reachable through explicit allow rules. Revoke
     * state simply deletes the per-rule row by external_ids tag.
     */
    protected void programFirewallRule(OvnProviderVO provider, Network network, String publicLs,
                                        String publicLrpLsp, FirewallRule rule) {
        IPAddressVO ipVo = ipAddressDao.findById(rule.getSourceIpAddressId());
        if (ipVo == null || ipVo.getAddress() == null) {
            return;
        }
        String publicIp = ipVo.getAddress().addr();
        // Make sure the per-IP default-drop is in place before we layer allow rules on top.
        ensureFirewallDefaultDeny(provider, network, publicLs, publicLrpLsp, publicIp);

        String ruleTag = "fw-" + rule.getId();
        if (rule.getState() == FirewallRule.State.Revoke) {
            ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs, "cloudstack_fw_rule_id", String.valueOf(rule.getId()));
            return;
        }

        String matchExpr = buildFirewallMatch(publicLrpLsp, publicIp, rule);
        if (matchExpr == null) {
            // Unsupported protocol or empty rule - skip silently.
            return;
        }
        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_fw_rule_id", String.valueOf(rule.getId()));
        ext.put("cloudstack_fw_ip", publicIp);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                publicLs, ruleTag, "to-lport", 1000L, matchExpr, "allow-related", ext);
    }

    /**
     * Builds the OVN match expression for a single firewall rule. ACLs on the public LS are
     * evaluated in the {@code to-lport} direction toward the router patch port, so we match
     * before DNAT happens - {@code ip4.dst} is still the public IP, {@code tcp/udp.dst} is
     * still the public-side port the user typed in CloudStack.
     */
    protected String buildFirewallMatch(String publicLrpLsp, String publicIp, FirewallRule rule) {
        String proto = rule.getProtocol() == null ? "" : rule.getProtocol().toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("outport == \"").append(publicLrpLsp).append("\" && ip4");
        sb.append(" && ip4.dst == ").append(publicIp);
        // Scope to source CIDRs if the user provided any, otherwise leave the rule open to 0.0.0.0/0.
        List<String> sourceCidrs = rule.getSourceCidrList();
        if (sourceCidrs != null && !sourceCidrs.isEmpty()) {
            StringBuilder cidrs = new StringBuilder();
            boolean first = true;
            for (String cidr : sourceCidrs) {
                if (cidr == null || cidr.isEmpty() || "0.0.0.0/0".equals(cidr)) {
                    cidrs.setLength(0);
                    break;
                }
                if (!first) cidrs.append(", ");
                cidrs.append(cidr);
                first = false;
            }
            if (cidrs.length() > 0) {
                sb.append(" && ip4.src == {").append(cidrs).append("}");
            }
        }
        switch (proto) {
            case "tcp":
            case "udp": {
                sb.append(" && ").append(proto);
                Integer s = rule.getSourcePortStart();
                Integer e = rule.getSourcePortEnd();
                if (s != null && e != null) {
                    if (s.equals(e)) {
                        sb.append(" && ").append(proto).append(".dst == ").append(s);
                    } else {
                        sb.append(" && ").append(proto).append(".dst >= ").append(s)
                                .append(" && ").append(proto).append(".dst <= ").append(e);
                    }
                }
                break;
            }
            case "icmp": {
                sb.append(" && icmp4");
                if (rule.getIcmpType() != null && rule.getIcmpType() != -1) {
                    sb.append(" && icmp4.type == ").append(rule.getIcmpType());
                }
                if (rule.getIcmpCode() != null && rule.getIcmpCode() != -1) {
                    sb.append(" && icmp4.code == ").append(rule.getIcmpCode());
                }
                break;
            }
            case "all":
            case "":
                // No protocol filter - any IPv4 traffic to the public IP.
                break;
            default:
                logger.warn("Skipping firewall rule {} with unsupported protocol [{}]", rule.getId(), proto);
                return null;
        }
        return sb.toString();
    }

    /**
     * Installs (or refreshes) the per-public-IP default-drop ACL. Without this, the public LS
     * would forward every DNAT'd packet because OVN ACLs default-allow when none of the rules
     * match - that is the opposite of CloudStack's expectation that an unprotected public IP
     * is unreachable.
     */
    protected void ensureFirewallDefaultDeny(OvnProviderVO provider, Network network, String publicLs,
                                              String publicLrpLsp, String publicIp) {
        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_fw_default", "true");
        ext.put("cloudstack_fw_ip", publicIp);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        String match = "outport == \"" + publicLrpLsp + "\" && ip4 && ip4.dst == " + publicIp;
        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                publicLs, "fw-default-" + publicIp, "to-lport", 100L, match, "drop", ext);
    }

    @Override
    public boolean applyNetworkACLs(Network config, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        if (config.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(config);
        String guestLs = getLogicalSwitchName(config);
        String networkId = String.valueOf(config.getId());
        try {
            // Full sync: wipe every ACL currently tagged to this network and re-install
            // the authoritative set. This keeps OVN state consistent even when rules are
            // reordered or the ACL list is replaced entirely.
            ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    guestLs, "cloudstack_network_id", networkId);

            if (rules != null) {
                for (NetworkACLItem rule : rules) {
                    if (rule.getState() == NetworkACLItem.State.Revoke) {
                        continue;
                    }
                    programNetworkAclRule(provider, config, guestLs, rule);
                }
            }
            // Always install a default-deny at priority 1 for both directions so that
            // unmatched traffic is dropped (OVN ACL default is allow when no rule matches).
            ensureNetworkAclDefaultDeny(provider, config, guestLs);
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, config.getDataCenterId());
        }
        return true;
    }

    /**
     * Translates a single {@link NetworkACLItem} into an OVN ACL row on the guest Logical_Switch.
     * CloudStack {@code Ingress} (traffic into the VM) maps to OVN {@code to-lport}; {@code Egress}
     * (traffic from the VM) maps to {@code from-lport}. Priority is derived from the rule number
     * so that lower CloudStack rule numbers take precedence (higher OVN priority).
     */
    protected void programNetworkAclRule(OvnProviderVO provider, Network network,
                                          String guestLs, NetworkACLItem rule) {
        String direction = rule.getTrafficType() == NetworkACLItem.TrafficType.Ingress
                ? "to-lport" : "from-lport";
        String aclAction = rule.getAction() == NetworkACLItem.Action.Allow ? "allow-related" : "drop";
        // CloudStack rule number starts at 1; lower = higher CloudStack priority = higher OVN prio.
        long ovnPriority = Math.max(2L, 1000L - rule.getNumber());
        String matchExpr = buildNetworkAclMatch(direction, rule);
        if (matchExpr == null) {
            return;
        }
        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_acl_rule_id", String.valueOf(rule.getId()));
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        ext.put("cloudstack_acl_direction", direction);
        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                guestLs, "nacl-" + rule.getId(), direction, ovnPriority, matchExpr, aclAction, ext);
    }

    /**
     * Builds the OVN match expression for a NetworkACL rule on the guest LS. For {@code to-lport}
     * (ingress) the source address is matched; for {@code from-lport} (egress) the destination
     * address is matched.
     */
    protected String buildNetworkAclMatch(String ovnDirection, NetworkACLItem rule) {
        boolean isIngress = "to-lport".equals(ovnDirection);
        String proto = rule.getProtocol() == null ? "all" : rule.getProtocol().toLowerCase();
        StringBuilder sb = new StringBuilder("ip4");
        List<String> cidrs = rule.getSourceCidrList();
        if (cidrs != null && !cidrs.isEmpty()) {
            StringBuilder cidrSet = new StringBuilder();
            for (String cidr : cidrs) {
                if (cidr == null || cidr.isEmpty() || "0.0.0.0/0".equals(cidr)) {
                    cidrSet.setLength(0);
                    break;
                }
                if (cidrSet.length() > 0) cidrSet.append(", ");
                cidrSet.append(cidr);
            }
            if (cidrSet.length() > 0) {
                // For ingress the CIDR is the packet source; for egress it is the destination.
                sb.append(isIngress ? " && ip4.src == {" : " && ip4.dst == {")
                        .append(cidrSet).append("}");
            }
        }
        switch (proto) {
            case "tcp":
            case "udp": {
                sb.append(" && ").append(proto);
                Integer portStart = rule.getSourcePortStart();
                Integer portEnd = rule.getSourcePortEnd();
                if (portStart != null && portEnd != null) {
                    String portCol = isIngress ? proto + ".dst" : proto + ".src";
                    if (portStart.equals(portEnd)) {
                        sb.append(" && ").append(portCol).append(" == ").append(portStart);
                    } else {
                        sb.append(" && ").append(portCol).append(" >= ").append(portStart)
                                .append(" && ").append(portCol).append(" <= ").append(portEnd);
                    }
                }
                break;
            }
            case "icmp": {
                sb.append(" && icmp4");
                if (rule.getIcmpType() != null && rule.getIcmpType() != -1) {
                    sb.append(" && icmp4.type == ").append(rule.getIcmpType());
                }
                if (rule.getIcmpCode() != null && rule.getIcmpCode() != -1) {
                    sb.append(" && icmp4.code == ").append(rule.getIcmpCode());
                }
                break;
            }
            case "all":
                break;
            default:
                logger.warn("Skipping NetworkACL rule {} with unsupported protocol [{}]", rule.getId(), proto);
                return null;
        }
        return sb.toString();
    }

    /**
     * Installs a default-drop ACL at priority 1 for both directions on the guest LS. Without this
     * OVN would allow any traffic not matched by an explicit rule (OVN ACL default is allow-all).
     */
    protected void ensureNetworkAclDefaultDeny(OvnProviderVO provider, Network network, String guestLs) {
        String networkId = String.valueOf(network.getId());
        for (String dir : new String[]{"to-lport", "from-lport"}) {
            Map<String, String> ext = new HashMap<>();
            ext.put("cloudstack_acl_default", "true");
            ext.put("cloudstack_network_id", networkId);
            ext.put("cloudstack_acl_direction", dir);
            ovnNbClient.addAclOnLs(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    guestLs, "nacl-default-" + dir, dir, 1L, "ip4", "drop", ext);
        }
    }

    @Override
    public boolean reorderAclRules(Vpc vpc, List<? extends Network> networks, List<? extends NetworkACLItem> networkACLItems) {
        return true;
    }

    @Override
    public boolean applyLBRules(Network network, List<LoadBalancingRule> rules) throws ResourceUnavailableException {
        // CloudStack's LB manager invokes this with the rules currently in transition, not the
        // full active set on the network - so an empty list means "nothing to apply right now",
        // not "wipe all LBs". Removal is driven by individual rules in Revoke state (handled in
        // programLBRule) and, as a safety net, by destroy() / cleanupPublicIpArtifacts which
        // sweep by external_ids when a network or public IP is being torn down.
        if (network.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN || rules == null || rules.isEmpty()) {
            return true;
        }
        OvnProviderVO provider = getProviderForNetwork(network);
        String routerName = getRouterNameForNetwork(network);
        String guestLs = getLogicalSwitchName(network);
        try {
            for (LoadBalancingRule rule : rules) {
                programLBRule(provider, network, routerName, guestLs, rule);
            }
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
        }
        return true;
    }

    /**
     * Translates one CloudStack {@link LoadBalancingRule} into an OVN {@code Load_Balancer} row.
     * Naming: {@code lb-<rule_id>-<protocol>}. Each VM destination becomes one entry in the
     * vips map's backend list. Algorithm and stickiness are mapped to OVN's
     * {@code selection_fields} + {@code options:affinity_timeout}. HealthCheckPolicy, when
     * present, becomes one Load_Balancer_Health_Check row referenced from {@code health_check}
     * with {@code ip_port_mappings} populated for SB Service_Monitor source attribution.
     *
     * <p>OVN LB is L4. CloudStack rules with {@code tcp-proxy}/{@code http}/{@code ssl}
     * protocols, {@code leastconn} algorithm, or cookie-based stickiness are rejected upstream
     * by {@link #validateLBRule}. Should one slip through (e.g. via DB-direct mutation), we log
     * and skip rather than raise.</p>
     */
    protected void programLBRule(OvnProviderVO provider, Network network,
                                  String routerName, String guestLs, LoadBalancingRule rule) {
        String ruleTag = String.valueOf(rule.getId());
        String lbName = null;

        if (rule.getState() == FirewallRule.State.Revoke) {
            ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    "cloudstack_lb_rule_id", ruleTag);
            return;
        }

        IPAddressVO ipVo = ipAddressDao.findById(rule.getLb().getSourceIpAddressId());
        if (ipVo == null || ipVo.getAddress() == null) {
            logger.warn("LB rule {} references unknown source IP id {}", rule.getId(), rule.getLb().getSourceIpAddressId());
            return;
        }
        String externalIp = ipVo.getAddress().addr();

        String protocol = rule.getProtocol() != null ? rule.getProtocol().toLowerCase() : "tcp";
        // Capability advertises only tcp/udp; keep validateLBRule and the datapath programmer in
        // sync so an invalid protocol bubbling through (e.g. via direct DB mutation) is logged
        // and skipped instead of silently creating a malformed Load_Balancer row.
        if (!"tcp".equals(protocol) && !"udp".equals(protocol)) {
            logger.warn("LB rule {} protocol [{}] is not supported by the OVN provider (tcp/udp only); skipping "
                    + "(validateLBRule should have rejected this)", rule.getId(), protocol);
            return;
        }
        if (rule.getSourcePortStart() == null) {
            logger.warn("LB rule {} has no source port; skipping", rule.getId());
            return;
        }
        int publicPort = rule.getSourcePortStart();

        // Build the backend list ("vm_ip:port,vm_ip:port,...") from active destinations.
        StringBuilder backends = new StringBuilder();
        Map<String, String> ipPortMappings = new HashMap<>();
        String hcSourceIp = network.getGateway();
        for (LoadBalancingRule.LbDestination dest : rule.getDestinations()) {
            if (dest.isRevoked()) {
                continue;
            }
            String destIp = dest.getIpAddress();
            int destPort = dest.getDestinationPortStart();
            if (destIp == null || destIp.isEmpty()) {
                continue;
            }
            if (backends.length() > 0) {
                backends.append(",");
            }
            backends.append(destIp).append(":").append(destPort);

            // Populate ip_port_mappings: <backend_ip> -> <lsp_name>:<source_ip>. The lsp_name is
            // the NIC UUID (matches our LSP naming scheme in createLogicalSwitchPort) and the
            // source_ip is the LR's gateway IP on the guest LS - that is the address from which
            // OVN's monitor will source HC probes.
            NicVO targetNic = nicDao.findByIp4AddressAndNetworkId(destIp, network.getId());
            if (targetNic != null && hcSourceIp != null && !hcSourceIp.isEmpty()) {
                ipPortMappings.put(destIp, targetNic.getUuid() + ":" + hcSourceIp);
            }
        }
        if (backends.length() == 0) {
            // No live destinations - drop the LB. Idempotent if it was never created.
            logger.debug("LB rule {} has no live destinations; removing any existing LB row", rule.getId());
            ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    "cloudstack_lb_rule_id", ruleTag);
            return;
        }

        Map<String, String> vips = java.util.Collections.singletonMap(externalIp + ":" + publicPort, backends.toString());

        // Algorithm and stickiness → selection_fields + affinity_timeout.
        Set<String> selectionFields = null;
        Long affinityTimeout = null;
        String algorithm = rule.getAlgorithm() != null ? rule.getAlgorithm().toLowerCase() : "roundrobin";
        if ("source".equals(algorithm)) {
            selectionFields = new java.util.LinkedHashSet<>();
            selectionFields.add("ip_src");
        }
        if (rule.getStickinessPolicies() != null) {
            for (LoadBalancingRule.LbStickinessPolicy sticky : rule.getStickinessPolicies()) {
                if (sticky.isRevoked()) continue;
                String method = sticky.getMethodName() != null ? sticky.getMethodName() : "";
                if ("SourceBased".equalsIgnoreCase(method)) {
                    if (selectionFields == null) {
                        selectionFields = new java.util.LinkedHashSet<>();
                        selectionFields.add("ip_src");
                    }
                    affinityTimeout = parseStickyTimeoutSeconds(sticky);
                } else {
                    logger.warn("LB rule {} sticky method [{}] is L7 (cookie); OVN cannot honour it - degrading to source-based",
                            rule.getId(), method);
                    if (selectionFields == null) {
                        selectionFields = new java.util.LinkedHashSet<>();
                        selectionFields.add("ip_src");
                    }
                }
            }
        }

        Map<String, String> options = new HashMap<>();
        options.put("hairpin_snat_ip", externalIp);
        if (affinityTimeout != null && affinityTimeout > 0) {
            options.put("affinity_timeout", String.valueOf(affinityTimeout));
        }

        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_lb_rule_id", ruleTag);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        // Tag with the public IP so cleanupPublicIpArtifacts can wipe LB rows when the IP is
        // released out-of-order (without going through the LB revoke callback first).
        ext.put("cloudstack_lb_ip", externalIp);
        ext.put("cloudstack_lb_kind", "loadbalancer");

        lbName = "lb-" + ruleTag + "-" + protocol;
        ovnNbClient.createOrReplaceLoadBalancer(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                lbName, protocol, vips, ext, options, selectionFields, ipPortMappings);
        ovnNbClient.attachLoadBalancerToRouter(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, lbName);
        ovnNbClient.attachLoadBalancerToSwitch(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                guestLs, lbName);

        // Health check: take the first non-revoked policy. CloudStack today only persists one
        // HealthCheckPolicy per rule but the API returns a list, so we are defensive.
        applyLBHealthCheck(provider, network, rule, lbName, externalIp + ":" + publicPort, ipPortMappings);
    }

    /**
     * Maps a CloudStack stickiness policy timeout into OVN seconds. CS uses different param
     * names depending on the method (cookie vs source-based); for SourceBased we look at
     * {@code expire}/{@code idletime}/{@code holdtime}/{@code persistence_timeout} - whichever
     * the user filled - and fall back to 180s if nothing parses.
     */
    private static long parseStickyTimeoutSeconds(LoadBalancingRule.LbStickinessPolicy sticky) {
        if (sticky.getParams() == null) return 180L;
        for (org.apache.cloudstack.api.InternalIdentity pair : java.util.Collections.<org.apache.cloudstack.api.InternalIdentity>emptyList()) { /* unused */ }
        // The Pair<String,String> instances from CloudStack have .first() / .second() accessors.
        for (com.cloud.utils.Pair<String, String> p : sticky.getParams()) {
            String name = p.first() != null ? p.first().toLowerCase() : "";
            if (name.contains("expire") || name.contains("idletime")
                    || name.contains("holdtime") || name.contains("timeout")) {
                try {
                    return Long.parseLong(p.second());
                } catch (NumberFormatException ignored) { /* fall through */ }
            }
        }
        return 180L;
    }

    /**
     * Applies (or clears) the OVN Load_Balancer_Health_Check for an LB rule. OVN HC is L4 TCP
     * only; HTTP/PING policies from CloudStack are accepted but degraded to a TCP probe with a
     * warning so the operator gets a hint to either accept it or move that workload to a
     * VirtualRouter offering.
     */
    protected void applyLBHealthCheck(OvnProviderVO provider, Network network, LoadBalancingRule rule,
                                       String lbName, String hcVip, Map<String, String> ipPortMappings) {
        java.util.List<? extends LoadBalancingRule.LbHealthCheckPolicy> policies = rule.getHealthCheckPolicies();
        LoadBalancingRule.LbHealthCheckPolicy active = null;
        if (policies != null) {
            for (LoadBalancingRule.LbHealthCheckPolicy p : policies) {
                if (!p.isRevoked()) {
                    active = p;
                    break;
                }
            }
        }
        if (active == null) {
            ovnNbClient.clearLoadBalancerHealthCheck(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    lbName);
            return;
        }
        String pingPath = active.getpingpath();
        if (pingPath != null && !pingPath.isEmpty()) {
            logger.warn("LB rule {} health check is HTTP/path-based ({}); OVN can only TCP-probe the backend port - "
                    + "honouring as TCP probe", rule.getId(), pingPath);
        }

        Map<String, String> hcOptions = new HashMap<>();
        // OVN options expect ms (interval/timeout) and integer counts. Map CS seconds to ms.
        int intervalSec = active.getHealthcheckInterval() > 0 ? active.getHealthcheckInterval() : 5;
        int responseSec = active.getResponseTime() > 0 ? active.getResponseTime() : 2;
        int healthyCount = active.getHealthcheckThresshold() > 0 ? active.getHealthcheckThresshold() : 2;
        int unhealthyCount = active.getUnhealthThresshold() > 0 ? active.getUnhealthThresshold() : 3;
        hcOptions.put("interval", String.valueOf(intervalSec));
        hcOptions.put("timeout", String.valueOf(responseSec));
        hcOptions.put("success_count", String.valueOf(healthyCount));
        hcOptions.put("failure_count", String.valueOf(unhealthyCount));

        Map<String, String> hcExt = new HashMap<>();
        hcExt.put("cloudstack_lb_rule_id", String.valueOf(rule.getId()));
        hcExt.put("cloudstack_network_id", String.valueOf(network.getId()));

        ovnNbClient.setLoadBalancerHealthCheck(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                lbName, hcVip, hcOptions, ipPortMappings, hcExt);
    }

    @Override
    public boolean validateLBRule(Network network, LoadBalancingRule rule) {
        if (network.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN) {
            return true;
        }
        // OVN Load_Balancer is L4 and we only advertise tcp/udp in the capabilities map; keep
        // this in sync with initCapabilities() so an offering that lists only tcp/udp does not
        // accept a rule we cannot program.
        String proto = rule.getProtocol() != null ? rule.getProtocol().toLowerCase() : "tcp";
        if (!"tcp".equals(proto) && !"udp".equals(proto)) {
            logger.warn("OVN LB rejecting rule {}: protocol [{}] not supported (tcp/udp only)",
                    rule.getId(), proto);
            return false;
        }
        // OVN has no per-backend connection state, so leastconn cannot be honoured. Reject
        // explicitly rather than silently degrading - capabilities only advertise roundrobin
        // and source.
        String algo = rule.getAlgorithm() != null ? rule.getAlgorithm().toLowerCase() : "";
        if ("leastconn".equals(algo)) {
            logger.warn("OVN LB rejecting rule {}: algorithm [leastconn] not supported (no backend conn state)",
                    rule.getId());
            return false;
        }
        // Reject LB rules that target the network's SourceNat IP. The same external IP would carry
        // both an LR-level snat NAT row (logical_ip=guest_cidr -> external_ip) and the LB's vips
        // map; replies from a backend are SNATed back to the SourceNat IP before the LB un-DNAT
        // can run, so the client sees a reply from an IP that doesn't match the connection it
        // opened and TCP resets. Lab-confirmed: traffic enters the LR but no SYN+ACK ever reaches
        // the upstream when LB and SourceNat share an external IP. Force the user to allocate a
        // dedicated public IP for LB.
        if (rule.getLb() != null && rule.getLb().getSourceIpAddressId() != null) {
            IPAddressVO ipVo = ipAddressDao.findById(rule.getLb().getSourceIpAddressId());
            if (ipVo != null && ipVo.isSourceNat()) {
                logger.warn("OVN LB rejecting rule {}: external IP {} is the network's SourceNat IP "
                        + "- allocate a separate public IP for the LB",
                        rule.getId(), ipVo.getAddress() != null ? ipVo.getAddress().addr() : "<null>");
                return false;
            }
        }
        return true;
    }

    @Override
    public List<LoadBalancerTO> updateHealthChecks(Network network, List<LoadBalancingRule> lbrules) {
        // TODO: query OVN SB Service_Monitor table to surface backend up/down status back to
        //       CloudStack (so the UI shows red/green per VM member). The OVN HC writes status
        //       per-backend in Service_Monitor; we'd convert each to a LbDestination state.
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
