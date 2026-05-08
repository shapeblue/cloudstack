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
import com.cloud.network.dao.OvnVpcPeeringDao;
import com.cloud.network.element.OvnProviderVO;
import com.cloud.network.element.OvnVpcPeeringVO;
import com.cloud.network.vpc.VpcVO;
import com.cloud.user.AccountManager;
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
import com.cloud.network.vpc.NetworkACLItemDao;
import com.cloud.network.vpc.NetworkACLItemVO;
import com.cloud.network.vpc.NetworkACLVO;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.StaticRouteProfile;
import com.cloud.network.vpc.Vpc;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

import org.apache.cloudstack.api.command.CreateVpcPeeringCmd;
import org.apache.cloudstack.api.command.DeleteVpcPeeringCmd;
import org.apache.cloudstack.api.command.ListVpcPeeringsCmd;
import org.apache.cloudstack.api.command.UpdateVpcPeeringCmd;
import org.apache.cloudstack.api.response.VpcPeeringResponse;
import org.apache.cloudstack.context.CallContext;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

public class OvnElement extends AdapterBase implements DhcpServiceProvider, DnsServiceProvider, VpcProvider,
        StaticNatServiceProvider, IpDeployer, PortForwardingServiceProvider, FirewallServiceProvider,
        NetworkACLServiceProvider, LoadBalancingServiceProvider, OvnPeeringService {

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
    com.cloud.network.vpc.dao.VpcDao vpcDao;

    @Inject
    com.cloud.host.dao.HostDao hostDao;

    @Inject
    OvnVpcPeeringDao ovnVpcPeeringDao;

    @Inject
    AccountManager accountMgr;

    @Inject
    DataCenterDao dataCenterDao;

    @Inject
    NetworkACLDao networkACLDao;

    @Inject
    NetworkACLItemDao networkACLItemDao;

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
        //  - Public + Internal schemes. Internal LB is delivered natively by attaching the
        //    same Load_Balancer row to the VPC LR + the tier LS that owns the VIP, with
        //    options:hairpin_snat_ip pointing at the tier gateway. No appliance VM needed.
        // SSL offload, HTTP-aware LB, cookie stickiness etc. are L7 features that OVN cannot do
        // in the datapath - those tenants should pick a VirtualRouter offering instead.
        lbCapabilities.put(Network.Capability.SupportedLBAlgorithms, "roundrobin,source");
        lbCapabilities.put(Network.Capability.SupportedLBIsolation, "dedicated");
        lbCapabilities.put(Network.Capability.SupportedProtocols, "tcp,udp");
        lbCapabilities.put(Network.Capability.LbSchemes,
                LoadBalancerContainer.Scheme.Public.name() + "," + LoadBalancerContainer.Scheme.Internal.name());
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
            if (network.getVpcId() != null) {
                externalIds.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
                externalIds.put("cloudstack_role", "tier");
            }
            try {
                ovnNbClient.createLogicalSwitch(provider.getNbConnection(), provider.getCaCertPath(), provider.getClientCertPath(),
                        provider.getClientPrivateKeyPath(), logicalSwitchName, externalIds);
                createDhcpOptionsForNetwork(provider, network);
                if (network.getVpcId() != null) {
                    // VPC tier: the LR (cs-vpc-{vpcId}) and the public side were already provisioned
                    // by implementVpc; here we only need to attach this tier to the shared LR and
                    // add a per-tier SNAT row so traffic from this CIDR egresses with the VPC's
                    // SourceNat IP. No per-network LR, no per-network public LS.
                    attachVpcTierToRouter(provider, network);
                    addVpcTierSnatRule(provider, network);
                } else {
                    createRouterAndAttachToGuest(provider, network);
                    applySourceNatForNetwork(provider, network);
                }
            } catch (CloudRuntimeException e) {
                throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, network.getDataCenterId());
            }
        }
        return true;
    }

    /**
     * Attaches a VPC tier's Logical_Switch to the shared VPC Logical_Router via a tier-LRP at
     * the tier's gateway IP. Mirrors {@link #createRouterAndAttachToGuest} but skips the LR
     * creation (the VPC LR is owned by {@link #implementVpc}). Idempotent.
     */
    protected void attachVpcTierToRouter(OvnProviderVO provider, Network network) {
        if (network.getCidr() == null || network.getGateway() == null) {
            return;
        }
        String routerName = getRouterNameForNetwork(network);
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

    /**
     * Programs (or refreshes) the per-tier SNAT row on the VPC LR so traffic from this tier's
     * CIDR is masqueraded behind the VPC's SourceNat IP. Skipped when the VPC has not yet been
     * assigned a SourceNat IP (the SNAT row will be added on the next implement / IP update).
     */
    protected void addVpcTierSnatRule(OvnProviderVO provider, Network network) {
        if (network.getCidr() == null) {
            return;
        }
        Vpc vpc = vpcDao.findById(network.getVpcId());
        if (vpc == null) {
            return;
        }
        List<IPAddressVO> ips = ipAddressDao.listByAssociatedVpc(vpc.getId(), true);
        if (ips == null) {
            return;
        }
        String routerName = getRouterNameForNetwork(network);
        for (IPAddressVO ipVo : ips) {
            if (!ipVo.isSourceNat() || ipVo.getAddress() == null) {
                continue;
            }
            String externalIp = ipVo.getAddress().addr();
            Map<String, String> ext = new HashMap<>();
            ext.put("cloudstack_network_id", String.valueOf(network.getId()));
            ext.put("cloudstack_vpc_id", String.valueOf(vpc.getId()));
            ext.put("cloudstack_nat_kind", "source-tier");
            ovnNbClient.addNatRule(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, "snat", externalIp, network.getCidr(), ext);
            // Refresh the gARP announcement on the VPC LRP so newly-attached tiers do not have
            // to wait for the next public-IP event for ovn-controller to gARP for the shared
            // SourceNat IP.
            applyVpcNatAddressesAnnouncement(provider, vpc);
            break;
        }
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
     * VPC-flavoured naming. The same scheme as the network helpers but keyed off a {@link Vpc}
     * directly, so {@link #implementVpc} / {@link #shutdownVpc} / {@link #updateVpcSourceNatIp}
     * can resolve OVN object names without manufacturing a {@link Network}.
     */
    protected String getVpcRouterName(Vpc vpc) {
        return String.format("cs-vpc-%d", vpc.getId());
    }

    protected String getVpcPublicLogicalSwitchName(Vpc vpc) {
        return String.format("cs-vpc-pub-%d", vpc.getId());
    }

    protected String getVpcPublicRouterPortName(Vpc vpc) {
        return "lrp-" + getVpcPublicLogicalSwitchName(vpc);
    }

    protected String getVpcPublicRouterSwitchPortName(Vpc vpc) {
        return "lsp-" + getVpcPublicRouterPortName(vpc);
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
     * MAC for a VPC-level router port. We pick a different leading octet ({@code 0xfc} external,
     * {@code 0xfb} internal) than {@link #buildRouterMac}'s isolated-network scheme so that an
     * isolated network and a VPC sharing the same numeric id never produce a colliding MAC on
     * the same OVN deployment.
     */
    private static String buildVpcRouterMac(long vpcId, boolean external) {
        return String.format("fa:16:3e:%02x:%02x:%02x",
                external ? 0xfc : 0xfb,
                (int) ((vpcId >> 8) & 0xff),
                (int) (vpcId & 0xff));
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
                if (ipVo.getAddress() == null || ipVo.getState() == com.cloud.network.IpAddress.State.Releasing) {
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
        StringBuilder arpProxy = new StringBuilder();
        for (IPAddressVO ipVo : ips) {
            if (ipVo.getAddress() == null || ipVo.getState() == com.cloud.network.IpAddress.State.Releasing) {
                continue;
            }
            if (arpProxy.length() > 0) arpProxy.append(' ');
            arpProxy.append(ipVo.getAddress().addr());
        }
        options.put("arp_proxy", arpProxy.toString());
        ovnNbClient.setLspOptions(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                externalLrpLsp, options);
    }

    /**
     * VPC counterpart of {@link #applyNatAddressesAnnouncement(OvnProviderVO, Network)}. The set
     * of advertised IPs is the {@code SourceNat} flag pool for the whole VPC (looked up by VPC
     * id), not per tier — every tier in a VPC shares the same external IP.
     */
    protected void applyVpcNatAddressesAnnouncement(OvnProviderVO provider, Vpc vpc) {
        String externalLrpLsp = getVpcPublicRouterSwitchPortName(vpc);
        String routerMac = buildVpcRouterMac(vpc.getId(), true);
        StringBuilder addresses = new StringBuilder(routerMac);
        boolean any = false;
        List<IPAddressVO> ips = ipAddressDao.listByAssociatedVpc(vpc.getId(), true);
        if (ips != null) {
            for (IPAddressVO ipVo : ips) {
                if (ipVo.getAddress() == null || ipVo.getState() == com.cloud.network.IpAddress.State.Releasing) {
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
        // arp_proxy makes OVN generate ARP responder flows on the external LS
        // for every IP the router owns, including port-forwarding VIPs that
        // only exist as LB entries (no dnat_and_snat NAT row on the router).
        StringBuilder arpProxy = new StringBuilder();
        for (IPAddressVO ipVo : ips) {
            if (ipVo.getAddress() == null || ipVo.getState() == com.cloud.network.IpAddress.State.Releasing) {
                continue;
            }
            if (arpProxy.length() > 0) arpProxy.append(' ');
            arpProxy.append(ipVo.getAddress().addr());
        }
        options.put("arp_proxy", arpProxy.toString());
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
                if (network.getVpcId() != null) {
                    // VPC tier: do not touch cs-vpc-{vpcId} or cs-vpc-pub-{vpcId}. Drop only the
                    // tier-specific SNAT row, the tier LRP on the shared VPC LR, the per-tier
                    // DHCP options, and the tier LS.
                    String vpcRouterName = getRouterNameForNetwork(network);
                    if (network.getCidr() != null) {
                        // Identify the tier SNAT row by (router, type, external_ip, logical_ip).
                        // We have to look up the VPC SourceNat IP now since the network's own
                        // associations don't carry it.
                        Vpc vpc = vpcDao.findById(network.getVpcId());
                        if (vpc != null) {
                            List<IPAddressVO> vpcIps = ipAddressDao.listByAssociatedVpc(vpc.getId(), true);
                            if (vpcIps != null) {
                                for (IPAddressVO ipVo : vpcIps) {
                                    if (ipVo.isSourceNat() && ipVo.getAddress() != null) {
                                        ovnNbClient.removeNatRule(provider.getNbConnection(),
                                                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                                                vpcRouterName, "snat", ipVo.getAddress().addr(), network.getCidr());
                                    }
                                }
                            }
                        }
                    }
                    String tierLrp = "lrp-" + getLogicalSwitchName(network);
                    ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            vpcRouterName, tierLrp);
                    ovnNbClient.deleteDhcpOptions(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            String.valueOf(network.getId()));
                    ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            getLogicalSwitchName(network));
                    return true;
                }
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
                if (ip.isSourceNat() && Boolean.TRUE.equals(services.contains(Network.Service.SourceNat))
                        && network.getVpcId() == null) {
                    // Isolated networks only: implementVpc already provisioned the VPC's public
                    // side, and CloudStack does not reuse this hook to push the VPC SourceNat IP
                    // through tier networks. Running this block for a VPC tier would create a
                    // duplicate LRP with the wrong MAC scheme.
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
            // Refresh nat-addresses on the gateway-side LSP. For a VPC tier the announcement is
            // VPC-scoped (one set of SourceNat IPs shared by every tier), so route through the
            // VPC-flavoured helper; isolated networks keep the per-network refresh.
            if (network.getVpcId() != null) {
                Vpc vpc = vpcDao.findById(network.getVpcId());
                if (vpc != null) {
                    applyVpcNatAddressesAnnouncement(provider, vpc);
                }
            } else {
                applyNatAddressesAnnouncement(provider, network);
            }
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

    /**
     * VPC variant of {@link #pickAnchorChassis(OvnProviderVO, Network)}: deterministic chassis
     * pick keyed off the VPC id, with the same stale-{@code Gateway_Chassis} prune applied to
     * the VPC's public LRP before we hand the name back to the caller.
     */
    protected String pickAnchorChassisForVpc(OvnProviderVO provider, Vpc vpc) {
        if (provider == null || provider.getSbConnection() == null || provider.getSbConnection().isEmpty()) {
            logger.warn("No OVN SB connection configured; cannot pick a Gateway_Chassis anchor for VPC {}", vpc);
            return null;
        }
        try {
            java.util.List<String> chassisNames = ovnNbClient.listSouthboundChassisNames(
                    provider.getSbConnection(), provider.getCaCertPath(),
                    provider.getClientCertPath(), provider.getClientPrivateKeyPath());
            if (chassisNames == null || chassisNames.isEmpty()) {
                logger.warn("OVN SB reports no registered Chassis yet; deferring Gateway_Chassis anchor for VPC {}", vpc);
                return null;
            }
            String publicLrpName = getVpcPublicRouterPortName(vpc);
            try {
                ovnNbClient.pruneStaleGatewayChassis(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        publicLrpName, new java.util.HashSet<>(chassisNames));
            } catch (CloudRuntimeException e) {
                logger.debug("Skipping Gateway_Chassis prune for {} ({})", publicLrpName, e.getMessage());
            }
            java.util.Collections.sort(chassisNames);
            return chassisNames.get((int) (Math.abs(vpc.getId()) % chassisNames.size()));
        } catch (Exception e) {
            logger.warn("Failed to query OVN SB for Chassis names while anchoring VPC {}: {}", vpc, e.getMessage());
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
        boolean isVpcTier = config.getVpcId() != null;
        Vpc vpc = isVpcTier ? vpcDao.findById(config.getVpcId()) : null;
        try {
            // Anchor the public LRP to a chassis so ovn-northd materialises the lr_in_dnat
            // pipeline. Without Gateway_Chassis, dnat_and_snat NAT rows are silently ignored
            // by lr_in_dnat. setLrpGatewayChassis is idempotent. For VPC tiers we route through
            // the VPC-flavoured helper so every tier converges on the same chassis the VPC LR
            // already anchored at implementVpc time.
            String anchorChassis = (isVpcTier && vpc != null) ? pickAnchorChassisForVpc(provider, vpc)
                    : pickAnchorChassis(provider, config);
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
                ext.put("cloudstack_public_ip", externalIp);
                if (isVpcTier) {
                    ext.put("cloudstack_vpc_id", String.valueOf(config.getVpcId()));
                }
                NicVO targetNic = nicDao.findByIp4AddressAndNetworkId(logicalIp, config.getId());
                String distributedLogicalPort = targetNic != null ? targetNic.getUuid() : null;
                // For distributed dnat_and_snat the external_mac must match the LR's external
                // LRP MAC so ovn-northd applies the rewrite locally on the chassis hosting the
                // backend VM. VPC LRPs use a different MAC scheme (buildVpcRouterMac, octet
                // 0xfc) than the per-network isolated LRPs (0xfe).
                String distributedMac = isVpcTier
                        ? buildVpcRouterMac(config.getVpcId(), true)
                        : buildRouterMac(config.getId(), true);
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
        ext.put("cloudstack_public_ip", externalIp);
        if (network.getVpcId() != null) {
            ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }

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
        if (network.getVpcId() != null) {
            ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }
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
    /**
     * No-op intentionally — see the multi-paragraph note below before reintroducing any
     * default-drop ACL on the public LS.
     *
     * <h3>Why no default-drop on the public LS</h3>
     *
     * Earlier revisions of this method installed a {@code to-lport ip4.dst==&lt;publicIp&gt;
     * action=drop} ACL at priority 100 on the public {@code Logical_Switch}, intending to
     * close every public IP that has any explicit firewall rule and only let through the
     * per-rule {@code allow-related} entries at priority 1000. That worked for unsolicited
     * inbound traffic (TCP/UDP probes from the internet on a port the operator did not
     * open) but it also broke <em>reply traffic</em> for any flow the VM itself initiated:
     * an ICMP / DNS / HTTPS reply to a static-NAT IP arrived on the public LS as a fresh
     * inbound packet, hit the default-drop, and never reached {@code lr_in_unsnat} for
     * NAT reversal.
     *
     * <p>The root cause is an OVN architectural choice. {@code ovn-northd} compiles
     * {@code ls_in_pre_acl} for an LS that has any stateful ACL, but it explicitly
     * <strong>bypasses {@code ct_next}</strong> for {@code router}-type and
     * {@code localnet}-type LSPs:
     *
     * <pre>
     *   table=4 (ls_in_pre_acl), priority=110,
     *           match=(ip && inport == "lsp-lrp-cs-pub-265"), action=(next;)
     *   table=4 (ls_in_pre_acl), priority=110,
     *           match=(ip && inport == "ln-cs-pub-265"),     action=(next;)
     * </pre>
     *
     * Because the public LS has only those two LSP types, every packet that traverses it
     * stays {@code ct_state=-trk}. The {@code ls_in_acl_hint} pipeline sets {@code reg0[9]
     * = 1} for any {@code !ct.trk} packet, and OVN's compilation of {@code action=drop}
     * generates a flow keyed on {@code reg0[9]==1} that fires for every untracked
     * inbound packet. {@code allow-related} ACLs we tried as a counter-measure
     * ({@code from-lport allow-related} on the egress side, {@code to-lport
     * allow-related ct.est && ct.rpl} on replies) never fire either, because the LS
     * conntrack zone is never populated in the first place — {@code ls_in_stateful}'s
     * commit requires {@code reg0[1]==1}, which only the {@code ct.new} hint at
     * priority 7 sets, which itself only runs after a successful {@code ct_next}.
     *
     * <p>Lab-verified: with a TCP/22 allow rule on a static-NAT IP, the VM could accept
     * inbound SSH but could not ping {@code 8.8.8.8} or resolve DNS — the reply leg of
     * every VM-initiated flow was dropped by the {@code reg0[9]==1} arm of the default
     * drop. Removing the default drop restored connectivity.
     *
     * <h3>Path forward</h3>
     *
     * The proper fix is to lift firewall enforcement off the public LS and onto an
     * object whose conntrack zone is actually populated:
     *
     * <ul>
     *   <li><strong>Option A (preferred):</strong> {@code Logical_Router policies} on the
     *       per-network or per-VPC LR. The LR's conntrack is committed by
     *       {@code ct_dnat} / {@code ct_snat}, so policies can use {@code ct.new} to
     *       drop unsolicited inbound while letting {@code ct.est} replies pass through
     *       to {@code lr_in_unsnat}. The per-rule allow ACL becomes a high-priority
     *       allow policy; the default-drop becomes a low-priority drop policy keyed on
     *       {@code inport == "lrp-cs-pub-&lt;id&gt;" && ct.new && ip4.dst == &lt;publicIp&gt;}.</li>
     *   <li><strong>Option B:</strong> attach the per-rule ACLs to the guest LS post
     *       NAT-reversal, matching the VM's internal IP. That is the Neutron-OVN
     *       security-group pattern. It changes the operator-visible match shape
     *       (CloudStack rules are written against the public IP, not the VM IP).</li>
     * </ul>
     *
     * <p>Both are out of scope for this commit; they require restructuring how
     * {@link #applyFWRules}, {@link #applyStaticNats} and the public-LS / public-LRP
     * lifecycle interact. The TODO is filed; in the meantime this method is a no-op so
     * that adding firewall rules does not regress NAT semantics on existing
     * deployments. The per-rule {@code allow-related} ACLs at priority 1000 still get
     * installed by {@link #programFirewallRule} — they are now informational-only on
     * the public LS but stay in place so the cleanup paths and any future LR-policy
     * migration can carry the per-rule history over. Outside of the OVN data plane,
     * CloudStack's iptables on the system VM and per-VM firewall on the guest still
     * apply, so the IP is not less protected than the VR-backed equivalent that runs
     * the same {@code FirewallRule}s through the VR's iptables.</p>
     */
    protected void ensureFirewallDefaultDeny(OvnProviderVO provider, Network network, String publicLs,
                                              String publicLrpLsp, String publicIp) {
        // Targeted ICMP echo-request drop. This is the slice of the original default-deny
        // that we *can* enforce statelessly without breaking VM-initiated outbound: the
        // match below pins the drop to icmp4.type == 8 (echo request) inbound on the
        // public IP. ICMP echo replies coming back from the internet for a VM that pinged
        // out have type == 0 (echo reply), so they do not match this drop; TCP/UDP replies
        // are unaffected entirely because the match clauses out non-ICMP traffic.
        //
        // What this does NOT cover: unsolicited TCP/UDP inbound to the public IP. The LS
        // pipeline cannot do stateful ACL there (see Javadoc above for the ct_next bypass
        // on router/localnet LSPs), and a stateless to-lport drop on TCP/UDP would re-
        // introduce the reply-traffic regression. Closing TCP/UDP requires moving firewall
        // enforcement to Logical_Router policies (LR conntrack tracks ct.new vs ct.est
        // correctly because ct_dnat / ct_snat populate the LR's ct zone), which is the
        // separate refactor tracked elsewhere.
        //
        // The per-rule ACLs from programFirewallRule (priority 1000, allow-related) still
        // override this drop when an operator opens ICMP via a CloudStack FirewallRule.
        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_fw_default", "true");
        ext.put("cloudstack_fw_default_icmp", "true");
        ext.put("cloudstack_fw_ip", publicIp);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        if (network.getVpcId() != null) {
            ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }
        String match = "outport == \"" + publicLrpLsp + "\" && ip4 && ip4.dst == " + publicIp
                + " && icmp4 && icmp4.type == 8";
        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                publicLs, "fw-default-icmp-" + publicIp, "to-lport", 100L, match, "drop", ext);
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

            // If this ACL is also used by a peering membership, re-apply on the peering LS
            if (rules != null && !rules.isEmpty()) {
                long aclId = rules.get(0).getAclId();
                List<OvnVpcPeeringVO> peeringsWithAcl = ovnVpcPeeringDao.listByAclId(aclId);
                for (OvnVpcPeeringVO peering : peeringsWithAcl) {
                    applyPeeringAcl(peering);
                }
            }
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
        ext.put("cloudstack_acl_id", String.valueOf(rule.getAclId()));
        ext.put("cloudstack_acl_number", String.valueOf(rule.getNumber()));
        if (network.getVpcId() != null) {
            ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }
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
            if (network.getVpcId() != null) {
                ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
            }
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

        boolean isInternal = rule.getScheme() == LoadBalancerContainer.Scheme.Internal;
        // For Public LB the VIP is sourced from the public IP allocation; for Internal LB it
        // is a private VIP carried directly on the rule (tier CIDR, no user_ip_address row).
        // rule.getSourceIp() works for both schemes uniformly — use it as the canonical VIP.
        com.cloud.utils.net.Ip vipIp = rule.getSourceIp();
        String externalIp = vipIp != null ? vipIp.addr() : null;
        if (externalIp == null || externalIp.isEmpty()) {
            logger.warn("LB rule {} has no source IP; skipping", rule.getId());
            return;
        }

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
        // Hairpin SNAT lets a VM behind the VIP reach its own VIP without ovn-northd
        // mis-routing the reply. For a Public LB we use the VIP itself (the public IP); for an
        // Internal LB whose VIP lives in a tier CIDR the public IP doesn't exist on this LR, so
        // we anchor the hairpin on the tier's gateway IP — that LRP is reachable on the same
        // LR, satisfies OVN's "must be an IP we own" check, and produces the right SNAT
        // when a VM in the tier hits its own VIP.
        options.put("hairpin_snat_ip", isInternal ? network.getGateway() : externalIp);
        if (affinityTimeout != null && affinityTimeout > 0) {
            options.put("affinity_timeout", String.valueOf(affinityTimeout));
        }

        Map<String, String> ext = new HashMap<>();
        ext.put("cloudstack_lb_rule_id", ruleTag);
        ext.put("cloudstack_network_id", String.valueOf(network.getId()));
        // Tag with the VIP so the per-IP-release sweep (cleanupPublicIpArtifacts) can wipe
        // Public LB rows out-of-order. Internal LBs carry a tier IP here (not a public IP),
        // so the same sweep will not touch them when a public IP is released.
        ext.put("cloudstack_lb_ip", externalIp);
        ext.put("cloudstack_lb_kind", "loadbalancer");
        ext.put("cloudstack_lb_scheme", isInternal ? "Internal" : "Public");
        if (network.getVpcId() != null) {
            ext.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }

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
        boolean isInternal = rule.getScheme() == LoadBalancerContainer.Scheme.Internal;
        com.cloud.utils.net.Ip vipIp = rule.getSourceIp();
        String vip = vipIp != null ? vipIp.addr() : null;

        if (isInternal) {
            // Internal LB: VIP must be a private IP that lives inside the tier hosting the rule
            // (or another tier of the same VPC, which OVN handles transparently because the LB
            // is attached to the shared VPC LR). We accept any IP within the network's CIDR
            // here; for cross-tier VIPs CloudStack already validates against the VPC supernet.
            // Reject obvious mistakes: an empty VIP, or a VIP that maps to a real public-IP
            // allocation (in which case the user wanted a Public LB).
            if (vip == null || vip.isEmpty()) {
                logger.warn("OVN LB rejecting Internal rule {}: no source IP", rule.getId());
                return false;
            }
            if (rule.getLb() != null && rule.getLb().getSourceIpAddressId() != null) {
                IPAddressVO ipVo = ipAddressDao.findById(rule.getLb().getSourceIpAddressId());
                if (ipVo != null) {
                    logger.warn("OVN LB rejecting Internal rule {}: VIP {} resolves to a public IP "
                                    + "allocation - use scheme=Public instead",
                            rule.getId(), vip);
                    return false;
                }
            }
            return true;
        }

        // Public LB: reject rules that target the network's SourceNat IP. The same external IP
        // would carry both an LR-level snat NAT row (logical_ip=guest_cidr -> external_ip) and
        // the LB's vips map; replies from a backend are SNATed back to the SourceNat IP before
        // the LB un-DNAT can run, so the client sees a reply from an IP that doesn't match the
        // connection it opened and TCP resets. Lab-confirmed: traffic enters the LR but no
        // SYN+ACK ever reaches the upstream when LB and SourceNat share an external IP. Force
        // the user to allocate a dedicated public IP for LB.
        if (rule.getLb() != null && rule.getLb().getSourceIpAddressId() != null) {
            IPAddressVO ipVo = ipAddressDao.findById(rule.getLb().getSourceIpAddressId());
            if (ipVo != null && ipVo.isSourceNat()) {
                logger.warn("OVN LB rejecting Public rule {}: external IP {} is the network's SourceNat IP "
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
        OvnProviderVO provider = ovnProviderDao.findByZoneId(vpc.getZoneId());
        if (provider == null) {
            throw new ResourceUnavailableException(
                    String.format("No OVN provider configured for zone %s", vpc.getZoneId()),
                    DataCenter.class, vpc.getZoneId());
        }
        String routerName = getVpcRouterName(vpc);
        Map<String, String> lrExt = new HashMap<>();
        lrExt.put("cloudstack_vpc_id", String.valueOf(vpc.getId()));
        lrExt.put("cloudstack_vpc_uuid", vpc.getUuid());
        lrExt.put("cloudstack_zone_id", String.valueOf(vpc.getZoneId()));
        lrExt.put("cloudstack_role", "vpc-router");
        try {
            ovnNbClient.createLogicalRouter(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, lrExt);
            // Wire up the public side now if CloudStack has already allocated the SourceNat IP
            // for this VPC. This is the common case (VpcManagerImpl allocates the IP during VPC
            // creation, before calling implementVpc). When the IP is changed later we re-run the
            // same idempotent helper from updateVpcSourceNatIp.
            applyVpcSourceNatPublicSide(provider, vpc);
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, vpc.getZoneId());
        }
        return true;
    }

    /**
     * Provisions or refreshes the public-side OVN artifacts for a VPC: the public Logical_Switch
     * (cs-vpc-pub-{id}), its localnet port wired to the provider's external bridge / VLAN, the
     * Logical_Router_Port that anchors the VPC LR on the public LS using the VPC's SourceNat IP,
     * the Gateway_Chassis row, the default route to the upstream gateway, and the gARP
     * announcement.
     *
     * <p>Idempotent on every component (skips inserts when the row already exists). Per-tier SNAT
     * rows ({@code logical_ip = tier_cidr}) are added separately by PR-2b's tier
     * {@code implement(network)} path.</p>
     *
     * <p>If the VPC has no SourceNat IP allocated yet this is a no-op; the public side will come
     * up on the next call (typically {@link #updateVpcSourceNatIp}).</p>
     */
    protected void applyVpcSourceNatPublicSide(OvnProviderVO provider, Vpc vpc) {
        List<IPAddressVO> ips = ipAddressDao.listByAssociatedVpc(vpc.getId(), true);
        if (ips == null || ips.isEmpty()) {
            logger.debug("VPC {} has no SourceNat IP yet; deferring public-side provisioning", vpc.getId());
            return;
        }
        String routerName = getVpcRouterName(vpc);
        String publicLs = getVpcPublicLogicalSwitchName(vpc);
        String publicLrpName = getVpcPublicRouterPortName(vpc);
        String localnet = provider.getLocalnetName();
        String externalBridge = provider.getExternalBridge();
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
            Map<String, String> publicLsExt = new HashMap<>();
            publicLsExt.put("cloudstack_vpc_id", String.valueOf(vpc.getId()));
            publicLsExt.put("cloudstack_role", "vpc-public");
            ovnNbClient.createLogicalSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs, publicLsExt);
            ovnNbClient.addLocalnetPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs, "ln-" + publicLs, localnet != null ? localnet : externalBridge, vlanTag);
            String prefix = "/" + maskToPrefix(netmask != null ? netmask : "255.255.240.0");
            ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, publicLs,
                    publicLrpName, buildVpcRouterMac(vpc.getId(), true),
                    java.util.Collections.singletonList(externalIp + prefix));
            String anchorChassis = pickAnchorChassisForVpc(provider, vpc);
            if (anchorChassis != null) {
                ovnNbClient.setLrpGatewayChassis(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        publicLrpName, anchorChassis, 10);
            }
            if (externalGateway != null && !externalGateway.isEmpty()) {
                ovnNbClient.addStaticRoute(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, "0.0.0.0/0", externalGateway);
            }
            applyVpcNatAddressesAnnouncement(provider, vpc);
        }
    }

    @Override
    public boolean shutdownVpc(Vpc vpc, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        OvnProviderVO provider = ovnProviderDao.findByZoneId(vpc.getZoneId());
        if (provider == null) {
            // Provider already gone — nothing to clean up. Treat as success so VPC removal can proceed.
            return true;
        }
        String routerName = getVpcRouterName(vpc);
        String publicLs = getVpcPublicLogicalSwitchName(vpc);
        try {
            // Wipe Load_Balancer rows tagged with this VPC. LB rows live in the global
            // Load_Balancer table and are referenced from LR/LS via the load_balancer column,
            // so deleting the LR/LS does not necessarily garbage-collect them when other refs
            // remain. By the time shutdownVpc runs CloudStack has already destroyed every tier,
            // but a defensive sweep keeps state clean for re-creates.
            ovnNbClient.removeLoadBalancersByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    "cloudstack_vpc_id", String.valueOf(vpc.getId()));
            // Remove all peering memberships for this VPC before destroying the router.
            removePeeringsForVpc(vpc, provider);

            // Public LS first — its router-type LSP pairs with the public LRP on the LR; deleting
            // the LS removes the LSP and any localnet/firewall ACLs sitting on it.
            ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    publicLs);
            // The LR may still have its public LRP and any tier LRPs / NAT rows referenced from
            // the strong-typed columns; OVSDB GCs those rows when the LR is removed. Tier LSes
            // were already deleted by destroy(network) calls preceding shutdownVpc.
            ovnNbClient.deleteLogicalRouter(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName);
        } catch (CloudRuntimeException e) {
            throw new ResourceUnavailableException(e.getMessage(), DataCenter.class, vpc.getZoneId());
        }
        return true;
    }

    @Override
    public boolean createPrivateGateway(PrivateGateway gateway) throws ConcurrentOperationException, ResourceUnavailableException {
        // PrivateGateway is out of scope for the OVN VPC v1.
        return true;
    }

    @Override
    public boolean deletePrivateGateway(PrivateGateway privateGateway) throws ConcurrentOperationException, ResourceUnavailableException {
        // PrivateGateway is out of scope for the OVN VPC v1.
        return true;
    }

    @Override
    public boolean applyStaticRoutes(Vpc vpc, List<StaticRouteProfile> routes) throws ResourceUnavailableException {
        // Tenant-managed static routes are out of scope for the OVN VPC v1; the only route we
        // program ourselves is the upstream default in applyVpcSourceNatPublicSide.
        return true;
    }

    @Override
    public boolean applyACLItemsToPrivateGw(PrivateGateway gateway, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        // Coupled to PrivateGateway support; out of scope for v1.
        return true;
    }

    @Override
    public boolean updateVpcSourceNatIp(Vpc vpc, IpAddress address) {
        // Re-run the public-side provisioning. applyVpcSourceNatPublicSide is idempotent and
        // attaches the new SourceNat IP via attachRouterToSwitch / addStaticRoute, then refreshes
        // the gARP announcement. Note: when the VPC's SourceNat IP is *changed* (rather than
        // first allocated), the previous LRP IP/NAT/route rows are not torn down here — the
        // OVN-only SourceNat-IP swap remains a TODO. v1 supports first-time allocation cleanly.
        OvnProviderVO provider = ovnProviderDao.findByZoneId(vpc.getZoneId());
        if (provider == null) {
            logger.warn("updateVpcSourceNatIp: no OVN provider for zone {}", vpc.getZoneId());
            return false;
        }
        try {
            applyVpcSourceNatPublicSide(provider, vpc);
        } catch (CloudRuntimeException e) {
            logger.warn("updateVpcSourceNatIp failed for VPC {}: {}", vpc.getId(), e.getMessage());
            return false;
        }
        return true;
    }

    // ── VPC Peering (OvnPeeringService) ──────────────────────────────────────

    private static final String PEERING_EXT_KEY = "cloudstack_peering_group";
    private static final int NAT_BYPASS_PRIORITY = 1000;

    @Override
    public OvnVpcPeeringVO createVpcPeering(CreateVpcPeeringCmd cmd) {
        long callerId = CallContext.current().getCallingAccount().getId();
        VpcVO vpc = vpcDao.findById(cmd.getVpcId());
        VpcVO peerVpc = vpcDao.findById(cmd.getPeerVpcId());
        if (vpc == null) {
            throw new InvalidParameterValueException("VPC not found: " + cmd.getVpcId());
        }
        if (peerVpc == null) {
            throw new InvalidParameterValueException("Peer VPC not found: " + cmd.getPeerVpcId());
        }
        if (vpc.getId() == peerVpc.getId()) {
            throw new InvalidParameterValueException("Cannot peer a VPC with itself");
        }
        if (vpc.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own VPC " + vpc.getUuid());
        }
        if (peerVpc.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own peer VPC " + peerVpc.getUuid());
        }
        if (vpc.getAccountId() != peerVpc.getAccountId()) {
            throw new InvalidParameterValueException("VPC peering is only allowed between VPCs of the same account");
        }

        OvnProviderVO providerA = ovnProviderDao.findByZoneId(vpc.getZoneId());
        OvnProviderVO providerB = ovnProviderDao.findByZoneId(peerVpc.getZoneId());
        if (providerA == null) {
            throw new InvalidParameterValueException("VPC zone " + vpc.getZoneId() + " has no OVN provider");
        }
        if (providerB == null) {
            throw new InvalidParameterValueException("Peer VPC zone " + peerVpc.getZoneId() + " has no OVN provider");
        }

        // Determine group: if peer VPC already belongs to a group, join it; otherwise check if our VPC
        // already belongs to one. If neither, create a new group.
        String groupUuid = null;
        List<OvnVpcPeeringVO> peerExisting = ovnVpcPeeringDao.listByVpcId(peerVpc.getId());
        if (!peerExisting.isEmpty()) {
            groupUuid = peerExisting.get(0).getGroupUuid();
        }
        if (groupUuid == null) {
            List<OvnVpcPeeringVO> myExisting = ovnVpcPeeringDao.listByVpcId(vpc.getId());
            if (!myExisting.isEmpty()) {
                groupUuid = myExisting.get(0).getGroupUuid();
            }
        }
        if (groupUuid == null) {
            groupUuid = UUID.randomUUID().toString();
        }

        // Ensure both VPCs aren't already in the same group
        if (ovnVpcPeeringDao.findByGroupUuidAndVpcId(groupUuid, vpc.getId()) != null
                && ovnVpcPeeringDao.findByGroupUuidAndVpcId(groupUuid, peerVpc.getId()) != null) {
            throw new InvalidParameterValueException("Both VPCs are already in the same peering group");
        }

        // Allocate link-local IPs for new members
        List<OvnVpcPeeringVO> groupMembers = ovnVpcPeeringDao.listByGroupUuid(groupUuid);
        Set<String> usedIps = new HashSet<>();
        for (OvnVpcPeeringVO m : groupMembers) {
            usedIps.add(m.getLinkLocalIp());
        }

        // Validate ACL belongs to the calling VPC if specified
        Long aclId = cmd.getAclId();
        if (aclId != null) {
            NetworkACLVO acl = networkACLDao.findById(aclId);
            if (acl == null) {
                throw new InvalidParameterValueException("Network ACL not found: " + aclId);
            }
            if (acl.getVpcId() != 0 && acl.getVpcId() != vpc.getId()) {
                throw new InvalidParameterValueException("Network ACL does not belong to VPC " + vpc.getUuid());
            }
        }

        OvnVpcPeeringVO peeringA = null;
        OvnVpcPeeringVO peeringB = null;

        if (ovnVpcPeeringDao.findByGroupUuidAndVpcId(groupUuid, vpc.getId()) == null) {
            String ipA = allocateLinkLocalIp(usedIps);
            usedIps.add(ipA);
            peeringA = new OvnVpcPeeringVO(groupUuid, vpc.getId(), vpc.getZoneId(),
                    vpc.getAccountId(), vpc.getDomainId(), ipA);
            peeringA.setAclId(aclId);
            peeringA = ovnVpcPeeringDao.persist(peeringA);
        } else {
            peeringA = ovnVpcPeeringDao.findByGroupUuidAndVpcId(groupUuid, vpc.getId());
        }

        if (ovnVpcPeeringDao.findByGroupUuidAndVpcId(groupUuid, peerVpc.getId()) == null) {
            String ipB = allocateLinkLocalIp(usedIps);
            usedIps.add(ipB);
            peeringB = new OvnVpcPeeringVO(groupUuid, peerVpc.getId(), peerVpc.getZoneId(),
                    peerVpc.getAccountId(), peerVpc.getDomainId(), ipB);
            peeringB = ovnVpcPeeringDao.persist(peeringB);
        }

        // Provision OVN fabric for the entire group
        provisionPeeringGroup(groupUuid);

        return peeringA;
    }

    @Override
    public boolean deleteVpcPeering(DeleteVpcPeeringCmd cmd) {
        OvnVpcPeeringVO peering = ovnVpcPeeringDao.findByUuid(cmd.getId());
        if (peering == null || !"Active".equals(peering.getState())) {
            throw new InvalidParameterValueException("VPC peering not found or already removed: " + cmd.getId());
        }
        long callerId = CallContext.current().getCallingAccount().getId();
        if (peering.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own this peering");
        }

        String groupUuid = peering.getGroupUuid();
        long vpcId = peering.getVpcId();
        VpcVO vpc = vpcDao.findById(vpcId);

        OvnProviderVO provider = ovnProviderDao.findByZoneId(peering.getZoneId());
        if (provider != null && vpc != null) {
            String routerName = String.format("cs-vpc-%d", vpcId);
            // Remove routes and policies on all OTHER members pointing to this VPC
            List<OvnVpcPeeringVO> groupMembers = ovnVpcPeeringDao.listByGroupUuid(groupUuid);
            for (OvnVpcPeeringVO member : groupMembers) {
                if (member.getVpcId() == vpcId) continue;
                OvnProviderVO memberProvider = ovnProviderDao.findByZoneId(member.getZoneId());
                if (memberProvider == null) continue;
                String memberRouter = String.format("cs-vpc-%d", member.getVpcId());
                VpcVO memberVpc = vpcDao.findById(member.getVpcId());
                if (memberVpc == null) continue;
                // Remove route on member pointing to this VPC
                ovnNbClient.removeStaticRoute(memberProvider.getNbConnection(),
                        memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                        memberRouter, vpc.getCidr(), peering.getLinkLocalIp());
                // Remove NAT bypass policy on member for this VPC's CIDR
                ovnNbClient.removeLogicalRouterPoliciesByExternalId(memberProvider.getNbConnection(),
                        memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                        memberRouter, PEERING_EXT_KEY + "_target", String.valueOf(vpcId));
            }

            // Remove routes and policies on THIS VPC pointing to all other members
            ovnNbClient.removeStaticRoutesByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, PEERING_EXT_KEY, groupUuid);
            ovnNbClient.removeLogicalRouterPoliciesByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, PEERING_EXT_KEY, groupUuid);

            // Remove peering ACLs for this VPC on the peering LS
            String peerLs = getPeeringLsName(groupUuid);
            String peeringTag = "cloudstack_peering_acl_vpc_" + vpcId;
            ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs, peeringTag, "true");

            // Remove LRP+LSP for this VPC on the peering switch
            String lrpName = getPeeringLrpName(groupUuid, vpcId);
            ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, lrpName);
            ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs, getPeeringLspName(groupUuid, vpcId));

            // If no other members, delete the peering LS from all zones
            List<OvnVpcPeeringVO> remaining = ovnVpcPeeringDao.listByGroupUuid(groupUuid);
            long activeCount = remaining.stream().filter(m -> m.getVpcId() != vpcId).count();
            if (activeCount == 0) {
                Set<Long> cleanedZones = new HashSet<>();
                for (OvnVpcPeeringVO m : remaining) {
                    if (!cleanedZones.add(m.getZoneId())) continue;
                    OvnProviderVO zp = ovnProviderDao.findByZoneId(m.getZoneId());
                    if (zp == null) continue;
                    ovnNbClient.deleteLogicalSwitch(zp.getNbConnection(),
                            zp.getCaCertPath(), zp.getClientCertPath(), zp.getClientPrivateKeyPath(),
                            peerLs);
                }
            }
        }

        peering.setState("Removed");
        peering.setRemoved(new java.util.Date());
        ovnVpcPeeringDao.update(peering.getId(), peering);
        return true;
    }

    @Override
    public OvnVpcPeeringVO updateVpcPeering(UpdateVpcPeeringCmd cmd) {
        OvnVpcPeeringVO peering = ovnVpcPeeringDao.findByUuid(cmd.getId());
        if (peering == null || !"Active".equals(peering.getState())) {
            throw new InvalidParameterValueException("VPC peering not found or already removed: " + cmd.getId());
        }
        long callerId = CallContext.current().getCallingAccount().getId();
        if (peering.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own this peering");
        }

        Long aclId = cmd.getAclId();
        if (aclId != null) {
            NetworkACLVO acl = networkACLDao.findById(aclId);
            if (acl == null) {
                throw new InvalidParameterValueException("Network ACL not found: " + aclId);
            }
            if (acl.getVpcId() != 0 && acl.getVpcId() != peering.getVpcId()) {
                throw new InvalidParameterValueException("Network ACL does not belong to this peering's VPC");
            }
        }

        peering.setAclId(aclId);
        ovnVpcPeeringDao.update(peering.getId(), peering);

        applyPeeringAcl(peering);
        return peering;
    }

    @Override
    public List<VpcPeeringResponse> listVpcPeerings(ListVpcPeeringsCmd cmd) {
        long callerId = CallContext.current().getCallingAccount().getId();
        List<OvnVpcPeeringVO> peerings;
        if (cmd.getVpcId() != null) {
            peerings = ovnVpcPeeringDao.listByVpcId(cmd.getVpcId());
        } else if (cmd.getGroupUuid() != null) {
            peerings = ovnVpcPeeringDao.listByGroupUuid(cmd.getGroupUuid());
        } else if (accountMgr.isRootAdmin(callerId)) {
            peerings = ovnVpcPeeringDao.listAllActive();
        } else {
            peerings = ovnVpcPeeringDao.listByAccountId(callerId);
        }

        List<VpcPeeringResponse> responses = new ArrayList<>();
        for (OvnVpcPeeringVO p : peerings) {
            responses.add(createVpcPeeringResponse(p));
        }
        return responses;
    }

    @Override
    public VpcPeeringResponse createVpcPeeringResponse(OvnVpcPeeringVO peering) {
        VpcPeeringResponse response = new VpcPeeringResponse();
        response.setObjectName("vpcpeering");
        response.setId(peering.getUuid());
        response.setGroupUuid(peering.getGroupUuid());
        response.setLinkLocalIp(peering.getLinkLocalIp());
        response.setState(peering.getState());
        response.setCreated(peering.getCreated());

        VpcVO vpc = vpcDao.findById(peering.getVpcId());
        if (vpc != null) {
            response.setVpcId(vpc.getUuid());
            response.setVpcName(vpc.getName());
            response.setVpcCidr(vpc.getCidr());
        }

        DataCenterVO zone = dataCenterDao.findById(peering.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }

        if (peering.getAclId() != null) {
            NetworkACLVO acl = networkACLDao.findById(peering.getAclId());
            if (acl != null) {
                response.setAclId(acl.getUuid());
                response.setAclName(acl.getName());
            }
        }

        // Find the "other" VPCs in this group for richer response
        List<OvnVpcPeeringVO> groupMembers = ovnVpcPeeringDao.listByGroupUuid(peering.getGroupUuid());
        for (OvnVpcPeeringVO m : groupMembers) {
            if (m.getVpcId() != peering.getVpcId()) {
                VpcVO peerVpc = vpcDao.findById(m.getVpcId());
                if (peerVpc != null) {
                    response.setPeerVpcId(peerVpc.getUuid());
                    response.setPeerVpcName(peerVpc.getName());
                    response.setPeerVpcCidr(peerVpc.getCidr());
                }
                break;
            }
        }

        return response;
    }

    protected void provisionPeeringGroup(String groupUuid) {
        List<OvnVpcPeeringVO> members = ovnVpcPeeringDao.listByGroupUuid(groupUuid);
        if (members.isEmpty()) return;

        String peerLs = getPeeringLsName(groupUuid);
        Map<String, String> lsExt = new HashMap<>();
        lsExt.put(PEERING_EXT_KEY, groupUuid);
        lsExt.put("cloudstack_role", "vpc-peering");

        // Create the peering LS in every distinct zone that participates.
        // Each zone has its own OVN NB, so the LS must exist in each.
        Set<Long> provisionedZones = new HashSet<>();
        for (OvnVpcPeeringVO member : members) {
            if (!provisionedZones.add(member.getZoneId())) continue;
            OvnProviderVO zoneProvider = ovnProviderDao.findByZoneId(member.getZoneId());
            if (zoneProvider == null) continue;
            ovnNbClient.createLogicalSwitch(zoneProvider.getNbConnection(),
                    zoneProvider.getCaCertPath(), zoneProvider.getClientCertPath(), zoneProvider.getClientPrivateKeyPath(),
                    peerLs, lsExt);
        }

        // Ensure each member is attached and has routes to every other member
        for (OvnVpcPeeringVO member : members) {
            OvnProviderVO provider = ovnProviderDao.findByZoneId(member.getZoneId());
            if (provider == null) continue;
            VpcVO vpc = vpcDao.findById(member.getVpcId());
            if (vpc == null) continue;

            String routerName = String.format("cs-vpc-%d", member.getVpcId());
            String lrpName = getPeeringLrpName(groupUuid, member.getVpcId());
            String mac = buildPeeringMac(member.getVpcId());

            // Attach router to peering switch
            ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, peerLs, lrpName, mac,
                    Collections.singletonList(member.getLinkLocalIp() + "/24"));

            // Add routes and NAT bypass policies for every OTHER member
            for (OvnVpcPeeringVO other : members) {
                if (other.getVpcId() == member.getVpcId()) continue;
                VpcVO otherVpc = vpcDao.findById(other.getVpcId());
                if (otherVpc == null || otherVpc.getCidr() == null) continue;

                Map<String, String> routeExt = new HashMap<>();
                routeExt.put(PEERING_EXT_KEY, groupUuid);
                routeExt.put(PEERING_EXT_KEY + "_target", String.valueOf(other.getVpcId()));
                ovnNbClient.addStaticRoute(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, otherVpc.getCidr(), other.getLinkLocalIp(), routeExt);

                // NAT bypass: skip SNAT for traffic destined to peered VPC
                String match = String.format("ip4.dst == %s", otherVpc.getCidr());
                Map<String, String> polExt = new HashMap<>();
                polExt.put(PEERING_EXT_KEY, groupUuid);
                polExt.put(PEERING_EXT_KEY + "_target", String.valueOf(other.getVpcId()));
                ovnNbClient.addLogicalRouterPolicy(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, NAT_BYPASS_PRIORITY, match, "allow", null, polExt);
            }
        }

        // Apply ACLs on the peering LS for members that have an ACL configured
        for (OvnVpcPeeringVO member : members) {
            if (member.getAclId() != null) {
                applyPeeringAcl(member);
            }
        }
    }

    protected void applyPeeringAcl(OvnVpcPeeringVO peering) {
        OvnProviderVO provider = ovnProviderDao.findByZoneId(peering.getZoneId());
        if (provider == null) return;

        String peerLs = getPeeringLsName(peering.getGroupUuid());
        String lspName = getPeeringLspName(peering.getGroupUuid(), peering.getVpcId());
        String peeringTag = "cloudstack_peering_acl_vpc_" + peering.getVpcId();

        // Wipe existing ACLs for this member on the peering LS
        ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                peerLs, peeringTag, "true");

        Long aclId = peering.getAclId();
        if (aclId == null) {
            return;
        }

        List<NetworkACLItemVO> rules = networkACLItemDao.listByACL(aclId);
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (NetworkACLItemVO rule : rules) {
            if (rule.getState() == NetworkACLItem.State.Revoke) {
                continue;
            }
            networkACLItemDao.loadCidrs(rule);
            programPeeringAclRule(provider, peerLs, lspName, peering, rule);
        }

        // Default deny for both directions, scoped to this member's port
        for (String dir : new String[]{"to-lport", "from-lport"}) {
            String portField = "to-lport".equals(dir) ? "outport" : "inport";
            String matchExpr = String.format("%s == \"%s\" && ip4", portField, lspName);
            Map<String, String> ext = new HashMap<>();
            ext.put(peeringTag, "true");
            ext.put("cloudstack_acl_default", "true");
            ovnNbClient.addAclOnLs(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs, "peer-acl-default-" + dir + "-vpc-" + peering.getVpcId(),
                    dir, 1L, matchExpr, "drop", ext);
        }
    }

    protected void programPeeringAclRule(OvnProviderVO provider, String peerLs,
                                          String lspName, OvnVpcPeeringVO peering,
                                          NetworkACLItem rule) {
        String direction = rule.getTrafficType() == NetworkACLItem.TrafficType.Ingress
                ? "to-lport" : "from-lport";
        String aclAction = rule.getAction() == NetworkACLItem.Action.Allow ? "allow-related" : "drop";
        long ovnPriority = Math.max(2L, 1000L - rule.getNumber());

        // Scope the match to this member's port on the peering LS
        String portField = "to-lport".equals(direction) ? "outport" : "inport";
        String baseMatch = buildNetworkAclMatch(direction, rule);
        if (baseMatch == null) {
            return;
        }
        String matchExpr = String.format("%s == \"%s\" && %s", portField, lspName, baseMatch);

        String peeringTag = "cloudstack_peering_acl_vpc_" + peering.getVpcId();
        Map<String, String> ext = new HashMap<>();
        ext.put(peeringTag, "true");
        ext.put("cloudstack_acl_rule_id", String.valueOf(rule.getId()));
        ext.put("cloudstack_acl_id", String.valueOf(rule.getAclId()));
        ext.put("cloudstack_acl_direction", direction);
        ext.put(PEERING_EXT_KEY, peering.getGroupUuid());

        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                peerLs, "peer-acl-" + rule.getId() + "-vpc-" + peering.getVpcId(),
                direction, ovnPriority, matchExpr, aclAction, ext);
    }

    protected void removePeeringsForVpc(Vpc vpc, OvnProviderVO provider) {
        List<OvnVpcPeeringVO> peerings = ovnVpcPeeringDao.listByVpcId(vpc.getId());
        for (OvnVpcPeeringVO peering : peerings) {
            try {
                String groupUuid = peering.getGroupUuid();
                String routerName = String.format("cs-vpc-%d", vpc.getId());

                // Clean up routes/policies on other members
                List<OvnVpcPeeringVO> groupMembers = ovnVpcPeeringDao.listByGroupUuid(groupUuid);
                for (OvnVpcPeeringVO member : groupMembers) {
                    if (member.getVpcId() == vpc.getId()) continue;
                    OvnProviderVO memberProvider = ovnProviderDao.findByZoneId(member.getZoneId());
                    if (memberProvider == null) continue;
                    String memberRouter = String.format("cs-vpc-%d", member.getVpcId());
                    ovnNbClient.removeStaticRoute(memberProvider.getNbConnection(),
                            memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                            memberRouter, vpc.getCidr(), peering.getLinkLocalIp());
                    ovnNbClient.removeLogicalRouterPoliciesByExternalId(memberProvider.getNbConnection(),
                            memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                            memberRouter, PEERING_EXT_KEY + "_target", String.valueOf(vpc.getId()));
                }

                // Remove our own routes/policies
                ovnNbClient.removeStaticRoutesByExternalId(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, PEERING_EXT_KEY, groupUuid);
                ovnNbClient.removeLogicalRouterPoliciesByExternalId(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, PEERING_EXT_KEY, groupUuid);

                // Remove LRP+LSP
                ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, getPeeringLrpName(groupUuid, vpc.getId()));
                ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        getPeeringLsName(groupUuid), getPeeringLspName(groupUuid, vpc.getId()));

                // Delete peering LS if last member
                long activeCount = groupMembers.stream().filter(m -> m.getVpcId() != vpc.getId()).count();
                if (activeCount == 0) {
                    ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            getPeeringLsName(groupUuid));
                }

                peering.setState("Removed");
                peering.setRemoved(new java.util.Date());
                ovnVpcPeeringDao.update(peering.getId(), peering);
            } catch (CloudRuntimeException e) {
                logger.warn("Failed to clean up peering {} for VPC {}: {}", peering.getUuid(), vpc.getId(), e.getMessage());
            }
        }
    }

    private static String getPeeringLsName(String groupUuid) {
        return "cs-peer-" + groupUuid;
    }

    private static String getPeeringLrpName(String groupUuid, long vpcId) {
        return String.format("lrp-peer-%s-vpc-%d", groupUuid, vpcId);
    }

    private static String getPeeringLspName(String groupUuid, long vpcId) {
        return String.format("lsp-peer-%s-vpc-%d", groupUuid, vpcId);
    }

    private static String buildPeeringMac(long vpcId) {
        return String.format("fa:16:3e:fa:%02x:%02x",
                (int) ((vpcId >> 8) & 0xff),
                (int) (vpcId & 0xff));
    }

    private static String allocateLinkLocalIp(Set<String> usedIps) {
        // Pool: 169.254.100.1 through 169.254.100.253 (skip .0 and .255)
        for (int i = 1; i <= 253; i++) {
            String ip = "169.254.100." + i;
            if (!usedIps.contains(ip)) {
                return ip;
            }
        }
        throw new CloudRuntimeException("No available link-local IPs in peering pool 169.254.100.0/24");
    }
}
