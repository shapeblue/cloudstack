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
""" BVT tests for remote diagnostics of system VMs
"""
# Import Local Modules
from marvin.codes import FAILED
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import executeDiagnostics
from marvin.lib.utils import (cleanup_resources)
from marvin.lib.base import (Account,
                             ServiceOffering,
                             VirtualMachine)
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_test_template,
                               list_ssvms,
                               list_routers,
                               list_hosts)

from nose.plugins.attrib import attr

class TestRemoteDiagnostics(cloudstackTestCase):
    """
    Test remote diagnostics with system VMs as root admin
    """
    @classmethod
    def setUpClass(cls):

        testClient = super(TestRemoteDiagnostics, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        cls.services = testClient.getParsedTestDataConfig()

        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.apiclient)
        cls.zone = get_zone(cls.apiclient, testClient.getZoneForTests())
        cls.hypervisor = testClient.getHypervisorInfo()
        cls.services['mode'] = cls.zone.networktype
        template = get_test_template(
            cls.apiclient,
            cls.zone.id,
            cls.hypervisor
        )
        if template == FAILED:
            cls.fail("get_test_template() failed to return template")

        cls.services["virtual_machine"]["zoneid"] = cls.zone.id

        # Create an account, network, VM and IP addresses
        cls.account = Account.create(
            cls.apiclient,
            cls.services["account"],
            domainid=cls.domain.id
        )
        cls.service_offering = ServiceOffering.create(
            cls.apiclient,
            cls.services["service_offerings"]["tiny"]
        )
        cls.vm_1 = VirtualMachine.create(
            cls.apiclient,
            cls.services["virtual_machine"],
            templateid=template.id,
            accountid=cls.account.name,
            domainid=cls.account.domainid,
            serviceofferingid=cls.service_offering.id
        )
        cls.cleanup = [
            cls.account,
            cls.service_offering
        ]
        return

    @classmethod
    def tearDownClass(cls):
        try:
            cls.apiclient = super(
                TestRemoteDiagnostics,
                cls
            ).getClsTestClient().getApiClient()
            # Clean up, terminate the created templates
            cleanup_resources(cls.apiclient, cls.cleanup)

        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)


    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.hypervisor = self.testClient.getHypervisorInfo()


    def tearDown(self):
        pass

    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_01_ping_in_system_vm(self):

        # Test with VR
        list_router_response = list_routers(
            self.apiclient
        )
        self.assertEqual(
            isinstance(list_router_response, list),
            True,
            "Check list response returns a valid list"
        )
        router = list_router_response[0]
        self.debug("Starting the router with ID: %s" %router.id)

        cmd = executeDiagnostics.executeDiagnosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = "8.8.8.8;"
        cmd.type = "ping"
        cmd_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Failed to exeute remote Ping command in VR"
                         )

        # Test with SSVM
        list_ssvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='secondarystoragevm',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_ssvm_response, list),
            True,
            "Check list response returns a valid list"
        )
        ssvm = list_ssvm_response[0]

        self.debug("Setting up SSVM with ID %s" %ssvm.id)
        cmd.id = ssvm.id
        ssvm_response = self.apiclient.remoteDiganostics(cmd)

        self.assertEqual(True,
                         ssvm_response.success,
                         msg="Failed to execute remote Ping in SSVM"
                         )

        # Test with CPVM
        list_cpvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='consoleproxy',
            state='Running',
        )
        self.assertEqual(
            isinstance(list_cpvm_response, list),
                        True,
                        "Check list response returns a valid list"
                        )
        cpvm = list_cpvm_response[0]

        self.debug("Setting up CPVM with ID %s" %cpvm.id)
        cmd.id = cpvm.id
        cpvm_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                     cpvm_response.success,
                     msg="Failed to execute remote Ping in CPVM"
                         )

    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_02_traceroute_in_system_vm(self):

        # Test with VR
        list_router_response = list_routers(
            self.apiclient
        )
        self.assertEqual(
            isinstance(list_router_response, list),
            True,
            "Check list response returns a valid list"
        )
        router = list_router_response[0]
        self.debug("Starting the router with ID: %s" %router.id)

        cmd = executeDiagnostics.executeDiagnosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = "8.8.8.8;"
        cmd.type = "traceroute"
        cmd_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Failed to exeute remote Traceroute command in VR"
                         )

        # Test with SSVM
        list_ssvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='secondarystoragevm',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_ssvm_response, list),
            True,
            "Check list response returns a valid list"
        )
        ssvm = list_ssvm_response[0]

        self.debug("Setting up SSVM with ID %s" %ssvm.id)
        cmd.id = ssvm.id
        ssvm_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         ssvm_response.success,
                         msg="Failed to execute remote Traceroute in SSVM"
                         )

        # Test with CPVM
        list_cpvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='consoleproxy',
            state='Running',
        )
        self.assertEqual(
            isinstance(list_cpvm_response, list),
            True,
            "Check list response returns a valid list"
        )
        cpvm = list_cpvm_response[0]

        self.debug("Setting up CPVM with ID %s" %cpvm.id)
        cmd.id = cpvm.id
        cpvm_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         cpvm_response.success,
                         msg="Failed to execute remote Traceroute in CPVM"
                         )


    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_03_arping_in_system_vm(self):

        # Test with VR
        list_router_response = list_routers(
            self.apiclient
        )
        self.assertEqual(
            isinstance(list_router_response, list),
            True,
            "Check list response returns a valid list"
        )

        router = list_router_response[0]
        self.debug("Starting the router with ID: %s" %router.id)

        hosts = list_hosts(
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

        cmd = executeDiagnostics.executeDiagnosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = host.ipaddress
        cmd.type = "arping"
        cmd.params = "-I eth0 -c 4"
        cmd_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Failed to exeute remote Arping command in VR"
                         )

        # Test with SSVM
        list_ssvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='secondarystoragevm',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_ssvm_response, list),
            True,
            "Check list response returns a valid list"
        )
        ssvm = list_ssvm_response[0]

        self.debug("Setting up SSVM with ID %s" %ssvm.id)
        cmd.id = ssvm.id
        ssvm_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         ssvm_response.success,
                         msg="Failed to execute remote Arping in SSVM"
                         )

        # Test with CPVM
        list_cpvm_response = list_ssvms(
            self.apiclient,
            systemvmtype='consoleproxy',
            state='Running',
        )
        self.assertEqual(
            isinstance(list_cpvm_response, list),
            True,
            "Check list response returns a valid list"
        )
        cpvm = list_cpvm_response[0]

        self.debug("Setting up CPVM with ID %s" %cpvm.id)
        cmd.id = cpvm.id
        cpvm_response = self.apiclient.executeDiagnostics(cmd)

        self.assertEqual(True,
                         cpvm_response.success,
                         msg="Failed to execute remote Arping in CPVM"
                         )