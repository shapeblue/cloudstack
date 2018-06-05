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
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import remoteDiganostics
from marvin.lib.utils import (cleanup_resources)
from marvin.lib.base import (Account,
                             ServiceOffering,
                             VirtualMachine)
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_test_template,
                               list_ssvms,
                               list_routers)

from nose.plugins.attrib import attr

class TestRemoteDiagnosticsInSystemVMs(cloudstackTestCase):
    """
    Test remote diagnostics with system VMs as root admin
    """
    @classmethod
    def setUpClass(cls):

        testClient = super(TestRemoteDiagnosticsInSystemVMs, cls).getClsTestClient()
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
                TestRemoteDiagnosticsInSystemVMs,
                cls
            ).getClsTestClient().getApiClient()
            # Clean up, terminate the created templates
            cleanup_resources(cls.apiclient, cls.cleanup)

        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.hypervisor = self.testClient.getHypervisorInfo()
        return

    def tearDown(self):
        pass
    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_01_ping_in_system_vm(self):
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

        cmd = remoteDiganostics.remoteDiganosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = "dkdlglglsmndhjfgke;"
        cmd.type = "ping"
        cmd_response = self.apiclient.remoteDiganostics(cmd);

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Ping command not executed successfully")

    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_02_traceroute_in_system_vm(self):
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

        cmd = remoteDiganostics.remoteDiganosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = "dkdlglglsmndhjfgke;"
        cmd.type = "ping"
        cmd_response = self.apiclient.remoteDiganostics(cmd);

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Ping command not executed successfully")

    @attr(tags=["advanced", "advancedns", "ssh", "smoke"], required_hardware="true")
    def test_03_arping_in_system_vm(self):
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

        cmd = remoteDiganostics.remoteDiganosticsCmd()
        cmd.id = router.id
        cmd.ipaddress = "dkdlglglsmndhjfgke;"
        cmd.type = "ping"
        cmd_response = self.apiclient.remoteDiganostics(cmd);

        self.assertEqual(True,
                         cmd_response.success,
                         msg="Ping command not executed successfully")

