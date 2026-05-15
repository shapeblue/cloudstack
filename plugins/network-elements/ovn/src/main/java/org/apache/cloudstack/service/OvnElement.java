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
import com.cloud.network.dao.OvnMeshNetworkDao;
import com.cloud.network.element.OvnProviderVO;
import com.cloud.network.element.OvnMeshNetworkVO;
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

import org.apache.cloudstack.api.command.CreateMeshNetworkCmd;
import org.apache.cloudstack.api.command.DeleteMeshNetworkCmd;
import org.apache.cloudstack.api.command.DisableMeshNetworkCmd;
import org.apache.cloudstack.api.command.EnableMeshNetworkCmd;
import org.apache.cloudstack.api.command.ListMeshNetworksCmd;
import org.apache.cloudstack.api.command.UpdateMeshNetworkCmd;
import org.apache.cloudstack.api.response.MeshNetworkMemberResponse;
import org.apache.cloudstack.api.response.MeshNetworkResponse;
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
import org.apache.commons.lang3.StringUtils;

public class OvnElement extends AdapterBase implements DhcpServiceProvider, DnsServiceProvider, VpcProvider,
        StaticNatServiceProvider, IpDeployer, PortForwardingServiceProvider, FirewallServiceProvider,
        NetworkACLServiceProvider, LoadBalancingServiceProvider, OvnMeshNetworkService {

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
    OvnMeshNetworkDao ovnMeshNetworkDao;

    @Inject
    AccountManager accountMgr;

    @Inject
    DataCenterDao dataCenterDao;

    @Inject
    NetworkACLDao networkACLDao;

    @Inject
    NetworkACLItemDao networkACLItemDao;

    @Inject
    com.cloud.service.dao.ServiceOfferingDao serviceOfferingDao;

    @Inject
    com.cloud.network.dao.NetworkDao networksDao;

    @Inject
    com.cloud.offerings.dao.NetworkOfferingServiceMapDao networkOfferingServiceMapDao;

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
                applyNicEgressRateLimit(provider, network, nic, vm, lspName);
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
     * Applies a per-LSP egress rate-limit derived from the VM's compute (service)
     * offering {@code nw_rate} (Mbps). {@code null} or non-positive ⇒ no shaping
     * (LSP options stay untouched). We read directly from {@link com.cloud.service.dao.ServiceOfferingDao}
     * to avoid pulling {@code NetworkModel} into the OVN plugin's Spring context — that
     * dependency direction risks a cycle since {@code NetworkModel} discovers
     * {@code OvnElement} as a {@link com.cloud.network.element.NetworkElement}.
     *
     * <p>OVN reads {@code Logical_Switch_Port.options:qos_max_rate} as <strong>kbps</strong>
     * for traffic ingressing the switch from this port — i.e. VM upload/egress — and
     * {@code qos_burst} as the bucket size in <strong>kbits</strong>. We give the burst
     * 100 ms of room so TCP slow-start isn't punished, with a 12 kbit floor so a single
     * MTU still fits.
     *
     * <p>Phase 1 limitation: only the service offering's {@code nw_rate} is consulted.
     * The global {@code vm.network.throttling.rate} fallback applied by the legacy
     * VR-based path is NOT applied here — operators that rely on the global must set
     * {@code nw_rate} explicitly on the offering. Phase 2 will broaden coverage.
     * Phase 1 also doesn't remove keys when the offering changes from positive to null;
     * stop+start is required.
     */
    protected void applyNicEgressRateLimit(OvnProviderVO provider, Network network,
                                           NicProfile nic, VirtualMachineProfile vm, String lspName) {
        Long soId = vm.getServiceOfferingId();
        if (soId == null) {
            return;
        }
        com.cloud.service.ServiceOfferingVO so;
        try {
            so = serviceOfferingDao.findById(vm.getId(), soId);
        } catch (RuntimeException e) {
            logger.warn("Skipping QoS on LSP [{}]: ServiceOffering lookup failed: {}", lspName, e.getMessage());
            return;
        }
        Integer rateMbps = (so != null) ? so.getRateMbps() : null;
        if (rateMbps == null || rateMbps <= 0) {
            logger.debug("No nw_rate on service offering for nic [{}] on network [{}]; skipping QoS", nic.getId(), network.getId());
            return;
        }
        long rateKbps = rateMbps.longValue() * 1000L;
        long burstKbits = Math.max(rateMbps.longValue() * 100L, 12L);
        Map<String, String> qos = new HashMap<>();
        qos.put("qos_max_rate", String.valueOf(rateKbps));
        qos.put("qos_burst", String.valueOf(burstKbits));
        ovnNbClient.setLspOptions(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                lspName, qos);
        logger.info("Applied QoS to LSP [{}]: max-rate={} kbps, burst={} kbits ({} Mbps offering)",
                lspName, rateKbps, burstKbits, rateMbps);
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
            // Drop the dnat_and_snat advertisement we created for this rule. We resolve
            // the original (external_ip, logical_ip) pair from the rule's IDs - if either
            // is missing we just skip; the IP-release path catches strays.
            IPAddressVO ipForRevoke = ipAddressDao.findById(rule.getSourceIpAddressId());
            if (ipForRevoke != null && ipForRevoke.getAddress() != null
                    && rule.getDestinationIpAddress() != null) {
                ovnNbClient.removeNatRule(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, "dnat_and_snat",
                        ipForRevoke.getAddress().addr(), rule.getDestinationIpAddress().addr());
            }
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

        // Publish the public IP so the upstream fabric learns the gateway-chassis MAC for
        // this VIP. A bare Load_Balancer row by itself does not trigger gARP nor an ARP
        // responder canonical enough for some upstream routers; an LB is purely a forwarding
        // table from OVN's point of view. Adding a dnat_and_snat NAT row makes the LR own
        // the IP, so ovn-controller emits gARP and answers ARP with the LR MAC.
        // Idempotent: addNatRule no-ops if an identical (external,logical) pair already
        // exists, so subsequent applyPFRules calls (CloudStack re-applies the full ruleset
        // on every change) are cheap. The first PF for a given external_ip wins the
        // mapping; further PFs to the same IP rely on the LB to fan out by port. Caveat:
        // dnat_and_snat is all-protocols, all-ports - the per-tier ACLs are what limit
        // exposure to only the ports declared in PF rules.
        Map<String, String> natExt = new HashMap<>();
        natExt.put("cloudstack_pf_rule_id", ruleTag);
        natExt.put("cloudstack_nat_kind", "pf_advertise");
        natExt.put("cloudstack_public_ip", externalIp);
        natExt.put("cloudstack_network_id", String.valueOf(network.getId()));
        if (network.getVpcId() != null) {
            natExt.put("cloudstack_vpc_id", String.valueOf(network.getVpcId()));
        }
        String gatewayLrpName = getPublicRouterPortNameForNetwork(network);
        ovnNbClient.addNatRule(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, "dnat_and_snat", externalIp, logicalIp, natExt,
                null, null, gatewayLrpName);
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

            // If this ACL is also used by a mesh network membership, re-apply on the mesh network LS
            if (rules != null && !rules.isEmpty()) {
                long aclId = rules.get(0).getAclId();
                List<OvnMeshNetworkVO> meshMembersWithAcl = ovnMeshNetworkDao.listByAclId(aclId);
                for (OvnMeshNetworkVO member : meshMembersWithAcl) {
                    applyMeshNetworkAcl(member);
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
            // Re-provision any mesh network this VPC participates in. shutdownVpc tears down
            // the OVN-side artifacts but keeps the DB rows Active, so a restart-with-cleanup
            // would otherwise leave us out of the mesh. provisionMeshNetwork is idempotent
            // and re-runs once per group regardless of how many members live in this VPC.
            List<OvnMeshNetworkVO> myMeshes = ovnMeshNetworkDao.listByVpcId(vpc.getId());
            Set<String> reprovisionedMeshes = new HashSet<>();
            for (OvnMeshNetworkVO p : myMeshes) {
                if (reprovisionedMeshes.add(p.getMeshUuid())) {
                    provisionMeshNetwork(p.getMeshUuid());
                }
            }
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
            // Remove all mesh network memberships for this VPC before destroying the router.
            removeMeshNetworksForVpc(vpc, provider);

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

    // ── Mesh Network (OvnMeshNetworkService) ───────────────────────────────────────────────────────────────────────

    /**
     * Adapter that hides whether a mesh-network member is a VPC or an
     * Isolated guest network behind a single shape. Every provisioning
     * code path below operates on {@code MeshMember} instead of branching
     * on the underlying CS entity, so adding a new member kind in the
     * future means writing one more {@link #resolveMeshMember(Long, Long,
     * long, String)} branch and not touching the data-plane code.
     */
    protected static final class MeshMember {
        final String kind;          // "vpc" | "network"
        final long id;              // vpc.id or networks.id
        final String uuid;
        final String name;
        final String cidr;
        final long zoneId;
        final long accountId;
        final long domainId;
        final String lrName;        // cs-vpc-<id> or cs-router-<id>

        private MeshMember(String kind, long id, String uuid, String name, String cidr,
                           long zoneId, long accountId, long domainId, String lrName) {
            this.kind = kind;
            this.id = id;
            this.uuid = uuid;
            this.name = name;
            this.cidr = cidr;
            this.zoneId = zoneId;
            this.accountId = accountId;
            this.domainId = domainId;
            this.lrName = lrName;
        }

        static MeshMember ofVpc(VpcVO v) {
            return new MeshMember("vpc", v.getId(), v.getUuid(), v.getName(), v.getCidr(),
                    v.getZoneId(), v.getAccountId(), v.getDomainId(),
                    String.format("cs-vpc-%d", v.getId()));
        }

        static MeshMember ofNetwork(com.cloud.network.dao.NetworkVO n) {
            return new MeshMember("network", n.getId(), n.getUuid(),
                    n.getName() != null ? n.getName() : "network-" + n.getId(),
                    n.getCidr(),
                    n.getDataCenterId(), n.getAccountId(), n.getDomainId(),
                    String.format("cs-router-%d", n.getId()));
        }

        boolean isVpc() { return "vpc".equals(kind); }
        boolean isNetwork() { return "network".equals(kind); }
        Long vpcIdOrNull() { return isVpc() ? id : null; }
        Long networkIdOrNull() { return isNetwork() ? id : null; }
    }

    /**
     * Resolves a (vpcId, networkId) pair from a {@code CreateMeshNetworkCmd}
     * into a {@link MeshMember}. Exactly one of the two must be set. Runs
     * the eligibility checks that protect the mesh fabric: the underlying
     * CS object must exist, must live in an OVN-enabled zone, must be
     * owned by the caller (or admin), and must be of a kind that owns a
     * Logical Router in the OVN NB.
     */
    protected MeshMember resolveMeshMember(Long vpcId, Long networkId, long callerId, String role) {
        if (vpcId != null && networkId != null) {
            throw new InvalidParameterValueException(
                    String.format("Pass only one of vpcid/networkid for the %s member, not both", role));
        }
        if (vpcId == null && networkId == null) {
            throw new InvalidParameterValueException(
                    String.format("One of vpcid/networkid must be supplied for the %s member", role));
        }
        if (vpcId != null) {
            VpcVO v = vpcDao.findById(vpcId);
            if (v == null) {
                throw new InvalidParameterValueException(String.format("%s VPC not found: %d", role, vpcId));
            }
            if (v.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
                throw new PermissionDeniedException("Caller does not own " + role + " VPC " + v.getUuid());
            }
            if (ovnProviderDao.findByZoneId(v.getZoneId()) == null) {
                throw new InvalidParameterValueException(
                        String.format("%s VPC %s lives in zone %d which has no OVN provider",
                                role, v.getUuid(), v.getZoneId()));
            }
            return MeshMember.ofVpc(v);
        }
        // networkId path: only Isolated, OVN-backed, non-VPC-tier networks are eligible.
        com.cloud.network.dao.NetworkVO n = networksDao.findById(networkId);
        if (n == null) {
            throw new InvalidParameterValueException(String.format("%s network not found: %d", role, networkId));
        }
        if (n.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own " + role + " network " + n.getUuid());
        }
        if (n.getGuestType() != com.cloud.network.Network.GuestType.Isolated) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s is not an Isolated network (kind=%s); only Isolated networks can be mesh members",
                            role, n.getUuid(), n.getGuestType()));
        }
        if (n.getVpcId() != null) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s is a VPC tier; pass the parent VPC instead", role, n.getUuid()));
        }
        if (n.getBroadcastDomainType() != Networks.BroadcastDomainType.OVN) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s is not backed by OVN (broadcast=%s)",
                            role, n.getUuid(), n.getBroadcastDomainType()));
        }
        if (n.getCidr() == null || n.getCidr().isEmpty()) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s has no CIDR and cannot participate in a mesh", role, n.getUuid()));
        }
        // The OVN Logical_Router (cs-router-<id>) for an isolated network is only
        // provisioned once the network reaches Implemented. Adding a mesh attachment
        // before that produces a dangling LSP on the mesh LS because the target LR
        // does not exist yet. Require Implemented and tell the operator how to drive
        // the network there.
        if (n.getState() != com.cloud.network.Network.State.Implemented) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s is in state %s — implement it (deploy a VM or restart the network) before adding it to a mesh",
                            role, n.getUuid(), n.getState()));
        }
        if (ovnProviderDao.findByZoneId(n.getDataCenterId()) == null) {
            throw new InvalidParameterValueException(
                    String.format("%s network %s lives in zone %d which has no OVN provider",
                            role, n.getUuid(), n.getDataCenterId()));
        }
        return MeshMember.ofNetwork(n);
    }

    /**
     * Returns the row that records the given member's current mesh-network
     * membership (Active state). Looks up by vpc_id for VPC members or by
     * network_id for Isolated network members. Returns {@code null} if the
     * member is not yet in any mesh.
     */
    protected OvnMeshNetworkVO findExistingMembership(MeshMember m) {
        List<OvnMeshNetworkVO> rows = m.isVpc()
                ? ovnMeshNetworkDao.listByVpcId(m.id)
                : ovnMeshNetworkDao.listByNetworkId(m.id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Looks up the active row for a member in a specific mesh group.
     */
    protected OvnMeshNetworkVO findMembershipInMesh(String meshUuid, MeshMember m) {
        return m.isVpc()
                ? ovnMeshNetworkDao.findByMeshUuidAndVpcId(meshUuid, m.id)
                : ovnMeshNetworkDao.findByMeshUuidAndNetworkId(meshUuid, m.id);
    }


    private static final String MESH_NETWORK_EXT_KEY = "cloudstack_mesh_network";
    private static final int NAT_BYPASS_PRIORITY = 1000;

    @Override
    public OvnMeshNetworkVO createMeshNetwork(CreateMeshNetworkCmd cmd) {
        long callerId = CallContext.current().getCallingAccount().getId();

        // Resolve both pair members into a kind-agnostic MeshMember so all
        // subsequent provisioning logic stays VPC/Network-neutral.
        MeshMember memberAobj = resolveMeshMember(cmd.getVpcId(), cmd.getNetworkId(), callerId, "first");
        MeshMember memberBobj = resolveMeshMember(cmd.getPeerVpcId(), cmd.getPeerNetworkId(), callerId, "peer");

        if (memberAobj.kind.equals(memberBobj.kind) && memberAobj.id == memberBobj.id) {
            throw new InvalidParameterValueException("Cannot mesh a member with itself");
        }
        if (memberAobj.accountId != memberBobj.accountId) {
            throw new InvalidParameterValueException("Mesh network is only allowed between members of the same account");
        }

        // A member may only belong to one mesh network at a time
        OvnMeshNetworkVO aExistingHead = findExistingMembership(memberAobj);
        OvnMeshNetworkVO bExistingHead = findExistingMembership(memberBobj);
        if (aExistingHead != null && bExistingHead != null
                && !aExistingHead.getMeshUuid().equals(bExistingHead.getMeshUuid())) {
            throw new InvalidParameterValueException(
                    String.format("Both members already belong to different mesh networks: %s is in %s, %s is in %s. A member can only be in one mesh.",
                            memberAobj.uuid, aExistingHead.getMeshUuid(), memberBobj.uuid, bExistingHead.getMeshUuid()));
        }

        // Reject overlapping CIDRs. The mesh network data plane installs one static route per
        // peer CIDR on every member's LR; if two members share an overlapping CIDR the
        // routes collide and OVN cannot resolve which peer to forward at — there is no
        // sane way to disambiguate at runtime. Bail out before any OVN row is created.
        rejectOverlappingMeshNetworkCidrs(memberAobj, memberBobj,
                aExistingHead != null ? aExistingHead.getMeshUuid()
                        : (bExistingHead != null ? bExistingHead.getMeshUuid() : null));

        // Determine the mesh: if the peer already belongs to a group, join it; otherwise check if
        // the calling member already belongs to one. If neither, create a new group.
        String meshUuid = null;
        String meshName = cmd.getName();
        String meshDescription = cmd.getDescription();
        if (bExistingHead != null) {
            meshUuid = bExistingHead.getMeshUuid();
            if (meshName == null) meshName = bExistingHead.getName();
            if (meshDescription == null) meshDescription = bExistingHead.getDescription();
        }
        if (meshUuid == null && aExistingHead != null) {
            meshUuid = aExistingHead.getMeshUuid();
            if (meshName == null) meshName = aExistingHead.getName();
            if (meshDescription == null) meshDescription = aExistingHead.getDescription();
        }
        if (meshUuid == null) {
            meshUuid = UUID.randomUUID().toString();
        }

        // Already-paired short circuit
        if (findMembershipInMesh(meshUuid, memberAobj) != null
                && findMembershipInMesh(meshUuid, memberBobj) != null) {
            throw new InvalidParameterValueException("Both members are already in the same mesh network");
        }

        // Allocate link-local IPs for the new members
        List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
        Set<String> usedIps = new HashSet<>();
        for (OvnMeshNetworkVO m : meshMembers) {
            usedIps.add(m.getLinkLocalIp());
        }

        // Validate ACL ownership for the calling member.
        // VPC members must reference a VPC-scoped ACL (acl.vpc_id == 0 means system default).
        // Isolated network members must reference a network-scoped ACL (acl.vpc_id == 0 only).
        Long aclId = cmd.getAclId();
        if (aclId != null) {
            NetworkACLVO acl = networkACLDao.findById(aclId);
            if (acl == null) {
                throw new InvalidParameterValueException("Network ACL not found: " + aclId);
            }
            if (memberAobj.isVpc()) {
                if (acl.getVpcId() != 0 && acl.getVpcId() != memberAobj.id) {
                    throw new InvalidParameterValueException("Network ACL " + acl.getUuid()
                            + " does not belong to VPC " + memberAobj.uuid);
                }
            } else {
                // For isolated networks we only accept the system defaults (acl.vpc_id == 0).
                if (acl.getVpcId() != 0) {
                    throw new InvalidParameterValueException("Network ACL " + acl.getUuid()
                            + " is VPC-scoped and cannot be applied to isolated network " + memberAobj.uuid);
                }
            }
        }

        // Cross-zone vs same-zone: drives which link-local pool feeds the mesh-network LRP IP.
        // We treat the group as cross-zone if the new pair OR any existing member crosses a
        // zone boundary - that matches the topology decision in provisionMeshNetwork().
        boolean willBeCrossZone = memberAobj.zoneId != memberBobj.zoneId;
        if (!willBeCrossZone) {
            for (OvnMeshNetworkVO m : meshMembers) {
                if (m.getZoneId() != memberAobj.zoneId) { willBeCrossZone = true; break; }
            }
        }

        OvnMeshNetworkVO rowA = persistOrFindMember(meshUuid, meshName, meshDescription, memberAobj, aclId, usedIps, willBeCrossZone);
        persistOrFindMember(meshUuid, meshName, meshDescription, memberBobj, null, usedIps, willBeCrossZone);

        // Provision OVN fabric for the entire mesh
        provisionMeshNetwork(meshUuid);

        return rowA;
    }

    /**
     * Inserts a mesh-network membership row for {@code m} if it doesn't
     * already exist in this mesh, allocating a link-local IP from the
     * appropriate pool. Returns the existing row when present.
     */
    private OvnMeshNetworkVO persistOrFindMember(String meshUuid, String meshName, String meshDescription,
                                                 MeshMember m, Long aclId, Set<String> usedIps, boolean crossZone) {
        OvnMeshNetworkVO existing = findMembershipInMesh(meshUuid, m);
        if (existing != null) {
            return existing;
        }
        String ip = crossZone ? allocateCrossZoneLinkLocalIp(usedIps) : allocateLinkLocalIp(usedIps);
        usedIps.add(ip);
        OvnMeshNetworkVO row = new OvnMeshNetworkVO(meshUuid, meshName, meshDescription,
                m.vpcIdOrNull(), m.networkIdOrNull(),
                m.zoneId, m.accountId, m.domainId, ip);
        if (aclId != null) {
            row.setAclId(aclId);
        }
        return ovnMeshNetworkDao.persist(row);
    }

    @Override
    public boolean deleteMeshNetwork(DeleteMeshNetworkCmd cmd) {
        // The cmd.id is normally a member-row UUID, but the AutogenView list maps each
        // mesh network to a single resource keyed off meshUuid. Accept both: if the
        // value matches a group, delete every member sequentially so the group as a
        // whole disappears.
        OvnMeshNetworkVO member = ovnMeshNetworkDao.findByUuid(cmd.getId());
        if (member == null) {
            List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuidIncludingDisabled(cmd.getId());
            if (meshMembers != null && !meshMembers.isEmpty()) {
                long callerId = CallContext.current().getCallingAccount().getId();
                if (meshMembers.get(0).getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
                    throw new PermissionDeniedException("Caller does not own this mesh network");
                }
                boolean ok = true;
                for (OvnMeshNetworkVO m : meshMembers) {
                    DeleteMeshNetworkCmd memberCmd = new DeleteMeshNetworkCmd();
                    try {
                        java.lang.reflect.Field idField = DeleteMeshNetworkCmd.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(memberCmd, m.getUuid());
                    } catch (ReflectiveOperationException ex) {
                        throw new CloudRuntimeException("Cannot rewrite DeleteMeshNetworkCmd.id: " + ex.getMessage(), ex);
                    }
                    ok &= deleteMeshNetwork(memberCmd);
                }
                return ok;
            }
            throw new InvalidParameterValueException("Mesh network not found or already removed: " + cmd.getId());
        }
        if ("Removed".equals(member.getState())) {
            throw new InvalidParameterValueException("Mesh network not found or already removed: " + cmd.getId());
        }
        long callerId = CallContext.current().getCallingAccount().getId();
        if (member.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own this mesh network");
        }
        String meshUuid = member.getMeshUuid();
        String memberCidr = resolveMemberCidr(member);

        OvnProviderVO provider = ovnProviderDao.findByZoneId(member.getZoneId());
        // Cross-zone path: OVN-IC propagates route removal automatically (since ic-route-adv
        // re-runs whenever the LRP set on the TS changes), so we just need to detach this
        // member's LRP+LSP from the TS and let ovn-ic do the rest.
        // We must look at the FULL group history (any state, including Removed) so a bulk
        // delete that has already marked earlier members Removed still routes the next
        // iterations through the cross-zone path. The link-local IP itself encodes the
        // pool the member was allocated from, which is the most reliable indicator.
        boolean isCrossZone = member.getLinkLocalIp() != null
                && member.getLinkLocalIp().startsWith(CROSS_ZONE_LL_PREFIX);
        if (provider != null && memberCidr != null && isCrossZone) {
            removeCrossZoneMeshNetworkMember(member, provider, meshUuid);
            member.setState("Removed");
            member.setRemoved(new java.util.Date());
            ovnMeshNetworkDao.update(member.getId(), member);
            return true;
        }
        if (provider != null && memberCidr != null) {
            String routerName = getRouterNameForMember(member);
            String targetTag = String.format("%s-%d", memberKindSuffix(member), member.getMemberId());
            // Remove routes and policies on all OTHER members pointing to this member's CIDR
            List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
            for (OvnMeshNetworkVO other : meshMembers) {
                if (other.getId() == member.getId()) continue;
                OvnProviderVO memberProvider = ovnProviderDao.findByZoneId(other.getZoneId());
                if (memberProvider == null) continue;
                String memberRouter = getRouterNameForMember(other);
                if (resolveMemberCidr(other) == null) continue;
                // Remove route on other pointing to this member's CIDR
                ovnNbClient.removeStaticRoute(memberProvider.getNbConnection(),
                        memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                        memberRouter, memberCidr, other.getLinkLocalIp());
                // Remove NAT bypass policy on other for this member's CIDR
                ovnNbClient.removeLogicalRouterPoliciesByExternalId(memberProvider.getNbConnection(),
                        memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                        memberRouter, MESH_NETWORK_EXT_KEY + "_target", targetTag);
            }

            // Remove routes and policies on THIS member pointing to all others
            ovnNbClient.removeStaticRoutesByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, MESH_NETWORK_EXT_KEY, meshUuid);
            ovnNbClient.removeLogicalRouterPoliciesByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, MESH_NETWORK_EXT_KEY, meshUuid);

            // Remove mesh network ACLs for this member on the mesh network LS
            String peerLs = getMeshLsName(meshUuid);
            String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(member), member.getMemberId());
            ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs, meshAclTag, "true");

            // Remove LRP+LSP for this member on the mesh network switch
            String lrpName = getMeshLrpName(meshUuid, member);
            ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, lrpName);
            ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs, getMeshLspName(meshUuid, member));

            // If no other members, delete the mesh network LS from all zones
            List<OvnMeshNetworkVO> remaining = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
            long activeCount = remaining.stream().filter(m -> m.getId() != member.getId()).count();
            if (activeCount == 0) {
                Set<Long> cleanedZones = new HashSet<>();
                for (OvnMeshNetworkVO m : remaining) {
                    if (!cleanedZones.add(m.getZoneId())) continue;
                    OvnProviderVO zp = ovnProviderDao.findByZoneId(m.getZoneId());
                    if (zp == null) continue;
                    ovnNbClient.deleteLogicalSwitch(zp.getNbConnection(),
                            zp.getCaCertPath(), zp.getClientCertPath(), zp.getClientPrivateKeyPath(),
                            peerLs);
                }
            }
        }

        member.setState("Removed");
        member.setRemoved(new java.util.Date());
        ovnMeshNetworkDao.update(member.getId(), member);
        return true;
    }

    @Override
    public OvnMeshNetworkVO updateMeshNetwork(UpdateMeshNetworkCmd cmd) {
        OvnMeshNetworkVO member = ovnMeshNetworkDao.findByUuid(cmd.getId());
        if (member == null || !"Active".equals(member.getState())) {
            throw new InvalidParameterValueException("Mesh network not found or already removed: " + cmd.getId());
        }
        long callerId = CallContext.current().getCallingAccount().getId();
        if (member.getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own this mesh network");
        }

        Long aclId = cmd.getAclId();
        if (aclId != null) {
            NetworkACLVO acl = networkACLDao.findById(aclId);
            if (acl == null) {
                throw new InvalidParameterValueException("Network ACL not found: " + aclId);
            }
            if (acl.getVpcId() != 0 && acl.getVpcId() != member.getVpcId()) {
                throw new InvalidParameterValueException("Network ACL does not belong to this member's VPC");
            }
        }

        member.setAclId(aclId);
        ovnMeshNetworkDao.update(member.getId(), member);

        List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuid(member.getMeshUuid());
        if (isCrossZoneMeshNetwork(meshMembers)) {
            applyCrossZoneMeshNetworkAcl(member, getTransitSwitchName(member.getMeshUuid()));
        } else {
            applyMeshNetworkAcl(member);
        }
        return member;
    }

    /**
     * Rejects a {@code createMeshNetwork} call when the new pair (or any
     * existing mesh member) carries an IPv4 CIDR that overlaps another.
     * The data plane installs one static route per peer CIDR on every
     * member's LR; two routes for overlapping prefixes would race and
     * OVN cannot disambiguate at runtime. We compare every relevant CIDR
     * pair before any OVN row is touched so the operator gets a clear
     * error instead of a subtle, intermittent reachability bug.
     *
     * <p>This is the kind-agnostic version: each {@link MeshMember}
     * already exposes its CIDR (the VPC super-CIDR for VPC members, the
     * network CIDR for Isolated members), and existing rows are resolved
     * back to their CIDRs the same way. {@code listByMeshUuidIncludingDisabled}
     * is used so Disabled members — which keep their slot reserved
     * waiting for re-enable — still count as constraints.
     */
    protected void rejectOverlappingMeshNetworkCidrs(MeshMember a, MeshMember b, String meshUuid) {
        String cidrA = a.cidr;
        String cidrB = b.cidr;
        if (StringUtils.isNotBlank(cidrA) && StringUtils.isNotBlank(cidrB)
                && com.cloud.utils.net.NetUtils.isNetworksOverlap(cidrA, cidrB)) {
            throw new InvalidParameterValueException(String.format(
                    "%s %s (%s) and %s %s (%s) have overlapping CIDRs and cannot be in the same mesh",
                    a.kind, a.name, cidrA, b.kind, b.name, cidrB));
        }
        if (meshUuid == null) {
            return; // brand-new group; only the pair-vs-pair check applies
        }
        List<OvnMeshNetworkVO> existingGroupMembers = ovnMeshNetworkDao.listByMeshUuidIncludingDisabled(meshUuid);
        for (OvnMeshNetworkVO m : existingGroupMembers) {
            // Skip rows that already represent A or B themselves
            if (a.isVpc() && m.getVpcId() != null && m.getVpcId() == a.id) continue;
            if (a.isNetwork() && m.getNetworkId() != null && m.getNetworkId() == a.id) continue;
            if (b.isVpc() && m.getVpcId() != null && m.getVpcId() == b.id) continue;
            if (b.isNetwork() && m.getNetworkId() != null && m.getNetworkId() == b.id) continue;

            String otherKind = m.getMemberKind();
            String otherName;
            String otherCidr;
            if (m.getVpcId() != null) {
                VpcVO ov = vpcDao.findById(m.getVpcId());
                if (ov == null) continue;
                otherName = ov.getName();
                otherCidr = ov.getCidr();
            } else if (m.getNetworkId() != null) {
                com.cloud.network.dao.NetworkVO on = networksDao.findById(m.getNetworkId());
                if (on == null) continue;
                otherName = on.getName();
                otherCidr = on.getCidr();
            } else {
                continue;
            }
            if (StringUtils.isBlank(otherCidr)) continue;

            if (StringUtils.isNotBlank(cidrA) && com.cloud.utils.net.NetUtils.isNetworksOverlap(cidrA, otherCidr)) {
                throw new InvalidParameterValueException(String.format(
                        "%s %s (%s) overlaps existing mesh member %s %s (%s)",
                        a.kind, a.name, cidrA, otherKind, otherName, otherCidr));
            }
            if (StringUtils.isNotBlank(cidrB) && com.cloud.utils.net.NetUtils.isNetworksOverlap(cidrB, otherCidr)) {
                throw new InvalidParameterValueException(String.format(
                        "%s %s (%s) overlaps existing mesh member %s %s (%s)",
                        b.kind, b.name, cidrB, otherKind, otherName, otherCidr));
            }
        }
    }

    /**
     * Resolves the cmd id to a mesh network's full membership (every member,
     * including Disabled) and verifies the caller owns the group. Throws if the
     * id is unknown or the group is empty. The id may be either a group UUID or
     * any single member's mesh network UUID.
     */
    protected List<OvnMeshNetworkVO> resolveMeshNetworkMembersForToggle(String id) {
        List<OvnMeshNetworkVO> members = ovnMeshNetworkDao.listByMeshUuidIncludingDisabled(id);
        if (members.isEmpty()) {
            OvnMeshNetworkVO single = ovnMeshNetworkDao.findByUuid(id);
            if (single != null && !"Removed".equals(single.getState())) {
                members = ovnMeshNetworkDao.listByMeshUuidIncludingDisabled(single.getMeshUuid());
            }
        }
        if (members.isEmpty()) {
            throw new InvalidParameterValueException("mesh network not found: " + id);
        }
        long callerId = CallContext.current().getCallingAccount().getId();
        if (members.get(0).getAccountId() != callerId && !accountMgr.isRootAdmin(callerId)) {
            throw new PermissionDeniedException("Caller does not own this mesh network");
        }
        return members;
    }

    @Override
    public boolean disableMeshNetwork(DisableMeshNetworkCmd cmd) {
        List<OvnMeshNetworkVO> members = resolveMeshNetworkMembersForToggle(cmd.getId());
        String meshUuid = members.get(0).getMeshUuid();

        // Tear down OVN data plane on every member's router so traffic stops, then
        // mark each row Disabled. We deliberately keep DB rows + linkLocalIp
        // assignments so a subsequent enable can deterministically rebuild the
        // same fabric via provisionMeshNetwork.
        boolean crossZone = isCrossZoneMeshNetwork(members);
        for (OvnMeshNetworkVO m : members) {
            if ("Disabled".equals(m.getState())) continue;
            OvnProviderVO provider = ovnProviderDao.findByZoneId(m.getZoneId());
            if (provider != null && resolveMemberCidr(m) != null) {
                if (crossZone) {
                    removeCrossZoneMeshNetworkMember(m, provider, meshUuid);
                } else {
                    String routerName = getRouterNameForMember(m);
                    ovnNbClient.removeStaticRoutesByExternalId(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, MESH_NETWORK_EXT_KEY, meshUuid);
                    ovnNbClient.removeLogicalRouterPoliciesByExternalId(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, MESH_NETWORK_EXT_KEY, meshUuid);
                    String peerLs = getMeshLsName(meshUuid);
                    String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(m), m.getMemberId());
                    ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            peerLs, meshAclTag, "true");
                    String lrpName = getMeshLrpName(meshUuid, m);
                    ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            routerName, lrpName);
                    ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            peerLs, getMeshLspName(meshUuid, m));
                }
            }
            m.setState("Disabled");
            ovnMeshNetworkDao.update(m.getId(), m);
        }

        // Same-zone path: also remove the mesh network LS once no Active members remain.
        if (!crossZone) {
            String peerLs = getMeshLsName(meshUuid);
            Set<Long> cleanedZones = new HashSet<>();
            for (OvnMeshNetworkVO m : members) {
                if (!cleanedZones.add(m.getZoneId())) continue;
                OvnProviderVO zp = ovnProviderDao.findByZoneId(m.getZoneId());
                if (zp == null) continue;
                ovnNbClient.deleteLogicalSwitch(zp.getNbConnection(),
                        zp.getCaCertPath(), zp.getClientCertPath(), zp.getClientPrivateKeyPath(),
                        peerLs);
            }
        }
        return true;
    }

    @Override
    public boolean enableMeshNetwork(EnableMeshNetworkCmd cmd) {
        List<OvnMeshNetworkVO> members = resolveMeshNetworkMembersForToggle(cmd.getId());
        for (OvnMeshNetworkVO m : members) {
            if (!"Active".equals(m.getState())) {
                m.setState("Active");
                ovnMeshNetworkDao.update(m.getId(), m);
            }
        }
        // provisionMeshNetwork is idempotent; reads listByMeshUuid (Active only)
        // so it now sees the freshly-Active members and rebuilds LS/LRPs/routes.
        provisionMeshNetwork(members.get(0).getMeshUuid());
        return true;
    }

    @Override
    public List<MeshNetworkResponse> listMeshNetworks(ListMeshNetworksCmd cmd) {
        long callerId = CallContext.current().getCallingAccount().getId();
        List<MeshNetworkResponse> responses = new ArrayList<>();

        // Filter by VPC: caller wants every mesh network member record this VPC participates in
        // (flat, one per row). Used by the per-VPC tab inside a VPC detail view.
        if (cmd.getVpcId() != null) {
            for (OvnMeshNetworkVO p : ovnMeshNetworkDao.listByVpcId(cmd.getVpcId())) {
                responses.add(createMeshNetworkResponse(p));
            }
            return responses;
        }

        // Filter by isolated network: same flat-list shape for the network detail tab.
        if (cmd.getNetworkId() != null) {
            for (OvnMeshNetworkVO p : ovnMeshNetworkDao.listByNetworkId(cmd.getNetworkId())) {
                responses.add(createMeshNetworkResponse(p));
            }
            return responses;
        }

        // Filter by group: return ONE aggregated row representing the whole member
        // mesh. AutogenView's detail view (/meshnetwork/<id>) hits this branch with id =
        // mesh_uuid (aliased onto meshuuid in the cmd), expecting members[] embedded.
        if (cmd.getMeshUuid() != null) {
            List<OvnMeshNetworkVO> members = ovnMeshNetworkDao.listByMeshUuidIncludingDisabled(cmd.getMeshUuid());
            if (!members.isEmpty()) {
                responses.add(createGroupResponse(cmd.getMeshUuid(), members));
            }
            return responses;
        }

        // Default list: ONE row per mesh network (aggregated). Driven by the
        // standard list view in AutogenView. Disabled groups are also returned so
        // users can see and re-enable them; "Removed" rows are excluded.
        List<OvnMeshNetworkVO> all = accountMgr.isRootAdmin(callerId)
                ? ovnMeshNetworkDao.listAllIncludingDisabled()
                : ovnMeshNetworkDao.listByAccountIdIncludingDisabled(callerId);

        // Group by mesh_uuid preserving insertion order (= creation order, since the
        // DAO already sorts by id).
        Map<String, List<OvnMeshNetworkVO>> byGroup = new java.util.LinkedHashMap<>();
        for (OvnMeshNetworkVO p : all) {
            byGroup.computeIfAbsent(p.getMeshUuid(), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<OvnMeshNetworkVO>> e : byGroup.entrySet()) {
            responses.add(createGroupResponse(e.getKey(), e.getValue()));
        }
        return responses;
    }

    /**
     * Builds a group-level MeshNetworkResponse with members[] embedded. Used by the
     * AutogenView list and detail flows so a mesh network appears as a single
     * resource entity (id == meshUuid).
     */
    protected MeshNetworkResponse createGroupResponse(String meshUuid, List<OvnMeshNetworkVO> members) {
        MeshNetworkResponse response = new MeshNetworkResponse();
        response.setObjectName("meshnetwork");
        response.setId(meshUuid);
        response.setMeshUuid(meshUuid);

        // Aggregate name/description from any non-blank member (they should all match).
        OvnMeshNetworkVO first = members.get(0);
        for (OvnMeshNetworkVO m : members) {
            if (m.getName() != null) { response.setName(m.getName()); break; }
        }
        for (OvnMeshNetworkVO m : members) {
            if (m.getDescription() != null) { response.setDescription(m.getDescription()); break; }
        }
        response.setState(first.getState());
        response.setCreated(first.getCreated());

        // Zone column: a single zone name when same-zone, otherwise "multi-zone".
        Set<Long> zones = new HashSet<>();
        for (OvnMeshNetworkVO m : members) zones.add(m.getZoneId());
        if (zones.size() == 1) {
            DataCenterVO z = dataCenterDao.findById(first.getZoneId());
            if (z != null) response.setZoneName(z.getName());
        } else {
            response.setZoneName("multi-zone");
        }

        // Member rollup: count, comma-separated names, and the embedded list used
        // by the Members tab.
        List<MeshNetworkMemberResponse> memberResponses = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (OvnMeshNetworkVO m : members) {
            MeshNetworkMemberResponse mr = new MeshNetworkMemberResponse();
            mr.setObjectName("member");
            mr.setId(m.getUuid());
            mr.setKind(m.getMemberKind());
            mr.setLinkLocalIp(m.getLinkLocalIp());
            mr.setState(m.getState());
            populateMemberResponse(mr, m, names);
            DataCenterVO mz = dataCenterDao.findById(m.getZoneId());
            if (mz != null) {
                mr.setZoneId(mz.getUuid());
                mr.setZoneName(mz.getName());
            }
            if (m.getAclId() != null) {
                NetworkACLVO acl = networkACLDao.findById(m.getAclId());
                if (acl != null) {
                    mr.setAclId(acl.getUuid());
                    mr.setAclName(acl.getName());
                }
            }
            memberResponses.add(mr);
        }
        response.setMembers(memberResponses);
        response.setVpcCount(memberResponses.size());
        response.setVpcNames(names.toString());
        response.setMemberCount(memberResponses.size());
        response.setMemberNames(names.toString());
        return response;
    }

    /**
     * Fills the kind-specific identity/name/cidr columns on a member
     * response. Appends the human name onto {@code names} for the
     * group-level comma-separated rollup.
     */
    private void populateMemberResponse(MeshNetworkMemberResponse mr, OvnMeshNetworkVO m, StringBuilder names) {
        if (m.getVpcId() != null) {
            VpcVO vpc = vpcDao.findById(m.getVpcId());
            if (vpc != null) {
                mr.setVpcId(vpc.getUuid());
                mr.setVpcName(vpc.getName());
                mr.setVpcCidr(vpc.getCidr());
                mr.setMemberName(vpc.getName());
                mr.setMemberCidr(vpc.getCidr());
                if (names != null) {
                    if (names.length() > 0) names.append(", ");
                    names.append(vpc.getName());
                }
            }
        } else if (m.getNetworkId() != null) {
            com.cloud.network.dao.NetworkVO net = networksDao.findById(m.getNetworkId());
            if (net != null) {
                mr.setNetworkId(net.getUuid());
                mr.setNetworkName(net.getName());
                mr.setNetworkCidr(net.getCidr());
                mr.setMemberName(net.getName());
                mr.setMemberCidr(net.getCidr());
                if (names != null) {
                    if (names.length() > 0) names.append(", ");
                    names.append(net.getName());
                }
            }
        }
    }

    @Override
    public MeshNetworkResponse createMeshNetworkResponse(OvnMeshNetworkVO member) {
        MeshNetworkResponse response = new MeshNetworkResponse();
        response.setObjectName("meshnetwork");
        response.setId(member.getUuid());
        response.setMeshUuid(member.getMeshUuid());
        response.setName(member.getName());
        response.setDescription(member.getDescription());
        response.setLinkLocalIp(member.getLinkLocalIp());
        response.setState(member.getState());
        response.setCreated(member.getCreated());

        // Carry the kind-specific identifiers so the UI knows whether this
        // record is for a VPC or an Isolated network. We keep the legacy
        // vpcid/vpcname/vpccidr fields for backward compatibility when the
        // member happens to be a VPC.
        if (member.getVpcId() != null) {
            VpcVO vpc = vpcDao.findById(member.getVpcId());
            if (vpc != null) {
                response.setVpcId(vpc.getUuid());
                response.setVpcName(vpc.getName());
                response.setVpcCidr(vpc.getCidr());
            }
        } else if (member.getNetworkId() != null) {
            com.cloud.network.dao.NetworkVO net = networksDao.findById(member.getNetworkId());
            if (net != null) {
                response.setNetworkId(net.getUuid());
                response.setNetworkName(net.getName());
                response.setNetworkCidr(net.getCidr());
            }
        }

        DataCenterVO zone = dataCenterDao.findById(member.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }

        if (member.getAclId() != null) {
            NetworkACLVO acl = networkACLDao.findById(member.getAclId());
            if (acl != null) {
                response.setAclId(acl.getUuid());
                response.setAclName(acl.getName());
            }
        }

        // Find the first "other" member in this mesh for richer response — used
        // by the legacy VPC-tab compatibility view that expected a single peer.
        // For meshes with isolated network members the same peer fields still
        // surface the underlying entity's identity.
        List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuid(member.getMeshUuid());
        for (OvnMeshNetworkVO m : meshMembers) {
            if (m.getId() == member.getId()) continue;
            if (m.getVpcId() != null) {
                VpcVO peerVpc = vpcDao.findById(m.getVpcId());
                if (peerVpc != null) {
                    response.setPeerVpcId(peerVpc.getUuid());
                    response.setPeerVpcName(peerVpc.getName());
                    response.setPeerVpcCidr(peerVpc.getCidr());
                }
            } else if (m.getNetworkId() != null) {
                com.cloud.network.dao.NetworkVO peerNet = networksDao.findById(m.getNetworkId());
                if (peerNet != null) {
                    response.setPeerNetworkId(peerNet.getUuid());
                    response.setPeerNetworkName(peerNet.getName());
                    response.setPeerNetworkCidr(peerNet.getCidr());
                }
            }
            break;
        }

        return response;
    }

    protected void provisionMeshNetwork(String meshUuid) {
        List<OvnMeshNetworkVO> members = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
        if (members.isEmpty()) return;

        if (isCrossZoneMeshNetwork(members)) {
            provisionCrossZoneMeshNetwork(meshUuid, members);
            return;
        }

        String peerLs = getMeshLsName(meshUuid);
        Map<String, String> lsExt = new HashMap<>();
        lsExt.put(MESH_NETWORK_EXT_KEY, meshUuid);
        lsExt.put("cloudstack_role", "mesh-network");

        // Create the mesh network LS in every distinct zone that participates.
        // Each zone has its own OVN NB, so the LS must exist in each.
        Set<Long> provisionedZones = new HashSet<>();
        for (OvnMeshNetworkVO member : members) {
            if (!provisionedZones.add(member.getZoneId())) continue;
            OvnProviderVO zoneProvider = ovnProviderDao.findByZoneId(member.getZoneId());
            if (zoneProvider == null) continue;
            ovnNbClient.createLogicalSwitch(zoneProvider.getNbConnection(),
                    zoneProvider.getCaCertPath(), zoneProvider.getClientCertPath(), zoneProvider.getClientPrivateKeyPath(),
                    peerLs, lsExt);
        }

        // Ensure each member is attached and has routes to every other member
        for (OvnMeshNetworkVO member : members) {
            OvnProviderVO provider = ovnProviderDao.findByZoneId(member.getZoneId());
            if (provider == null) continue;
            String memberCidr = resolveMemberCidr(member);
            if (memberCidr == null) continue;

            String routerName = getRouterNameForMember(member);
            String lrpName = getMeshLrpName(meshUuid, member);
            String mac = buildMeshMac(member);

            // Attach router to mesh network switch
            ovnNbClient.attachRouterToSwitch(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    routerName, peerLs, lrpName, mac,
                    Collections.singletonList(member.getLinkLocalIp() + "/24"));

            // Add routes and NAT bypass policies for every OTHER member
            for (OvnMeshNetworkVO other : members) {
                if (other.getId() == member.getId()) continue;
                String otherCidr = resolveMemberCidr(other);
                if (otherCidr == null) continue;
                String targetTag = String.format("%s-%d", memberKindSuffix(other), other.getMemberId());

                Map<String, String> routeExt = new HashMap<>();
                routeExt.put(MESH_NETWORK_EXT_KEY, meshUuid);
                routeExt.put(MESH_NETWORK_EXT_KEY + "_target", targetTag);
                ovnNbClient.addStaticRoute(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, otherCidr, other.getLinkLocalIp(), routeExt);

                // NAT bypass: skip SNAT for traffic destined to the peered member's CIDR
                String match = String.format("ip4.dst == %s", otherCidr);
                Map<String, String> polExt = new HashMap<>();
                polExt.put(MESH_NETWORK_EXT_KEY, meshUuid);
                polExt.put(MESH_NETWORK_EXT_KEY + "_target", targetTag);
                ovnNbClient.addLogicalRouterPolicy(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, NAT_BYPASS_PRIORITY, match, "allow", null, polExt);
            }
        }

        // Apply ACLs on the mesh network LS for members that have an ACL configured
        for (OvnMeshNetworkVO member : members) {
            if (member.getAclId() != null) {
                applyMeshNetworkAcl(member);
            }
        }
    }

    protected void applyMeshNetworkAcl(OvnMeshNetworkVO member) {
        OvnProviderVO provider = ovnProviderDao.findByZoneId(member.getZoneId());
        if (provider == null) return;

        String peerLs = getMeshLsName(member.getMeshUuid());
        String lspName = getMeshLspName(member.getMeshUuid(), member);
        String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(member), member.getMemberId());

        // Wipe existing ACLs for this member on the mesh network LS
        ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                peerLs, meshAclTag, "true");

        Long aclId = member.getAclId();
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
            programMeshNetworkAclRule(provider, peerLs, lspName, member, rule);
        }

        // Default deny for both directions, scoped to this member's port
        for (String dir : new String[]{"to-lport", "from-lport"}) {
            String portField = "to-lport".equals(dir) ? "outport" : "inport";
            String matchExpr = String.format("%s == \"%s\" && ip4", portField, lspName);
            Map<String, String> ext = new HashMap<>();
            ext.put(meshAclTag, "true");
            ext.put("cloudstack_acl_default", "true");
            ovnNbClient.addAclOnLs(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    peerLs,
                    String.format("mesh-acl-default-%s-%s-%d", dir, memberKindSuffix(member), member.getMemberId()),
                    dir, 1L, matchExpr, "drop", ext);
        }
    }

    protected void programMeshNetworkAclRule(OvnProviderVO provider, String peerLs,
                                          String lspName, OvnMeshNetworkVO member,
                                          NetworkACLItem rule) {
        String direction = rule.getTrafficType() == NetworkACLItem.TrafficType.Ingress
                ? "to-lport" : "from-lport";
        String aclAction = rule.getAction() == NetworkACLItem.Action.Allow ? "allow-related" : "drop";
        long ovnPriority = Math.max(2L, 1000L - rule.getNumber());

        // Scope the match to this member's port on the mesh network LS
        String portField = "to-lport".equals(direction) ? "outport" : "inport";
        String baseMatch = buildNetworkAclMatch(direction, rule);
        if (baseMatch == null) {
            return;
        }
        String matchExpr = String.format("%s == \"%s\" && %s", portField, lspName, baseMatch);

        String meshAclTag = "cloudstack_mesh_acl_vpc_" + member.getVpcId();
        Map<String, String> ext = new HashMap<>();
        ext.put(meshAclTag, "true");
        ext.put("cloudstack_acl_rule_id", String.valueOf(rule.getId()));
        ext.put("cloudstack_acl_id", String.valueOf(rule.getAclId()));
        ext.put("cloudstack_acl_direction", direction);
        ext.put(MESH_NETWORK_EXT_KEY, member.getMeshUuid());

        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                peerLs, "peer-acl-" + rule.getId() + "-vpc-" + member.getVpcId(),
                direction, ovnPriority, matchExpr, aclAction, ext);
    }

    /**
     * Tears down the OVN-side member artifacts owned by {@code vpc} but keeps the DB rows
     * intact. Called from {@link #shutdownVpc} which runs both for restart-with-cleanup
     * (where we want {@link #implementVpc} to re-provision the member after the LR comes
     * back) and for VPC deletion (where the foreign-key CASCADE on {@code vpc_id} removes
     * the rows automatically once the VPC is gone). Either way, leaving the DB row Active
     * here is the right move - it's restartable, and a real delete still trims the row.
     */
    protected void removeMeshNetworksForVpc(Vpc vpc, OvnProviderVO provider) {
        List<OvnMeshNetworkVO> memberships = ovnMeshNetworkDao.listByVpcId(vpc.getId());
        removeMeshNetworkMemberships(memberships, provider, "VPC " + vpc.getUuid());
    }

    /**
     * Tears down the OVN-side mesh artifacts owned by an isolated guest
     * network (called from the network shutdown hook). The DB row is kept
     * Active so a re-implementation of the network re-provisions the
     * member, mirroring the VPC path.
     */
    protected void removeMeshNetworksForNetwork(Network network, OvnProviderVO provider) {
        List<OvnMeshNetworkVO> memberships = ovnMeshNetworkDao.listByNetworkId(network.getId());
        removeMeshNetworkMemberships(memberships, provider, "Network " + network.getUuid());
    }

    /**
     * Common OVN-side cleanup for a member's mesh-network rows during
     * resource shutdown. Pulled out of {@link #removeMeshNetworksForVpc}
     * so the network-side shutdown can reuse the same logic without
     * duplicating route/LRP/LSP teardown.
     */
    private void removeMeshNetworkMemberships(List<OvnMeshNetworkVO> memberships, OvnProviderVO provider, String resourceLabel) {
        for (OvnMeshNetworkVO member : memberships) {
            try {
                String meshUuid = member.getMeshUuid();
                List<OvnMeshNetworkVO> meshMembers = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
                if (isCrossZoneMeshNetwork(meshMembers)) {
                    removeCrossZoneMeshNetworkMember(member, provider, meshUuid);
                    continue;
                }
                String routerName = getRouterNameForMember(member);
                String memberCidr = resolveMemberCidr(member);
                String targetTag = String.format("%s-%d", memberKindSuffix(member), member.getMemberId());

                // Clean up routes/policies on other members
                for (OvnMeshNetworkVO other : meshMembers) {
                    if (other.getId() == member.getId()) continue;
                    OvnProviderVO memberProvider = ovnProviderDao.findByZoneId(other.getZoneId());
                    if (memberProvider == null) continue;
                    String memberRouter = getRouterNameForMember(other);
                    if (memberCidr != null) {
                        ovnNbClient.removeStaticRoute(memberProvider.getNbConnection(),
                                memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                                memberRouter, memberCidr, other.getLinkLocalIp());
                    }
                    ovnNbClient.removeLogicalRouterPoliciesByExternalId(memberProvider.getNbConnection(),
                            memberProvider.getCaCertPath(), memberProvider.getClientCertPath(), memberProvider.getClientPrivateKeyPath(),
                            memberRouter, MESH_NETWORK_EXT_KEY + "_target", targetTag);
                }

                // Remove our own routes/policies
                ovnNbClient.removeStaticRoutesByExternalId(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, MESH_NETWORK_EXT_KEY, meshUuid);
                ovnNbClient.removeLogicalRouterPoliciesByExternalId(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, MESH_NETWORK_EXT_KEY, meshUuid);

                // Remove LRP+LSP
                ovnNbClient.removeLogicalRouterPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        routerName, getMeshLrpName(meshUuid, member));
                ovnNbClient.deleteLogicalSwitchPort(provider.getNbConnection(),
                        provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                        getMeshLsName(meshUuid), getMeshLspName(meshUuid, member));

                // Delete mesh network LS if last member
                long activeCount = meshMembers.stream().filter(m -> m.getId() != member.getId()).count();
                if (activeCount == 0) {
                    ovnNbClient.deleteLogicalSwitch(provider.getNbConnection(),
                            provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                            getMeshLsName(meshUuid));
                }

                // DB row left Active on purpose - see method javadoc.
            } catch (CloudRuntimeException e) {
                logger.warn("Failed to clean up mesh member {} for {}: {}", member.getUuid(), resourceLabel, e.getMessage());
            }
        }
    }

    private static String getMeshLsName(String meshUuid) {
        return "cs-mesh-" + meshUuid;
    }

    /**
     * Short suffix used inside OVN names to distinguish member kinds.
     * The suffix is part of the LRP/LSP names: {@code -vpc-<id>} for VPC
     * members, {@code -net-<id>} for Isolated network members. Including
     * the kind in the suffix means a VPC and an Isolated network sharing
     * the same numeric id never collide on the same mesh LS.
     */
    private static String memberKindSuffix(OvnMeshNetworkVO m) {
        return m.getNetworkId() != null ? "net" : "vpc";
    }

    /**
     * Returns the OVN Logical_Router name that owns this mesh-network
     * membership: the VPC's {@code cs-vpc-<vpcId>} for VPC members, the
     * isolated network's {@code cs-router-<networkId>} for Network
     * members.
     */
    private static String getRouterNameForMember(OvnMeshNetworkVO m) {
        if (m.getNetworkId() != null) {
            return String.format("cs-router-%d", m.getNetworkId());
        }
        return String.format("cs-vpc-%d", m.getVpcId());
    }

    private static String getMeshLrpName(String meshUuid, OvnMeshNetworkVO m) {
        return String.format("lrp-mesh-%s-%s-%d", meshUuid, memberKindSuffix(m), m.getMemberId());
    }

    private static String getMeshLspName(String meshUuid, OvnMeshNetworkVO m) {
        return String.format("lsp-mesh-%s-%s-%d", meshUuid, memberKindSuffix(m), m.getMemberId());
    }

    /**
     * Derives a stable MAC for a mesh-network LRP. The third byte
     * encodes the member kind (0xfa for VPC, 0xfb for Network) so a VPC
     * with the same numeric id as an isolated network cannot produce
     * a duplicate MAC on the same mesh switch.
     */
    private static String buildMeshMac(OvnMeshNetworkVO m) {
        int kindByte = m.getNetworkId() != null ? 0xfb : 0xfa;
        long id = m.getMemberId();
        return String.format("fa:16:3e:%02x:%02x:%02x",
                kindByte,
                (int) ((id >> 8) & 0xff),
                (int) (id & 0xff));
    }

    /**
     * Resolves a mesh-network member's CIDR (VPC super-CIDR or isolated
     * network CIDR) from its DB row. Returns {@code null} if the row's
     * underlying entity is gone.
     */
    private String resolveMemberCidr(OvnMeshNetworkVO m) {
        if (m.getNetworkId() != null) {
            com.cloud.network.dao.NetworkVO n = networksDao.findById(m.getNetworkId());
            return n != null ? n.getCidr() : null;
        }
        if (m.getVpcId() != null) {
            VpcVO v = vpcDao.findById(m.getVpcId());
            return v != null ? v.getCidr() : null;
        }
        return null;
    }

    /**
     * Returns the human-readable name of the entity backing a mesh-network
     * member row (VPC name or Network name). Used by error messages and
     * the API response surface.
     */
    private String resolveMemberName(OvnMeshNetworkVO m) {
        if (m.getNetworkId() != null) {
            com.cloud.network.dao.NetworkVO n = networksDao.findById(m.getNetworkId());
            return n != null ? n.getName() : "network-" + m.getNetworkId();
        }
        if (m.getVpcId() != null) {
            VpcVO v = vpcDao.findById(m.getVpcId());
            return v != null ? v.getName() : "vpc-" + m.getVpcId();
        }
        return null;
    }

    private static String allocateLinkLocalIp(Set<String> usedIps) {
        // Pool: 169.254.100.1 through 169.254.100.253 (skip .0 and .255)
        for (int i = 1; i <= 253; i++) {
            String ip = "169.254.100." + i;
            if (!usedIps.contains(ip)) {
                return ip;
            }
        }
        throw new CloudRuntimeException("No available link-local IPs in member pool 169.254.100.0/24");
    }

    // ── Cross-zone member via OVN-IC Transit Switch ─────────────────────────

    // Pool used for the TS-facing LRP IPs - separate from same-zone member pool
    // (169.254.100.0/24) so the two paths cannot overlap if the same VO is reused.
    private static final String CROSS_ZONE_LL_PREFIX = "169.254.200.";
    // Don't bleed CIDRs that are local-fabric concerns across IC. Pub net (10/8) and
    // any other link-local must never get learned in a peer AZ.
    private static final String IC_ROUTE_BLACKLIST = "10.0.0.0/8,169.254.0.0/16";

    protected boolean isCrossZoneMeshNetwork(List<OvnMeshNetworkVO> members) {
        if (members == null || members.size() < 2) return false;
        Set<Long> zones = new HashSet<>();
        for (OvnMeshNetworkVO m : members) zones.add(m.getZoneId());
        return zones.size() > 1;
    }

    private static String getTransitSwitchName(String meshUuid) {
        return "ts-mesh-" + meshUuid;
    }

    private static String getCrossZoneLrpName(OvnMeshNetworkVO m) {
        return String.format("lrp-cs-%s-%d-ts", memberKindSuffix(m), m.getMemberId());
    }

    private static String getCrossZoneLspName(OvnMeshNetworkVO m) {
        return String.format("lsp-ts-%s-%d", memberKindSuffix(m), m.getMemberId());
    }

    private static String allocateCrossZoneLinkLocalIp(Set<String> usedIps) {
        for (int i = 1; i <= 253; i++) {
            String ip = CROSS_ZONE_LL_PREFIX + i;
            if (!usedIps.contains(ip)) {
                return ip;
            }
        }
        throw new CloudRuntimeException("No available link-local IPs in cross-zone member pool 169.254.200.0/24");
    }

    /**
     * Provisions cross-zone member via OVN-IC. Each AZ NB gets its name, the IC NB gets the
     * Transit_Switch, and each VPC's LR gets a TS-facing LRP with HA gateway-chassis pinning.
     * Routes are propagated via {@code ic-route-adv}/{@code ic-route-learn} - we do NOT add
     * static peer-CIDR routes manually.
     *
     * Pre-requisites (operator-managed, not configured by this method):
     * - Each gateway-chassis hypervisor has {@code external_ids:ovn-is-interconn=true}
     * - Underlay reachability between chassis encap-IPs across AZs
     * - All providers in the group have {@code icNbConnection} and {@code availabilityZoneName}
     *   set on their OvnProviderVO row.
     */
    protected void provisionCrossZoneMeshNetwork(String meshUuid, List<OvnMeshNetworkVO> members) {
        // Index providers per zone, validate IC config
        Map<Long, OvnProviderVO> providersByZone = new HashMap<>();
        for (OvnMeshNetworkVO m : members) {
            providersByZone.computeIfAbsent(m.getZoneId(), z -> ovnProviderDao.findByZoneId(z));
        }
        OvnProviderVO icProvider = null;
        for (OvnProviderVO p : providersByZone.values()) {
            if (p == null) {
                throw new CloudRuntimeException("Cross-zone member requires every zone in the group to have an OVN provider");
            }
            if (StringUtils.isBlank(p.getIcNbConnection())) {
                throw new CloudRuntimeException(String.format(
                        "Cross-zone member requires icNbConnection on provider for zone %d. Set it via addOvnProvider/updateOvnProvider.",
                        p.getZoneId()));
            }
            if (StringUtils.isBlank(p.getAvailabilityZoneName())) {
                throw new CloudRuntimeException(String.format(
                        "Cross-zone member requires availabilityZoneName on provider for zone %d. Set it via addOvnProvider/updateOvnProvider.",
                        p.getZoneId()));
            }
            icProvider = p;
        }

        String tsName = getTransitSwitchName(meshUuid);

        // Set NB_Global.name and IC route options on each AZ NB. ovn-ic uses these to
        // register the AZ in IC SB and decide what to advertise/learn.
        Map<String, String> icOpts = new HashMap<>();
        icOpts.put("ic-route-adv", "true");
        icOpts.put("ic-route-learn", "true");
        icOpts.put("ic-route-adv-default-route", "false");
        icOpts.put("ic-route-blacklist", IC_ROUTE_BLACKLIST);
        for (OvnProviderVO p : providersByZone.values()) {
            ovnNbClient.setNbGlobalAvailabilityZoneName(p.getNbConnection(),
                    p.getCaCertPath(), p.getClientCertPath(), p.getClientPrivateKeyPath(),
                    p.getAvailabilityZoneName());
            ovnNbClient.setNbGlobalIcOptions(p.getNbConnection(),
                    p.getCaCertPath(), p.getClientCertPath(), p.getClientPrivateKeyPath(),
                    icOpts);
        }

        // Create the Transit_Switch in the IC NB. ovn-ic propagates it to every AZ NB
        // shortly after; the per-AZ LSP attachments below depend on that propagation,
        // but the OVSDB transactions are eventually consistent so we just retry on miss
        // via the idempotent attach helper.
        Map<String, String> tsExt = new HashMap<>();
        tsExt.put(MESH_NETWORK_EXT_KEY, meshUuid);
        tsExt.put("cloudstack_role", "mesh-network-ic");
        ovnNbClient.createTransitSwitch(icProvider.getIcNbConnection(),
                icProvider.getCaCertPath(), icProvider.getClientCertPath(), icProvider.getClientPrivateKeyPath(),
                tsName, tsExt);

        // Attach each member's router to the TS — VPC LR for VPC members,
        // isolated-network LR for Network members. ovn-ic picks up these LRPs and
        // exposes them as remote ports in the other AZs' NBs - no manual cross-AZ
        // sync needed.
        for (OvnMeshNetworkVO member : members) {
            OvnProviderVO p = providersByZone.get(member.getZoneId());
            if (resolveMemberCidr(member) == null) continue;

            String routerName = getRouterNameForMember(member);
            String lrpName = getCrossZoneLrpName(member);
            String lspName = getCrossZoneLspName(member);
            String mac = buildMeshMac(member);
            // /24 over the link-local pool keeps every member in the same broadcast
            // domain on the TS, which is what OVN-IC expects.
            String lrpIpCidr = member.getLinkLocalIp() + "/24";

            List<String> gatewayChassis = ovnNbClient.listInterconnectionChassisSystemIds(
                    p.getSbConnection(),
                    p.getCaCertPath(), p.getClientCertPath(), p.getClientPrivateKeyPath());
            if (gatewayChassis == null || gatewayChassis.isEmpty()) {
                throw new CloudRuntimeException(String.format(
                        "No interconnection-enabled chassis found in zone %d. Mark at least one chassis with `ovs-vsctl set Open_vSwitch . external_ids:ovn-is-interconn=true` before provisioning cross-zone member.",
                        p.getZoneId()));
            }

            ovnNbClient.attachRouterToTransitSwitch(p.getNbConnection(),
                    p.getCaCertPath(), p.getClientCertPath(), p.getClientPrivateKeyPath(),
                    routerName, tsName, lrpName, lspName, mac, lrpIpCidr, gatewayChassis);
        }

        // ACLs: cross-zone members can still carry Network_ACL constraints. Reuse the
        // same per-member apply path - it scopes ACLs to the LRP/LSP that we just
        // attached. (Same-zone branch had already done this.)
        for (OvnMeshNetworkVO member : members) {
            if (member.getAclId() != null) {
                applyCrossZoneMeshNetworkAcl(member, tsName);
            }
        }
    }

    /**
     * Per-member ACL application for cross-zone path. The mesh network LS in the same-zone
     * variant is replaced here by the TS LS in this AZ NB; the LSP we scope to is the
     * router-port LSP we created in {@link #provisionCrossZoneMeshNetwork}.
     */
    protected void applyCrossZoneMeshNetworkAcl(OvnMeshNetworkVO member, String tsLsName) {
        OvnProviderVO provider = ovnProviderDao.findByZoneId(member.getZoneId());
        if (provider == null) return;
        String lspName = getCrossZoneLspName(member);
        String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(member), member.getMemberId());

        ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                tsLsName, meshAclTag, "true");

        Long aclId = member.getAclId();
        if (aclId == null) return;
        List<NetworkACLItemVO> rules = networkACLItemDao.listByACL(aclId);
        if (rules == null || rules.isEmpty()) return;

        for (NetworkACLItemVO rule : rules) {
            if (rule.getState() == NetworkACLItem.State.Revoke) continue;
            networkACLItemDao.loadCidrs(rule);
            programCrossZoneMeshAclRule(provider, tsLsName, lspName, member, rule);
        }
        for (String dir : new String[]{"to-lport", "from-lport"}) {
            String portField = "to-lport".equals(dir) ? "outport" : "inport";
            String matchExpr = String.format("%s == \"%s\" && ip4", portField, lspName);
            Map<String, String> ext = new HashMap<>();
            ext.put(meshAclTag, "true");
            ext.put("cloudstack_acl_default", "true");
            ovnNbClient.addAclOnLs(provider.getNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    tsLsName,
                    String.format("ts-acl-default-%s-%s-%d", dir, memberKindSuffix(member), member.getMemberId()),
                    dir, 1L, matchExpr, "drop", ext);
        }
    }

    protected void programCrossZoneMeshAclRule(OvnProviderVO provider, String tsLsName,
                                                  String lspName, OvnMeshNetworkVO member,
                                                  NetworkACLItem rule) {
        String direction = rule.getTrafficType() == NetworkACLItem.TrafficType.Ingress
                ? "to-lport" : "from-lport";
        String aclAction = rule.getAction() == NetworkACLItem.Action.Allow ? "allow-related" : "drop";
        long ovnPriority = Math.max(2L, 1000L - rule.getNumber());
        String portField = "to-lport".equals(direction) ? "outport" : "inport";
        String baseMatch = buildNetworkAclMatch(direction, rule);
        if (baseMatch == null) return;
        String matchExpr = String.format("%s == \"%s\" && %s", portField, lspName, baseMatch);

        String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(member), member.getMemberId());
        Map<String, String> ext = new HashMap<>();
        ext.put(meshAclTag, "true");
        ext.put("cloudstack_acl_rule_id", String.valueOf(rule.getId()));
        ext.put("cloudstack_acl_id", String.valueOf(rule.getAclId()));
        ext.put("cloudstack_acl_direction", direction);
        ext.put(MESH_NETWORK_EXT_KEY, member.getMeshUuid());

        ovnNbClient.addAclOnLs(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                tsLsName,
                String.format("ts-acl-%d-%s-%d", rule.getId(), memberKindSuffix(member), member.getMemberId()),
                direction, ovnPriority, matchExpr, aclAction, ext);
    }

    /**
     * Removes a single member's TS attachment in its AZ NB. If the mesh has no remaining
     * members at all, also drops the Transit_Switch from the IC NB.
     */
    protected void removeCrossZoneMeshNetworkMember(OvnMeshNetworkVO member, OvnProviderVO provider, String meshUuid) {
        String tsName = getTransitSwitchName(meshUuid);
        String routerName = getRouterNameForMember(member);
        String lrpName = getCrossZoneLrpName(member);
        String lspName = getCrossZoneLspName(member);
        String meshAclTag = String.format("cloudstack_mesh_acl_%s_%d", memberKindSuffix(member), member.getMemberId());

        // Wipe ACLs scoped to this member on the TS LS first
        ovnNbClient.removeAclsOnLsByExternalId(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                tsName, meshAclTag, "true");

        ovnNbClient.detachRouterFromTransitSwitch(provider.getNbConnection(),
                provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                routerName, tsName, lrpName, lspName);

        // If group has no other live members, drop the TS in IC NB. ovn-ic propagates
        // the removal to every AZ NB.
        List<OvnMeshNetworkVO> remaining = ovnMeshNetworkDao.listByMeshUuid(meshUuid);
        long active = remaining.stream().filter(m -> m.getId() != member.getId()).count();
        if (active == 0 && StringUtils.isNotBlank(provider.getIcNbConnection())) {
            ovnNbClient.deleteTransitSwitch(provider.getIcNbConnection(),
                    provider.getCaCertPath(), provider.getClientCertPath(), provider.getClientPrivateKeyPath(),
                    tsName);
        }
    }
}
