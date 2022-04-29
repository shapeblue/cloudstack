# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
""" BVT tests for Network offerings"""

#Import Local Modules
from marvin.codes import FAILED
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import (createGuestNetworkIpv6Prefix,
                                  listGuestNetworkIpv6Prefixes,
                                  deleteGuestNetworkIpv6Prefix)
from marvin.lib.utils import (isAlmostEqual,
                              cleanup_resources,
                              random_gen,
                              get_process_status,
                              get_host_credentials)
from marvin.lib.base import (Configurations,
                             Domain,
                             NetworkOffering,
                             VpcOffering,
                             Account,
                             PublicIpRange,
                             Network,
                             VPC,
                             Router,
                             ServiceOffering,
                             VirtualMachine,
                             NIC,
                             Host,
                             NetworkACLList,
                             NetworkACL)
from marvin.lib.common import (get_domain,
                               get_zone,
                               list_hosts,
                               get_test_template,
                               get_template)
from marvin.sshClient import SshClient
from marvin.cloudstackException import CloudstackAPIException
from marvin.lib.decoratorGenerators import skipTestIf

from nose.plugins.attrib import attr
from ipaddress import IPv6Network
from random import getrandbits, choice, randint
import time
import logging

ipv6_offering_config_name = "ipv6.offering.enabled"
ULA_BASE = IPv6Network("fd00::/8")
PREFIX_OPTIONS = [i for i in range(48, 65, 4)]
ACL_TABLE = "ip6_acl"
ACL_CHAINS_SUFFIX = {
    "Ingress": "_ingress_policy",
    "Egress": "_egress_policy"
}
CIDR_IPV6_ANY = "::/0"
ICMPV6_TYPE = {
    1: "destination-unreachable",
    2: "packet-too-big",
    3: "time-exceeded",
    4: "parameter-problem",
    128: "echo-request",
    129: "echo-reply",
    130: "mld-listener-query",
    131: "mld-listener-report",
    132: "mld-listener-done",
    132: "mld-listener-reduction",
    133: "nd-router-solicit",
    134: "nd-router-advert",
    135: "nd-neighbor-solicit",
    136: "nd-neighbor-advert",
    137: "nd-redirect",
    138: "router-renumbering",
    141: "ind-neighbor-solicit",
    142: "ind-neighbor-advert",
    143: "mld2-listener-report"
}
ICMPV6_CODE_TYPE = {
    0: "no-route",
    1: "admin-prohibited",
    3: "addr-unreachable",
    4: "port-unreachable",
    5: "policy-fail",
    6: "reject-route"
}
ICMPV6_TYPE_ANY = "{ destination-unreachable, packet-too-big, time-exceeded, parameter-problem, echo-request, echo-reply, mld-listener-query, mld-listener-report, mld-listener-done, nd-router-solicit, nd-router-advert, nd-neighbor-solicit, nd-neighbor-advert, nd-redirect, router-renumbering }"
TCP_UDP_PORT_ANY = "{ 0-65535 }"
VPC_ROUTER_PUBLIC_NIC = "eth1"
VPC_ROUTER_GUEST_NIC = "eth2"
VPC_DATA = {
    "cidr": "10.1.0.0/22",
    "tier1_gateway": "10.1.1.1",
    "tier2_gateway": "10.1.2.1",
    "tier_netmask": "255.255.255.0"
}
ROUTE_TEST_VPC_DATA = {
    "cidr": "10.2.0.0/22",
    "tier1_gateway": "10.2.1.1",
    "tier_netmask": "255.255.255.0"
}


class TestIpv6Vpc(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        testClient = super(TestIpv6Vpc, cls).getClsTestClient()
        cls.services = testClient.getParsedTestDataConfig()
        cls.apiclient = testClient.getApiClient()
        cls.dbclient = testClient.getDbConnection()
        cls.test_ipv6_guestprefix = None
        cls.initial_ipv6_offering_enabled = None
        cls._cleanup = []
        cls.routerDetailsMap = {}
        cls.vpcAllowAllAclDetailsMap = {}

        cls.logger = logging.getLogger('TestIpv6Vpc')

        cls.zone = get_zone(cls.apiclient, testClient.getZoneForTests())
        cls.services['mode'] = cls.zone.networktype
        cls.ipv6NotSupported = False

        ipv6_guestprefix = cls.getGuestIpv6Prefix()
        if ipv6_guestprefix == None:
            cls.ipv6NotSupported = True
        if cls.ipv6NotSupported == False:
            ipv6_publiciprange = cls.getPublicIpv6Range()
            if ipv6_publiciprange == None:
                cls.ipv6NotSupported = True

        if cls.ipv6NotSupported == False:
            cls.initial_ipv6_offering_enabled = Configurations.list(
                cls.apiclient,
                name=ipv6_offering_config_name)[0].value
            Configurations.update(cls.apiclient,
                ipv6_offering_config_name,
                "true")
            cls.domain = get_domain(cls.apiclient)
            cls.account = Account.create(
                cls.apiclient,
                cls.services["account"],
                admin=True,
                domainid=cls.domain.id
            )
            cls._cleanup.append(cls.account)
            cls.hypervisor = testClient.getHypervisorInfo()
            cls.template = get_template(
                cls.apiclient,
                cls.zone.id,
                cls.services["ostype"]
            )
        else:
            cls.debug("IPv6 is not supported, skipping tests!")
        return

    @classmethod
    def tearDownClass(cls):
        if cls.initial_ipv6_offering_enabled != None:
            Configurations.update(cls.apiclient,
                ipv6_offering_config_name,
                cls.initial_ipv6_offering_enabled)
        super(TestIpv6Vpc, cls).tearDownClass()
        if cls.test_ipv6_guestprefix != None:
            cmd = deleteGuestNetworkIpv6Prefix.deleteGuestNetworkIpv6PrefixCmd()
            cmd.id = cls.test_ipv6_guestprefix.id
            cls.apiclient.deleteGuestNetworkIpv6Prefix(cmd)

    @classmethod
    def getGuestIpv6Prefix(cls):
        cmd = listGuestNetworkIpv6Prefixes.listGuestNetworkIpv6PrefixesCmd()
        cmd.zoneid = cls.zone.id
        ipv6_prefixes_response = cls.apiclient.listGuestNetworkIpv6Prefixes(cmd)
        if isinstance(ipv6_prefixes_response, list) == True and len(ipv6_prefixes_response) > 0:
            return ipv6_prefixes_response[0]
        ipv6_guestprefix_service = cls.services["guestip6prefix"]
        cmd = createGuestNetworkIpv6Prefix.createGuestNetworkIpv6PrefixCmd()
        cmd.zoneid = cls.zone.id
        cmd.prefix = ipv6_guestprefix_service["prefix"]
        ipv6_guestprefix = cls.apiclient.createGuestNetworkIpv6Prefix(cmd)
        cls.test_ipv6_guestprefix = ipv6_guestprefix
        return ipv6_guestprefix

    @classmethod
    def getPublicIpv6Range(cls):
        list_public_ip_range_response = PublicIpRange.list(
            cls.apiclient,
            zoneid=cls.zone.id
        )
        ipv4_range_vlan = None
        if isinstance(list_public_ip_range_response, list) == True and len(list_public_ip_range_response) > 0:
            for ip_range in list_public_ip_range_response:
                if ip_range.ip6cidr != None and ip_range.ip6gateway != None:
                    return ip_range
                if ip_range.netmask != None and ip_range.gateway != None:
                    vlan = ip_range.vlan
                    if ipv4_range_vlan == None and vlan.startswith("vlan://"):
                        vlan = vlan.replace("vlan://", "")
                        ipv4_range_vlan = int(vlan)
        ipv6_publiciprange_service = cls.services["publicip6range"]
        ipv6_publiciprange_service["zoneid"] = cls.zone.id
        ipv6_publiciprange_service["vlan"] = ipv4_range_vlan
        ipv6_publiciprange = PublicIpRange.create(
            cls.apiclient,
            ipv6_publiciprange_service
        )
        cls._cleanup.append(ipv6_publiciprange)
        return ipv6_publiciprange

    def setUp(self):
        self.services = self.testClient.getParsedTestDataConfig()
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []
        return

    def tearDown(self):
        try:
            #Clean up, terminate the created templates
            cleanup_resources(self.apiclient, reversed(self.cleanup))

        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def getRandomIpv6Cidr(self):
        prefix_length = choice(PREFIX_OPTIONS)
        random_suffix = getrandbits(40) << (128-prefix_length)
        base_address = ULA_BASE.network_address + random_suffix
        return str(IPv6Network((base_address, prefix_length)))

    def createTinyServiceOffering(self):
        self.service_offering = ServiceOffering.create(
            self.apiclient,
            self.services["service_offerings"]["big"],
        )
        self.cleanup.append(self.service_offering)

    def createVpcOfferingInternal(self, is_redundant, is_ipv6):
        off_service = self.services["vpc_offering"]
        if is_redundant:
            off_service["serviceCapabilityList"] = {
                "SourceNat": {
                    "RedundantRouter": 'true'
                },
            }
        if is_ipv6:
            off_service["internetprotocol"] = "dualstack"
        vpc_offering = VpcOffering.create(
            self.apiclient,
            off_service
        )
        vpc_offering.update(self.apiclient, state='Enabled')
        return vpc_offering

    def createIpv4VpcOffering(self, is_redundant=False):
        self.vpc_offering = self.createVpcOfferingInternal(is_redundant, False)
        self.cleanup.append(self.vpc_offering)

    def createIpv6VpcOffering(self, is_redundant=False):
        self.vpc_offering = self.createVpcOfferingInternal(is_redundant, True)
        self.cleanup.append(self.vpc_offering)

    def createIpv6VpcOfferingForUpdate(self, is_redundant=False):
        self.vpc_offering_update = self.createVpcOfferingInternal(is_redundant, True)
        self.cleanup.append(self.vpc_offering_update)

    def createNetworkTierOfferingInternal(self, is_ipv6):
        off_service = self.services["nw_offering_isolated_vpc"]
        if is_ipv6:
            off_service["internetprotocol"] = "dualstack"
        network_offering = NetworkOffering.create(
            self.apiclient,
            off_service,
            conservemode=False
        )
        network_offering.update(self.apiclient, state='Enabled')
        return network_offering

    def createIpv4NetworkTierOffering(self, is_redundant=False):
        self.network_offering = self.createNetworkTierOfferingInternal(False)
        self.cleanup.append(self.network_offering)

    def createIpv6NetworkTierOffering(self, is_redundant=False):
        self.network_offering = self.createNetworkTierOfferingInternal(True)
        self.cleanup.append(self.network_offering)

    def createIpv6NetworkTierOfferingForUpdate(self, is_redundant=False):
        self.network_offering_update = self.createNetworkTierOfferingInternal(True)
        self.cleanup.append(self.network_offering_update)

    def deployAllowAllVpcInternal(self, cidr):
        service = self.services["vpc"]
        service["cidr"] = cidr
        vpc = VPC.create(
            self.apiclient,
            self.services["vpc"],
            vpcofferingid=self.vpc_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid
        )
        acl = NetworkACLList.create(
            self.apiclient,
            services={},
            name="allowall",
            description="allowall",
            vpcid=vpc.id
        )
        rule ={
            "protocol": "all",
            "traffictype": "ingress",
        }
        NetworkACL.create(self.apiclient,
            # networkid=None,
            services=rule,
            aclid=acl.id
        )
        rule["traffictype"] = "egress"
        NetworkACL.create(self.apiclient,
            networkid=None,
            services=rule,
            aclid=acl.id
        )
        self.vpcAllowAllAclDetailsMap[vpc.id] = acl.id
        return vpc

    def deployVpc(self):
        self.vpc = self.deployAllowAllVpcInternal(VPC_DATA["cidr"])
        self.cleanup.append(self.vpc)

    def deployNetworkTierInternal(self, network_offering_id, vpc_id, tier_gateway, tier_netmask):
        acl_id = None
        if vpc_id in self.vpcAllowAllAclDetailsMap:
            acl_id = self.vpcAllowAllAclDetailsMap[vpc_id]
        network = Network.create(
            self.apiclient,
            self.services["ntwk"],
            self.account.name,
            self.account.domainid,
            networkofferingid=network_offering_id,
            vpcid=vpc_id,
            zoneid=self.zone.id,
            gateway=tier_gateway,
            netmask=tier_netmask,
            aclid=acl_id
        )
        return network

    def deployNetworkTier(self):
        self.network = self.deployNetworkTierInternal(
            self.network_offering.id,
            self.vpc.id,
            VPC_DATA["tier1_gateway"],
            VPC_DATA["tier_netmask"]
        )
        self.cleanup.append(self.network)

    def deployNetworkTierVm(self):
        if self.template == FAILED:
            assert False, "get_test_template() failed to return template"
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.virtual_machine = VirtualMachine.create(
            self.apiclient,
            self.services["virtual_machine"],
            templateid=self.template.id,
            accountid=self.account.name,
            domainid=self.account.domainid,
            networkids=self.network.id,
            serviceofferingid=self.service_offering.id
        )
        self.cleanup.append(self.virtual_machine)

    def checkIpv6Vpc(self):
        self.debug("Listing VPC: %s" % (self.vpc.name))
        ipv6_vpc = VPC.list(self.apiclient,listall="true",id=self.vpc.id)
        self.assertTrue(
            isinstance(ipv6_vpc, list),
            "Check listVpcs response returns a valid list"
        )
        self.assertEqual(
            len(ipv6_vpc),
            1,
            "Network not found"
        )
        ipv6_vpc = ipv6_vpc[0]
        self.assertNotEqual(ipv6_vpc.ip6routes,
                    None,
                    "IPv6 routes for network is empty")

    def checkIpv6NetworkTierBasic(self):
        self.debug("Listing network: %s" % (self.network.name))
        ipv6_network = Network.list(self.apiclient,listall="true",id=self.network.id)
        self.assertTrue(
            isinstance(ipv6_network, list),
            "Check listNetworks response returns a valid list"
        )
        self.assertEqual(
            len(ipv6_network),
            1,
            "Network not found"
        )
        ipv6_network = ipv6_network[0]
        self.assertNotEqual(ipv6_network,
                    None,
                    "User is not able to retrieve network details %s" % self.network.id)
        self.assertNotEqual(ipv6_network.ip6cidr,
                    None,
                    "IPv6 CIDR for network is empty")
        self.assertNotEqual(ipv6_network.ip6gateway,
                    None,
                    "IPv6 gateway for network is empty")
        self.assertNotEqual(ipv6_network.ip6routes,
                    None,
                    "IPv6 routes for network is empty")

    def checkIpv6VpcRoutersBasic(self):
        self.debug("Listing routers for VPC: %s" % self.vpc.name)
        self.routers = Router.list(
            self.apiclient,
            vpcid=self.vpc.id,
            listall=True
        )
        self.assertTrue(
            isinstance(self.routers, list),
            "Check listRouters response returns a valid list"
        )
        self.assertTrue(
            len(self.routers) > 0,
            "Router for the network isn't found"
        )
        for router in self.routers:
            self.assertFalse(
                router.isredundantrouter == True and router.redundantstate == "FAULT",
                "Router for the network is in FAULT state"
            )
            nics = router.nic
            for nic in nics:
                if (nic.traffictype == 'Guest' and router.isredundantrouter == False) or nic.traffictype == 'Public':
                    self.assertNotEqual(nic.ip6address,
                                None,
                                "IPv6 address for router %s NIC is empty" % nic.traffictype)
                    self.assertNotEqual(nic.ip6cidr,
                                None,
                                "IPv6 CIDR for router %s NIC is empty" % nic.traffictype)
                    self.assertNotEqual(nic.ip6gateway,
                                None,
                                "IPv6 gateway for router %s NIC is empty" % nic.traffictype)


    def getRouterProcessStatus(self, router, cmd):
        if router.id not in self.routerDetailsMap or self.routerDetailsMap[router.id] is None:
            connect_ip = self.apiclient.connection.mgtSvr
            connect_user = self.apiclient.connection.user
            connect_passwd = self.apiclient.connection.passwd
            hypervisor = self.hypervisor
            if self.hypervisor.lower() not in ('vmware', 'hyperv'):
                hosts = Host.list(
                    self.apiclient,
                    zoneid=router.zoneid,
                    type='Routing',
                    state='Up',
                    id=router.hostid
                )
                self.assertEqual(
                    isinstance(hosts, list),
                    True,
                    "Check list host returns a valid list"
                )
                host = hosts[0]
                connect_ip = host.ipaddress
                hypervisor = None
                try:
                    connect_user, connect_passwd= get_host_credentials(
                        self.config, host.ipaddress)
                except KeyError:
                    self.skipTest(
                        "Marvin configuration has no host credentials to\
                                check router services")
            details = {}
            details['connect_ip'] = connect_ip
            details['connect_user'] = connect_user
            details['connect_passwd'] = connect_passwd
            details['hypervisor'] = hypervisor
            self.routerDetailsMap[router.id] = details
        result = get_process_status(
            self.routerDetailsMap[router.id]['connect_ip'],
            22,
            self.routerDetailsMap[router.id]['connect_user'],
            self.routerDetailsMap[router.id]['connect_passwd'],
            router.linklocalip,
            cmd,
            hypervisor=self.routerDetailsMap[router.id]['hypervisor']
        )
        self.assertTrue(type(result) == list and len(result) > 0,
            "%s on router %s returned invalid result" % (cmd, router.id))
        result = '\n'.join(result)
        return result

    def getVpcRouter(self, vpc, red_state="PRIMARY"):
        routers = Router.list(
            self.apiclient,
            vpcid=vpc.id,
            listall=True
        )
        self.assertTrue(
            isinstance(routers, list) and len(routers) > 0,
            "No routers found for VPC %s" % vpc.id
        )
        if len(routers) == 1:
            return routers[0]
        for router in routers:
            if router.redundantstate == red_state:
                return router

    def getNetworkGateway(self, network):
        ipv6_network = Network.list(self.apiclient,listall="true",id=network.id)
        self.assertTrue(
            isinstance(ipv6_network, list),
            "Check listNetworks response returns a valid list"
        )
        self.assertEqual(
            len(ipv6_network),
            1,
            "Network not found"
        )
        ipv6_network = ipv6_network[0]
        self.assertNotEqual(ipv6_network.ip6gateway,
                    None,
                    "IPv6 gateway for network is empty")
        return ipv6_network.ip6gateway

    def getNetworkRoutes(self, network):
        ipv6_network = Network.list(self.apiclient,listall="true",id=network.id)
        self.assertTrue(
            isinstance(ipv6_network, list),
            "Check listNetworks response returns a valid list"
        )
        self.assertEqual(
            len(ipv6_network),
            1,
            "Network not found"
        )
        ipv6_network = ipv6_network[0]
        self.assertNotEqual(ipv6_network.ip6routes,
                    None,
                    "IPv6 routes for network is empty")
        return ipv6_network.ip6routes

    def isNetworkEgressDefaultPolicyAllow(self, network):
        ipv6_network = Network.list(self.apiclient,listall="true",id=network.id)
        if len(ipv6_network) == 1:
            ipv6_network = ipv6_network[0]
            return ipv6_network.egressdefaultpolicy
        return False

    def checkRouterNicState(self, router, dev, state):
        st = "state %s" % state
        cmd = "ip link show %s | grep '%s'" % (dev, st)
        res = self.getRouterProcessStatus(router, cmd)
        self.assertTrue(type(res) == str and len(res) > 0 and st in res,
            "%s failed on router %s" % (cmd, router.id))

    def checkIpv6VpcPrimaryRouter(self, router, network_ip6gateway):
        self.checkRouterNicState(router, VPC_ROUTER_GUEST_NIC, "UP")
        guest_gateway_check_cmd = "ip -6 address show %s | grep 'inet6 %s'" % (VPC_ROUTER_GUEST_NIC, network_ip6gateway)
        res = self.getRouterProcessStatus(router, guest_gateway_check_cmd)
        self.assertTrue(type(res) == str and len(res) > 0 and network_ip6gateway in res,
            "%s failed on router %s" % (guest_gateway_check_cmd, router.id))
        self.assertFalse("dadfailed" in res,
            "dadfailed for IPv6 guest gateway on router %s" % router.id)
        self.checkRouterNicState(router, VPC_ROUTER_PUBLIC_NIC, "UP")
        public_ipv6 = None
        public_ipv6_gateway = None
        nics = router.nic
        for nic in nics:
            if nic.traffictype == 'Public':
                public_ipv6 = nic.ip6address
                public_ipv6_gateway = nic.ip6gateway
                break
        self.assertNotEqual(public_ipv6,
            None,
            "IPv6 address for router Public NIC is empty")
        public_ip_check_cmd = "ip -6 address show %s | grep 'inet6 %s'" % (VPC_ROUTER_PUBLIC_NIC, public_ipv6)
        res = self.getRouterProcessStatus(router, public_ip_check_cmd)
        self.assertTrue(type(res) == str and len(res) > 0 and public_ipv6 in res,
            "%s failed on router %s" % (public_ip_check_cmd, router.id))
        self.assertFalse("dadfailed" in res,
            "dadfailed for public IPv6 on router %s" % router.id)
        self.assertNotEqual(public_ipv6_gateway,
            None,
            "IPv6 gateway for router Public NIC is empty")
        default_route_check_cmd = "ip -6 route | grep 'default via %s'" % (public_ipv6_gateway)
        res = self.getRouterProcessStatus(router, default_route_check_cmd)
        self.assertTrue(type(res) == str and len(res) > 0 and public_ipv6_gateway in res,
            "%s failed on router %s" % (default_route_check_cmd, router.id))

    def checkIpv6NetworkBackupRouter(self, router, network_ip6gateway):
        self.checkRouterNicState(router, VPC_ROUTER_GUEST_NIC, "UP")
        guest_gateway_check_cmd = "ip -6 address show %s | grep 'inet6 %s'" % ("eth0", network_ip6gateway)
        res = self.getRouterProcessStatus(router, guest_gateway_check_cmd)
        self.assertFalse(type(res) == str and len(res) > 0 and network_ip6gateway in res,
            "%s failed on router %s" % (guest_gateway_check_cmd, router.id))
        self.checkRouterNicState(router, VPC_ROUTER_PUBLIC_NIC, "DOWN")

    def checkIpv6VpcRoutersInternal(self):
        network_ip6gateway = self.getNetworkGateway(self.network)
        for router in self.routers:
            if router.state != "Running":
                continue
            if router.isredundantrouter == True and router.redundantstate == 'BACKUP':
                self.checkIpv6VpcBackupRouter(router, network_ip6gateway)
                continue
            self.checkIpv6VpcPrimaryRouter(router, network_ip6gateway)


    def checkIpv6NetworkTierVm(self):
        self.debug("Listing NICS for VM %s in network tier: %s" % (self.virtual_machine.name, self.network.name))
        nics = NIC.list(
            self.apiclient,
            virtualmachineid=self.virtual_machine.id,
            networkid=self.network.id
        )
        self.assertEqual(
            len(nics),
            1,
            "VM NIC for the network tier isn't found"
        )
        nic = nics[0]
        self.assertNotEqual(nic.ip6address,
                    None,
                    "IPv6 address for VM %s NIC is empty" % nic.traffictype)
        self.virtual_machine_ipv6_address = nic.ip6address
        self.assertNotEqual(nic.ip6cidr,
                    None,
                    "IPv6 CIDR for VM %s NIC is empty" % nic.traffictype)
        self.assertNotEqual(nic.ip6gateway,
                    None,
                    "IPv6 gateway for VM %s NIC is empty" % nic.traffictype)

    def restartVpckWithCleanup(self):
        self.vpc.restart(self.apiclient, cleanup=True)

    def updateNetworkTierWithOffering(self):
        self.vpc.update(self.apiclient, networkofferingid=self.network_offering_update.id)

    def checkVpcRouting(self):
        self.routing_test_vpc = self.deployAllowAllVpcInternal(ROUTE_TEST_VPC_DATA["cidr"])
        self.cleanup.append(self.routing_test_vpc)
        self.routing_test_network_offering = self.createNetworkTierOfferingInternal(True)
        self.cleanup.append(self.routing_test_network_offering)
        self.routing_test_network = self.deployNetworkTierInternal(
            self.routing_test_network_offering.id,
            self.routing_test_vpc.id,
            ROUTE_TEST_VPC_DATA["tier1_gateway"],
            ROUTE_TEST_VPC_DATA["tier_netmask"]
        )
        self.cleanup.append(self.routing_test_network)
        self.services["virtual_machine"]["zoneid"] = self.zone.id
        self.routing_test_vm = VirtualMachine.create(
            self.apiclient,
            self.services["virtual_machine"],
            templateid=self.template.id,
            accountid=self.account.name,
            domainid=self.account.domainid,
            networkids=[self.routing_test_network.id],
            serviceofferingid=self.service_offering.id,
            mode="advanced",
            vpcid=self.routing_test_vpc.id
        )
        self.cleanup.append(self.routing_test_vm)

        router = self.getVpcRouter(self.routing_test_vpc)
        routes = self.getNetworkRoutes(self.network)
        self.logger.debug("Adding vpc routes in routing_test_vpc %s" % routes)
        for route in routes:
            add_route_cmd = "ip -6 route add %s via %s" % (route.subnet, route.gateway)
            self.getRouterProcessStatus(router, add_route_cmd)

        router = self.getVpcRouter(self.vpc)
        routes = self.getNetworkRoutes(self.routing_test_network)
        self.logger.debug("Adding routing_test_vpc routes in vpc %s" % routes)
        for route in routes:
            add_route_cmd = "ip -6 route add %s via %s" % (route.subnet, route.gateway)
            self.getRouterProcessStatus(router, add_route_cmd)

        time.sleep(self.services["sleep"])

        ping_cmd = "ping6 -c 4 %s" % self.virtual_machine_ipv6_address
        res = self.getRouterProcessStatus(router, ping_cmd)
        self.assertTrue(" 0% packet loss" in res,
            "Ping from router %s of VPC %s to VM %s of VPC %s is unsuccessful" % (router.id, self.routing_test_vpc.id, self.virtual_machine.id, self.vpc.id))

        ssh = self.routing_test_vm.get_ssh_client(retries=5)
        res = ssh.execute(ping_cmd)
        self.assertTrue(type(res) == list and len(res) > 0,
            "%s on VM %s returned invalid result" % (ping_cmd, self.routing_test_vm.id))
        self.logger.debug(res)
        res = '\n'.join(res)
        self.assertTrue(" 0% packet loss" in res,
            "Ping from VM %s of VPC %s to VM %s of VPC %s is unsuccessful" % (self.routing_test_vm.id, self.routing_test_vpc.id, self.virtual_machine.id, self.vpc.id))

    def checkIpv6AclRule(self):
        return

    def checkVpcVRRedundancy(self):
        network_ip6gateway = self.getNetworkGateway(self.network)
        primary_router = self.getVpcRouter(self.vpc)
        Router.stop(
            self.apiclient,
            id=primary_router.id
        )
        time.sleep(self.services["sleep"]/2)
        new_primary_router = self.getNetworkRouter(self.vpc)
        self.assertNotEqual(new_primary_router.id, primary_router.id,
            "Original primary router ID: %s of VPC is still the primary router after stopping" % (primary_router.id))
        print(new_primary_router)
        self.checkIpv6VpcPrimaryRouter(new_primary_router, network_ip6gateway)

    @attr(
        tags=[
            "advanced",
            "basic",
            "eip",
            "sg",
            "advancedns",
            "smoke"],
        required_hardware="false")
    @skipTestIf("ipv6NotSupported")
    def test_01_verify_ipv6_vpc(self):
        """Test to verify IPv6 VPC

        # Validate the following:
        # 1. Create IPv6 VPC, add tiers, deploy VM
        # 2. Verify VPC, tier has required IPv6 details
        # 3. List router for the VPC and verify it has required IPv6 details for Guest and Public NIC of the VR
        # 4. SSH into VR(s) and verify correct details are present for its NICs
        # 5. Verify VM in network tier has required IPv6 details
        # 6. Restart VPC with cleanup
        # 7. Update network tier with a new offering
        # 8. Again verify network tier and VR details
        # 9. Deploy another IPv6 VPC with tier and check routing between two VPC and their VM
        # 10. Create IPv6 ACL rules and verify in VR if they get implemented
        """

        self.createIpv6VpcOffering()
        self.deployVpc()
        self.createIpv6NetworkTierOffering()
        self.createIpv6NetworkTierOfferingForUpdate()
        self.createTinyServiceOffering()
        self.deployNetworkTier()
        self.deployNetworkTierVm()
        self.checkIpv6Vpc()
        self.checkIpv6NetworkTierBasic()
        self.checkIpv6VpcRoutersBasic()
        # self.checkIpv6VpcRoutersInternal()
        self.checkIpv6NetworkTierVm()
        # self.restartVpcWithCleanup()
        # self.updateNetworkTierWithOffering()
        # self.checkIpv6NetworkTierBasic()
        # self.checkIpv6NetworkTierRoutersBasic()
        self.checkVpcRouting()
        # self.checkIpv6AclRule()
