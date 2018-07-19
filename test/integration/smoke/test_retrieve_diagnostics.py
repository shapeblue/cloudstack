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
""" BVT tests for retrieve diagnostics of system VMs
"""
# Import Local Modules
from marvin.codes import FAILED
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import retrieveDiagnostics
from marvin.lib.utils import (cleanup_resources)
from marvin.lib.base import (Account,
                             ServiceOffering,
                             VirtualMachine,
                             Configurations)
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_test_template,
                               list_ssvms,
                               list_routers)

from nose.plugins.attrib import attr

class TestRetrieveDiagnostics(cloudstackTestCase):
    """
    Test retrieve diagnostics with system VMs and VR as root admin
    """

    @classmethod
    def setUpClass(cls):

        testClient = super(TestRetrieveDiagnostics, cls).getClsTestClient()
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
            cls.service_offering,
            cls.vm_1
        ]

    @classmethod
    def tearDownClass(cls):
        try:
            cls.apiclient = super(
                TestRetrieveDiagnostics,
                cls
            ).getClsTestClient().getApiClient()
            # Clean up, terminate the created templates
            cleanup_resources(cls.apiclient, cls.cleanup)

        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.dbClient = self.testClient.getDbConnection()
        self.hypervisor = self.testClient.getHypervisorInfo()

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_ssvm_retrieve_files_success(self):
        list_ssvm_response = list_ssvms(
            self.apiclient,
            systemvmId='secondarystoragevm',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_ssvm_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_ssvm_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "LOGFILES"
        cmd.details = "/var/log/cloudstack/agent/agent.log,/var/log/yum.log,/var/log/cloudstack/agent/security_group.log"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'url')

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_router_retrieve_files_success(self):
        list_router_response = list_ssvms(
            self.apiclient,
            systemvmId='domainrouter',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_router_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_router_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "LOGFILES"
        cmd.details = "/var/log/cloudstack/agent/agent.log,/var/log/cloudstack/agent/security_group.log,[IPTABLES]"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'Log file downloaded successfully')

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_cpvm_retrieve_files_success(self):
        list_cpvm_response = list_ssvms(
            self.apiclient,
            systemvmId='consoleproxy',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_cpvm_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_cpvm_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "LOGFILES"
        cmd.details = "/var/log/cloudstack/agent/agent.log,/var/log/cloudstack/agent/security_group.log,[IPTABLES]"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'Log file downloaded successfully')

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_ssvm_retrieve_files_failure(self):
        list_ssvm_response = list_ssvms(
            self.apiclient,
            systemvmId='consoleproxy',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_ssvm_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_ssvm_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "FILES"
        cmd.details = "agent.log,cloud.log"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'Failed to locate files from the system vm, check if the directory specified is correct.')

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_router_retrieve_files_failure(self):
        list_router_response = list_ssvms(
            self.apiclient,
            systemvmId='domainrouter',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_router_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_router_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "FILES"
        cmd.details = "[IPTABLES],[IFCONFIG]"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'Diagnostic type specified is not supported.')

    @attr(tags=["advanced", "smoke", "eip", "advancedns", "sg"], required_hardware="true")
    def test_cpvm_retrieve_files_failure(self):
        list_cpvm_response = list_ssvms(
            self.apiclient,
            systemvmId='testingvm',
            state='Running',
        )

        self.assertEqual(
            isinstance(list_cpvm_response, list),
            True,
            'Check list response returns a valid list'
        )
        ssvm = list_cpvm_response[0]
        cmd = retrieveDiagnostics.retrieveDiagnosticsCmd()
        cmd.systemvmId = ssvm.id
        cmd.type = "FILES"
        cmd.details = "[IPTABLES],[IFCONFIG]"
        response = self.apiclient.getDiagnosticsFiles(cmd)
        self.assertEqual(len(response), 1)
        self.assertEqual(response[0].name, 'Failed to find the system vm specified.')
