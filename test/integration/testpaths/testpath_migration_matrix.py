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
from marvin.lib.utils import cleanup_resources, format_volume_to_ext3
from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_template
                               )

class TestStorageMigrations(cloudstackTestCase):
    """
    test a matrix of migration based on a input of available storages and assuming users 'admin' and 'user'
    as of now this test class makes a lot of assumtions. more defencive programming is needed to do away with those.
    - a set of source-pools is assumed with diskOfferings and tags of the same names
    - tagging may yet be in the way of migrations, possible improvement there
    TODO the implementation of the matrix traversal is not implemented yet
    """

    user_dimension = [ "admin" ] # should be [ "admin", "user"]
    system_dimension = [ "6.7" ] # should be [ "6.5", "6.7"]
    source_pool_dimension = [ "nfs-67" ]
    targets_pool_dimension = [ "nfs-65" ]
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
                cls.userapiclient,
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
        run the defined tests in a double loop from sources to targets

        TODO set up a multidimensonal dictionary to capture results in

        :return:
        """
        for source_name in self.source_pool_dimension:
            for target_name in self.targets_pool_dimension:
                if source_name != target_name:
                    self.test_a_migration(source_name, target_name)
        return

    def test_a_migration(self, source_name, target_name):
        """
        Do a single iteration of the test sequence,
         creating the volume on the source migrating and then deleting the volume from the target.

        :param source_name: source pool to migrate from
        :param target_name: target pool to migrate to
        :return:
        """
        # TODO validate pool results
        source_pool = StoragePool.list(self.apiclient, name=source_name)[0]
        target_pool = StoragePool.list(self.apiclient, name=target_name)[0]
        volume = self.setup_source(source_pool)
        result_to_validate = self.migrate(volume, target_pool)
        self.validate(volume, migration_result=result_to_validate, target=target_pool)
        self.remove_volume(volume)
        return

    def setup_source(self, source):
        """
        # create a volume
        # attach the volume to a running vm to have it on primary
        # migrate to the source primary
        # detach the volume

        :param source: the pool to set a volume up on
        :return: the marvin volume object
        """
        vol = Volume.create(
            self.userapiclient,
            self.testdata["volume"],
            diskofferingid=self.disk_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid,
        )

        print("created volume with id: '{id}' and name: '{name}".format(id=vol.id, name=vol.name))

        # NOTE either self.cleanup.append(vol) at this point or the more localised/explicit remove(vol) in the test method
        # TODO improve marvin so cleanup doesn't break if an explicit remove had already be done

        # make sure the volume is implemented
        self.vm.attach_volume(self.userapiclient, vol)
        self.vm.detach_volume(self.userapiclient, vol)

        #put it on the origin point of this migration test
        vol.migrate(self.userapiclient, storageid=source.id)

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
        as the method signature sugests

        :param volume: the object to migrate
        :param target: pool to migrate to
        :return: None if no target pool found, the result of the migration command otherwise
        """
        pools = StoragePool.listForMigration(
            self.userapiclient,
            id=volume.id
        )

        for pool in pools:
            if pool.name == target.name:
                return volume.migrate(self.userapiclient, storageid=pool.id)
        return None

    def validate(self, volume, migration_result, target):
        """

        :param volume:
        :param target:
        :return:
        """
        if migration_result == None:
            raise Exception("No migration result to verify")
        print migration_result
        return