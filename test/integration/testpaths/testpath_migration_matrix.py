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
""" Test cases for Delta Snapshots Test Path
"""
import time
from marvin.lib.base import ServiceOffering, Configurations, VirtualMachine, Account, Volume, DiskOffering, StoragePool
from marvin.lib.utils import cleanup_resources
from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_template
                               )

class TestStorageMigrations(cloudstackTestCase):
    """
    test a matrix of migration based on a input of available storages and assuming users 'admin' and 'user'
    """

    user_dimension = [ "admin" ] # should be [ "admin", "user"]
    system_dimension = [ "6.7" ] # should be [ "6.5", "6.7"]
    source_pool_dimension = [ "NFS" ]
    targets_pool_dimension = [ "VSAN" ]
    # we should use a pool_matrix of
    # [ "NFS", "VMFS5", "VMFS6", "VVOLS", "VSAN", "DSC"] * [ "NFS", "VMFS5", "VMFS6", "VVOLS", "VSAN", "DSC"]

    @classmethod
    def setUpClass(cls):
        testClient = super(TestStorageMigrations, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        cls.testdata = testClient.getParsedTestDataConfig()
        cls.hypervisor = cls.testClient.getHypervisorInfo()

        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.apiclient)
        cls.zone = get_zone(cls.apiclient, testClient.getZoneForTests())

        cls._cleanup = []

        cls.mgtSvrDetails = cls.config.__dict__["mgtSvr"][0].__dict__
        cls.skiptest = False

        # for now untill we want to be more generic
        if cls.hypervisor.lower() not in ["vmware"]:
            cls.skiptest = True

        try:

            # Create an account
            cls.account = Account.create(
                cls.apiclient,
                cls.testdata["account"],
                domainid=cls.domain.id
            )
            cls._cleanup.append(cls.account)

            # Create user api client of the account
            cls.userapiclient = testClient.getUserApiClient(
                UserName=cls.account.name,
                DomainName=cls.account.domain
            )

            # Create Service offering
            cls.service_offering = ServiceOffering.create(
                cls.apiclient,
                cls.testdata["service_offering"],
            )
            cls._cleanup.append(cls.service_offering)

            cls.template = get_template(
                cls.apiclient,
                cls.zone.id,
                cls.testdata["ostype"]
            )

            cls.vm = VirtualMachine.create(
                cls.apiclient,
                cls.testdata["small"],
                templateid=cls.template.id,
                accountid=cls.account.name,
                domainid=cls.account.domainid,
                serviceofferingid=cls.service_offering.id,
                zoneid=cls.zone.id,
                mode=cls.zone.networktype
            )
            cls._cleanup.append(cls.vm)
            cls.disk_offering = DiskOffering.create(
                cls.apiclient,
                cls.testdata["disk_offering"]
            )
            cls._cleanup.append(cls.disk_offering)

        except Exception as e:
            cls.tearDownClass()
            raise e
        return

    def setUp(self):
        if self.skiptest:
            self.skipTest(
                "not testing migration on %s" %
                self.hypervisor)
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.apiclient, reversed(cls._cleanup))
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def tearDown(self):
        try:
            cleanup_resources(self.apiclient, reversed(self.cleanup))
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags=["advanced", "basic"], required_hardware="true")
    def test_the_matrix(self):
        """
        run the defined tests
        :return:
        """
        return

    def test_a_migration(self, source, target):
        """
        Do a single iteration of the test sequence,
         creating the volume on the source migrating and then deleting the volume from the target.

        :param source: source pool to igrate from
        :param target: target pool to migrate to
        :return:
        """
        volume = self.setup_source(source)
        self.migrate(volume, target)
        self.validate(volume, target)
        self.remove_volume(volume)
        return

    def setup_source(self, source):
        """

        :param source: the pool to set a volume up on
        :return: the marvin volume object
        """
        # create a volume with tags for the right primary
        # attach the volume to a running vm to have it on primary
        # detach the volume
        vol = Volume.create(
            self.apiclient,
            self.testdata["volume"],
            diskofferingid=self.disk_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid,
        )

        self.cleanup.append(vol)

        self.vm.attach_volume(
            self.apiclient,
            vol
        )

        pools = StoragePool.listForMigration(
            self.apiclient,
            id=vol.id
        )

        if not pools:
            self.skipTest(
                "No suitable storage pools found for volume migration.\
                        Skipping")

        self.vm.detach_volume(self.apiclient, vol)

        return vol

    def remove_volume(self, volume):
        """

        :param volume: the id of the volume
        :return:
        """
        volume.delete(self.apiclient)
        return

    def migrate(self, volume, target):
        """

        :param volume:
        :param target:
        :return:
        """
        return

    def validate(self, volume, target):
        """

        :param volume:
        :param target:
        :return:
        """
        return