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
from marvin.lib.base import ServiceOffering, Configurations, VirtualMachine, Account, Volume, DiskOffering, StoragePool, \
    Role
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

    source_pool_dimension = [ "nfs-65", "vsanDatastore-6.5", "ref-trl-1513-v-M7-alex-mattioli-esxi-pri5" ]
    targets_pool_dimension = [ "nfs-65", "vsanDatastore-6.5", "ref-trl-1513-v-M7-alex-mattioli-esxi-pri5" ]
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

        # for now until we want to be more generic
        if cls.hypervisor.lower() not in ["vmware"]:
            cls.skiptest = True

        try:
            # Get root admin role
            cls.rootadminrole = Role.list(cls.apiclient)[0]

            # Create an account
            cls.account = Account.create(
                cls.apiclient,
                cls.testdata["account"],
                roleid=cls.rootadminrole.id
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

        TODO set up a multidimensional dictionary to capture results in

        :return:
        """
        print(">>> Creating volume")
        vol = Volume.create(
            self.userapiclient,
            self.testdata["volume"],
            diskofferingid=self.disk_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid,
        )
        print(">>> Attach volume to the VM")
        self.vm.attach_volume(self.userapiclient, vol)
        print(">>> Detach volume from the VM")
        self.vm.detach_volume(self.userapiclient, vol)

        print(">>> Starting migrations")
        for source_name in self.source_pool_dimension:
            for target_name in self.targets_pool_dimension:
                if source_name != target_name:
                    self.test_a_migration(vol, source_name, target_name)

        print(">>> Delete volume")
        vol.delete(self.apiclient)
        return

    def test_a_migration(self, volume, source_name, target_name):
        """
        Do a single iteration of the test sequence,
         moving the volume to the source destination and then testing the actual migration from there to the target

        :param volume: the volume we want to migrate
        :param source_name: source pool to migrate from
        :param target_name: target pool to migrate to
        :return:
        """
        # TODO validate pool results
        source_pool = StoragePool.list(self.apiclient, name=source_name)[0]
        target_pool = StoragePool.list(self.apiclient, name=target_name)[0]
        voldata = volume.list(self.apiclient, id=volume.id)

        # Move our volume to the source datastore
        if source_pool.name != voldata[0].storage:
            try:
                print(">>> Setup volume migration from %s to source datastore %s" % (voldata[0].storage, source_pool.name))
                result1 = volume.migrate(self.userapiclient, storageid=source_pool.id, volumeid=volume.id)
                if source_pool.name == result1.storage:
                    print("SUCCESS!")
                else:
                    print("ERROR: Something went wrong, volume storage is still %s" % volume.storage)
            except Exception as e:
                print(">>> ERROR: Setup volume migration to source datastore %s failed with error %s " % (source_pool.name, e))
                return
        else:
            print(">>> Current source datastore is the desired one - no need for setup")

        # Migrate our volume to the target datastore
        try:
            print(">>> Migrating volume from %s to %s" % (source_pool.name, target_pool.name))
            result2 = volume.migrate(self.userapiclient, volumeid=volume.id, storageid=target_pool.id)
            if target_pool.name == result2.storage:
                print("SUCCESS!")
            else:
                print("ERROR: Something went wrong, volume storage is still %s" % volume.storage)
        except Exception as e:
            print(">>> ERROR: Volume migration to target datastore %s failed with error %s " % (target_pool.name, e))
        return
