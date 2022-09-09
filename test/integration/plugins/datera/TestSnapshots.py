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

import logging
import unittest
import random
import XenAPI
import distutils.util

logger = logging.getLogger('myapp')
hdlr = logging.FileHandler('/var/tmp/syed.log')
formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
hdlr.setFormatter(formatter)
logger.addHandler(hdlr)
logger.setLevel(logging.WARNING)


# All tests inherit from cloudstackTestCase
from marvin.cloudstackTestCase import cloudstackTestCase

from nose.plugins.attrib import attr

# Import Integration Libraries

# base - contains all resources as entities and defines create, delete, list operations on them
from marvin.lib.base import Account, DiskOffering, ServiceOffering, Snapshot, StoragePool, Template, User, \
    VirtualMachine, Volume
# common - commonly used methods for all tests are listed here
from marvin.lib.common import get_domain, get_template, get_zone, list_clusters, list_hosts, list_virtual_machines, \
    list_volumes

# utils - utility classes for common cleanup, external library wrappers, etc.
from marvin.lib.utils import cleanup_resources

import dfs_sdk


class TestData():
    account = "account"
    capacityBytes = "capacitybytes"
    capacityIops = "capacityiops"
    clusterId = "clusterId"
    computeOffering = "computeoffering"
    diskName = "diskname"
    diskOffering = "diskoffering"
    domainId = "domainId"
    hypervisor = "hypervisor"
    login = "login"
    mvip = "mvip"
    password = "password"
    port = "port"
    primaryStorage = "primarystorage"
    provider = "provider"
    scope = "scope"
    Datera = "datera"
    storageTag = "Datera_SAN_1"
    tags = "tags"
    templateCacheName = "centos56-x86-64-xen"  # TODO
    templateName = "templatename"
    testAccount = "testaccount"
    url = "url"
    user = "user"
    username = "username"
    virtualMachine = "virtualmachine"
    virtualMachine2 = "virtualmachine2"
    volume_1 = "volume_1"
    volume_2 = "volume_2"
    xenServer = "xenserver"
    zoneId = "zoneId"

    def __init__(self):
        self.testdata = {
            TestData.Datera: {
                TestData.mvip: "192.168.22.100",
                TestData.login: "admin",
                TestData.password: "password",
                TestData.port: 80,
                TestData.url: "https://192.168.22.100:443"
            },
            TestData.xenServer: {
                TestData.username: "root",
                TestData.password: "password"
            },
            TestData.account: {
                "email": "test@test.com",
                "firstname": "John",
                "lastname": "Doe",
                "username": "test",
                "password": "test"
            },
            TestData.testAccount: {
                "email": "test2@test2.com",
                "firstname": "Jane",
                "lastname": "Doe",
                "username": "test2",
                "password": "test"
            },
            TestData.user: {
                "email": "user@test.com",
                "firstname": "Jane",
                "lastname": "Doe",
                "username": "testuser",
                "password": "password"
            },
            TestData.primaryStorage: {
                "name": "Datera-%d" % random.randint(0, 100),
                TestData.scope: "ZONE",
                "url": "MVIP=192.168.22.100;SVIP=192.168.100.2;" +
                       "clusterAdminUsername=admin;clusterAdminPassword=password;" +
                       "clusterDefaultMinIops=10000;clusterDefaultMaxIops=15000;" +
                       "numReplicas=1;",
                TestData.provider: "Datera",
                TestData.tags: TestData.storageTag,
                TestData.capacityIops: 4500000,
                TestData.capacityBytes: 2251799813685248,
                TestData.hypervisor: "Any"
            },
            TestData.virtualMachine: {
                "name": "TestVM",
                "displayname": "Test VM"
            },
            TestData.virtualMachine2: {
                "name": "TestVM2",
                "displayname": "Test VM 2"
            },
            TestData.computeOffering: {
                "name": "DT_CO_1",
                "displaytext": "DT_CO_1 (Min IOPS = 10,000; Max IOPS = 15,000)",
                "cpunumber": 1,
                "cpuspeed": 100,
                "memory": 128,
                "storagetype": "shared",
                "customizediops": False,
                "miniops": "10000",
                "maxiops": "15000",
                "hypervisorsnapshotreserve": 200,
                "tags": TestData.storageTag
            },
            TestData.diskOffering: {
                "name": "DT_DO_1",
                "displaytext": "DT_DO_1 (5GB Min IOPS = 300; Max IOPS = 500)",
                "disksize": 5,
                "customizediops": False,
                "miniops": 300,
                "maxiops": 500,
                "hypervisorsnapshotreserve": 200,
                TestData.tags: TestData.storageTag,
                "storagetype": "shared"
            },
            "testdiskofferings": {
                "customiopsdo": {
                    "name": "DT_Custom_Iops_DO",
                    "displaytext": "Customized Iops DO",
                    "disksize": 5,
                    "customizediops": True,
                    "miniops": 500,
                    "maxiops": 1000,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                },
                "customsizedo": {
                    "name": "DT_Custom_Size_DO",
                    "displaytext": "Customized Size DO",
                    "disksize": 5,
                    "customizediops": False,
                    "miniops": 500,
                    "maxiops": 1000,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                },
                "customsizeandiopsdo": {
                    "name": "DT_Custom_Iops_Size_DO",
                    "displaytext": "Customized Size and Iops DO",
                    "disksize": 10,
                    "customizediops": True,
                    "miniops": 400,
                    "maxiops": 800,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                },
                "newiopsdo": {
                    "name": "DT_New_Iops_DO",
                    "displaytext": "New Iops (min=350, max = 700)",
                    "disksize": 5,
                    "miniops": 350,
                    "maxiops": 700,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                },
                "newsizedo": {
                    "name": "DT_New_Size_DO",
                    "displaytext": "New Size: 175",
                    "disksize": 10,
                    "miniops": 400,
                    "maxiops": 800,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                },
                "newsizeandiopsdo": {
                    "name": "DT_New_Size_Iops_DO",
                    "displaytext": "New Size and Iops",
                    "disksize": 10,
                    "miniops": 200,
                    "maxiops": 400,
                    "hypervisorsnapshotreserve": 200,
                    TestData.tags: TestData.storageTag,
                    "storagetype": "shared"
                }
            },
            TestData.volume_1: {
                TestData.diskName: "test-volume",
            },
            TestData.volume_2: {
                TestData.diskName: "test-volume-2",
            },
            TestData.templateName: "tiny linux xenserver",  # TODO
            TestData.zoneId: 1,
            TestData.clusterId: 1,
            TestData.domainId: 1,
            TestData.url: "192.168.129.50"
        }

    def update(self, overrideFileName):
        if os.path.exists(overrideFileName):
            with open(overrideFileName) as fd:
                self.testdata = self._update(self.testdata, json.loads(fd.read()))

    def _update(self, d, u):

        for k, v in u.iteritems():
            if isinstance(v, collections.Mapping):
                r = self.update(d.get(k, {}), v)
                d[k] = r
            else:
                d[k] = u[k]
        return d


class TestSnapshots(cloudstackTestCase):
    _vm_not_in_running_state_err_msg = "The VM should be in running state"
    _should_be_zero_volume_access_groups_in_list_err_msg = "There shouldn't be any volume access groups in this list."
    _should_be_zero_snapshots_in_list_err_msg = "There shouldn't be any snapshots in this list."
    _should_only_be_one_snapshot_in_list_err_msg = "There should only be one snapshot in this list."
    _should_be_two_snapshots_in_list_err_msg = "There should be two snapshots in this list."
    _should_be_three_snapshots_in_list_err_msg = "There should be three snapshots in this list."
    _should_be_zero_volumes_in_list_err_msg = "There shouldn't be any volumes in this list."
    _should_only_be_one_volume_in_list_err_msg = "There should only be one volume in this list."
    _should_be_two_volumes_in_list_err_msg = "There should be two volumes in this list."
    _should_be_three_volumes_in_list_err_msg = "There should be three volumes in this list."
    _should_be_four_volumes_in_list_err_msg = "There should be four volumes in this list."
    _should_be_five_volumes_in_list_err_msg = "There should be five volumes in this list."
    _should_be_six_volumes_in_list_err_msg = "There should be six volumes in this list."
    _should_be_seven_volumes_in_list_err_msg = "There should be seven volumes in this list."
    _should_be_five_items_in_list_err_msg = "There should be five items in this list."
    _should_be_a_valid_volume_err = "There should be a valid backend volume"

    @classmethod
    def setUpClass(cls):
        # Set up API client
        testclient = super(TestSnapshots, cls).getClsTestClient()
        cls.apiClient = testclient.getApiClient()
        cls.dbConnection = testclient.getDbConnection()

        td = TestData()

        if cls.config.TestData and cls.config.TestData.Path:
            td.update(cls.config.TestData.Path)

        cls.testdata = td.testdata

        cls.supports_resign = cls._get_supports_resign()

        # Set up xenAPI connection
        hosts = list_hosts(cls.apiClient, clusterid=cls.testdata[TestData.clusterId])
        xenserver = cls.testdata[TestData.xenServer]

        for h in hosts:
            host_ip = "https://" + h.ipaddress
            try:
                cls.xen_session = XenAPI.Session(host_ip)
                cls.xen_session.xenapi.login_with_password(xenserver[TestData.username], xenserver[TestData.password])
                break
            except XenAPI.Failure as e:
                pass

        # Set up datera connection
        datera = cls.testdata[TestData.Datera]
        cls.dt_client = dfs_sdk.DateraApi(
            username=datera[TestData.login],
            password=datera[TestData.password],
            hostname=datera[TestData.mvip]
        )

        # Get Resources from Cloud Infrastructure
        cls.zone = get_zone(cls.apiClient, zone_id=cls.testdata[TestData.zoneId])
        cls.cluster = list_clusters(cls.apiClient)[0]
        cls.template = get_template(cls.apiClient, cls.zone.id, template_name=cls.testdata[TestData.templateName])
        cls.domain = get_domain(cls.apiClient, cls.testdata[TestData.domainId])

        # Create test account
        cls.account = Account.create(
            cls.apiClient,
            cls.testdata["account"],
            admin=1
        )

        # Set up connection to make customized API calls
        cls.user = User.create(
            cls.apiClient,
            cls.testdata["user"],
            account=cls.account.name,
            domainid=cls.domain.id
        )

        primarystorage = cls.testdata[TestData.primaryStorage]

        cls.primary_storage = StoragePool.create(
            cls.apiClient,
            primarystorage,
            scope=primarystorage[TestData.scope],
            zoneid=cls.zone.id,
            provider=primarystorage[TestData.provider],
            tags=primarystorage[TestData.tags],
            capacityiops=primarystorage[TestData.capacityIops],
            capacitybytes=primarystorage[TestData.capacityBytes],
            hypervisor=primarystorage[TestData.hypervisor]
        )

        cls.compute_offering = ServiceOffering.create(
            cls.apiClient,
            cls.testdata[TestData.computeOffering]
        )

        cls.disk_offering = DiskOffering.create(
            cls.apiClient,
            cls.testdata[TestData.diskOffering]
        )

        # Resources that are to be destroyed
        cls._cleanup = [
            cls.compute_offering,
            cls.disk_offering,
            cls.user,
            cls.account
        ]

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.apiClient, cls._cleanup)

            cls.primary_storage.delete(cls.apiClient)

            cls._purge_datera_template_volumes()
        except Exception as e:
            logging.debug("Exception in tearDownClass(cls): %s" % e)

    def setUp(self):
        self.attached = False
        self.cleanup = []

    def tearDown(self):
        cleanup_resources(self.apiClient, self.cleanup)

    @attr(hypervisor='XenServer')
    def test_01_create_native_snapshots(self):
        """
        * Create a VM using the managed disk offering
        * Create 3 snapshots on the root drive
        * Delete each of the snapshots while checking the backend
        * Create 2 more snaphsots
        * Delete the VM (should not delete the volume)
        * Delete the snapshots  (should delete the volume)
        """

        if not self.supports_resign:
            self.skipTest("Resignature not supported, skipping")

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        dt_volume_name = self._get_app_instance_name_from_cs_volume(vm_1_root_volume)

        dt_volumes = self._get_dt_volumes()

        dt_volume = self._check_and_get_dt_volume(dt_volumes, dt_volume_name)

        dt_snapshots = self._get_native_snapshots_for_dt_volume(dt_volume)

        self._check_list(dt_snapshots, 0, TestSnapshots._should_be_zero_snapshots_in_list_err_msg)

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        vol_snap_1 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                    TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                    TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        vol_snap_3 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 3,
                                                    TestSnapshots._should_be_three_snapshots_in_list_err_msg)

        self._delete_and_test_snapshot(vol_snap_2)

        self._delete_and_test_snapshot(vol_snap_1)

        self._delete_and_test_snapshot(vol_snap_3)

        vol_snap_1 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                    TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                    TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        virtual_machine.delete(self.apiClient, True)

        dt_volumes = self._get_dt_volumes()

        self._delete_and_test_snapshot(vol_snap_1)

        self._delete_and_test_snapshot(vol_snap_2, False)

    def test_02_create_template_from_native_snapshot(self):

        if not self.supports_resign:
            self.skipTest("Resignature not supported, skipping")

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        dt_volume = self._get_dt_volume_for_cs_volume(vm_1_root_volume)

        self.assertNotEqual(
            dt_volume,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        dt_snapshots = self._get_native_snapshots_for_dt_volume(dt_volume)

        self._check_list(dt_snapshots, 0, TestSnapshots._should_be_zero_snapshots_in_list_err_msg)

        vol_snap_1 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                    TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                    TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        vol_snap_3 = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 3,
                                                    TestSnapshots._should_be_three_snapshots_in_list_err_msg)

        services = {"displaytext": "Template-1", "name": "Template-1-name", "ostype": "CentOS 5.6 (64-bit)",
                    "ispublic": "true"}

        template = Template.create_from_snapshot(self.apiClient, vol_snap_2, services)

        self.cleanup.append(template)

        virtual_machine_dict = {"name": "TestVM2", "displayname": "Test VM 2"}

        virtual_machine_2 = VirtualMachine.create(
            self.apiClient,
            virtual_machine_dict,
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=template.id,
            domainid=self.domain.id,
            startvm=True
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine_2.id,
            listall=True
        )

        self.assertEqual(
            virtual_machine_2.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_2_root_volume = list_volumes_response[0]

        dt_volume_2 = self._get_dt_volume_for_cs_volume(vm_2_root_volume)

        self.assertNotEqual(
            dt_volume_2,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        self._delete_and_test_snapshot(vol_snap_1)
        self._delete_and_test_snapshot(vol_snap_2)
        self._delete_and_test_snapshot(vol_snap_3)

        virtual_machine.delete(self.apiClient, True)
        virtual_machine_2.delete(self.apiClient, True)

    def test_03_create_volume_from_native_snapshot(self):

        if not self.supports_resign:
            self.skipTest("Resignature not supported, skipping")

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        dt_volume_1 = self._get_dt_volume_for_cs_volume(vm_1_root_volume)

        self.assertNotEqual(
            dt_volume_1,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        vol_snap_a = self._create_and_test_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                    TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        services = {"diskname": "Vol-1", "zoneid": self.testdata[TestData.zoneId], "size": 100, "ispublic": True}

        volume_created_from_snapshot = Volume.create_from_snapshot(self.apiClient, vol_snap_a.id, services,
                                                                   account=self.account.name, domainid=self.domain.id)

        dt_snapshot_volume = self._get_dt_volume_for_cs_volume(volume_created_from_snapshot)

        self.assertNotEqual(
            dt_snapshot_volume,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        volume_created_from_snapshot = virtual_machine.attach_volume(
            self.apiClient,
            volume_created_from_snapshot
        )

        self._delete_and_test_snapshot(vol_snap_a)

        virtual_machine.delete(self.apiClient, True)

        list_volumes_response = list_volumes(
            self.apiClient,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        data_volume = list_volumes_response[0]

        data_volume_2 = Volume(data_volume.__dict__)

        data_volume_2.delete(self.apiClient)

        self._get_dt_volume_for_cs_volume(data_volume, should_exist=False)

    def test_04_create_non_native_snapshot(self):
        """
        * Create a VM using the managed disk offering
        * Create 3 snapshots on the root drive
        * Delete each of the snapshots while checking the backend
        * Create 2 more snaphsots
        * Delete the VM (should not delete the volume)
        * Delete the snapshots  (should delete the volume)
        """
        old_supports_resign = self.supports_resign
        self._set_supports_resign(False)

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        vol_snap_1 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                               TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                               TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        vol_snap_3 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 3,
                                                               TestSnapshots._should_be_three_snapshots_in_list_err_msg)

        self._delete_and_test_non_native_snapshot(vol_snap_2)

        self._delete_and_test_non_native_snapshot(vol_snap_1)

        self._delete_and_test_non_native_snapshot(vol_snap_3, False)

        vol_snap_1 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                               TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                               TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        virtual_machine.delete(self.apiClient, True)

        self._delete_and_test_non_native_snapshot(vol_snap_1)

        self._delete_and_test_non_native_snapshot(vol_snap_2)

        self._set_supports_resign(old_supports_resign)

    def test_05_create_template_from_non_native_snapshot(self):

        old_supports_resign = self.supports_resign
        self._set_supports_resign(False)

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        dt_volume = self._get_dt_volume_for_cs_volume(vm_1_root_volume)

        self.assertNotEqual(
            dt_volume,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        vol_snap_1 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                               TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        vol_snap_2 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 2,
                                                               TestSnapshots._should_be_two_snapshots_in_list_err_msg)

        vol_snap_3 = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 3,
                                                               TestSnapshots._should_be_three_snapshots_in_list_err_msg)

        services = {"displaytext": "Template-1", "name": "Template-1-name", "ostype": "CentOS 5.6 (64-bit)",
                    "ispublic": "true"}

        template = Template.create_from_snapshot(self.apiClient, vol_snap_2, services)

        self.cleanup.append(template)

        virtual_machine_dict = {"name": "TestVM2", "displayname": "Test VM 2"}

        virtual_machine_2 = VirtualMachine.create(
            self.apiClient,
            virtual_machine_dict,
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=template.id,
            domainid=self.domain.id,
            startvm=True
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine_2.id,
            listall=True
        )

        self.assertEqual(
            virtual_machine_2.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_2_root_volume = list_volumes_response[0]

        dt_volume_2 = self._get_dt_volume_for_cs_volume(vm_2_root_volume)

        self.assertNotEqual(
            dt_volume_2,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        virtual_machine.delete(self.apiClient, True)
        virtual_machine_2.delete(self.apiClient, True)

        self._delete_and_test_non_native_snapshot(vol_snap_1)

        self._delete_and_test_non_native_snapshot(vol_snap_2)

        self._delete_and_test_non_native_snapshot(vol_snap_3)

        self._set_supports_resign(old_supports_resign)

    def test_06_create_volume_from_non_native_snapshot(self):

        old_supports_resign = self.supports_resign
        self._set_supports_resign(False)

        primary_storage_db_id = self._get_cs_storage_pool_db_id(self.primary_storage)

        virtual_machine = VirtualMachine.create(
            self.apiClient,
            self.testdata[TestData.virtualMachine],
            accountid=self.account.name,
            zoneid=self.zone.id,
            serviceofferingid=self.compute_offering.id,
            templateid=self.template.id,
            domainid=self.domain.id,
            startvm=True
        )

        self.assertEqual(
            virtual_machine.state.lower(),
            "running",
            TestSnapshots._vm_not_in_running_state_err_msg
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            virtualmachineid=virtual_machine.id,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        vm_1_root_volume = list_volumes_response[0]

        dt_volume_1 = self._get_dt_volume_for_cs_volume(vm_1_root_volume)

        self.assertNotEqual(
            dt_volume_1,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        vol_snap_a = self._create_and_test_non_native_snapshot(vm_1_root_volume.id, primary_storage_db_id, 1,
                                                               TestSnapshots._should_only_be_one_snapshot_in_list_err_msg)

        services = {"diskname": "Vol-1", "zoneid": self.testdata[TestData.zoneId], "size": 100, "ispublic": True}

        volume_created_from_snapshot = Volume.create_from_snapshot(self.apiClient, vol_snap_a.id, services,
                                                                   account=self.account.name, domainid=self.domain.id)

        dt_snapshot_volume = self._get_dt_volume_for_cs_volume(volume_created_from_snapshot)

        self.assertNotEqual(
            dt_snapshot_volume,
            None,
            TestSnapshots._should_be_a_valid_volume_err
        )

        volume_created_from_snapshot = virtual_machine.attach_volume(
            self.apiClient,
            volume_created_from_snapshot
        )

        self._delete_and_test_non_native_snapshot(vol_snap_a)

        virtual_machine.delete(self.apiClient, True)

        list_volumes_response = list_volumes(
            self.apiClient,
            listall=True
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        data_volume = list_volumes_response[0]

        data_volume_2 = Volume(data_volume.__dict__)

        data_volume_2.delete(self.apiClient)

        self._get_dt_volume_for_cs_volume(data_volume, should_exist=False)

        self._set_supports_resign(old_supports_resign)

    def _check_list(self, in_list, expected_size_of_list, err_msg):
        self.assertEqual(
            isinstance(in_list, list),
            True,
            "'in_list' is not a list."
        )

        self.assertEqual(
            len(in_list),
            expected_size_of_list,
            err_msg
        )

    def _check_volume(self, volume, volume_name):
        self.assertTrue(
            volume.name.startswith(volume_name),
            "The volume name is incorrect."
        )

        self.assertEqual(
            volume.diskofferingid,
            self.disk_offering.id,
            "The disk offering is incorrect."
        )

        self.assertEqual(
            volume.zoneid,
            self.zone.id,
            "The zone is incorrect."
        )

        self.assertEqual(
            volume.storagetype,
            self.disk_offering.storagetype,
            "The storage type is incorrect."
        )

    def _check_and_get_cs_volume(self, volume_id, volume_name):

        list_volumes_response = list_volumes(
            self.apiClient,
            id=volume_id
        )

        self._check_list(list_volumes_response, 1, TestSnapshots._should_only_be_one_volume_in_list_err_msg)

        cs_volume = list_volumes_response[0]

        self._check_volume(cs_volume, volume_name)

        return cs_volume

    def _get_app_instance_name_from_cs_volume(self, cs_volume, vol_type='VOLUME'):
        return 'Cloudstack-' + vol_type + '-' + cs_volume.id

    def _get_iqn(self, cs_volume):
        """
        Get IQN for the CS volume from Datera
        """
        app_instance_name = self._get_app_instance_name_from_cs_volume(cs_volume)
        app_instance = self.dt_client.app_instances.get(app_instance_name)
        return app_instance['storage_instances']['storage-1']['access']['iqn']

    def _get_dt_volumes(self):
        return self.dt_client.app_instances.get()

    def _check_and_get_dt_volume(self, dt_volumes, dt_volume_name, should_exist=True):
        dt_volume = None
        dt_volumes = self._get_dt_volumes()

        for volume in dt_volumes.values():
            if volume['name'] == dt_volume_name:
                dt_volume = volume
                break

        if should_exist:
            self.assertNotEqual(
                dt_volume,
                None,
                "Check if Datera volume was created: " + str(dt_volumes)
            )
        else:
            self.assertEqual(
                dt_volume,
                None,
                "Check if volume was deleted: " + str(dt_volumes)
            )

        return dt_volume

    @classmethod
    def _set_supports_resign(cls, value=True):
        supports_resign = str(value)

        sql_query = "UPDATE host_details SET value='" + supports_resign + "' WHERE name='supportsResign'"
        cls.dbConnection.execute(sql_query)

        sql_query = "Update cluster_details Set value = '" + supports_resign + "' Where name = 'supportsResign'"
        cls.dbConnection.execute(sql_query)

    def _get_supports_resign(cls):

        sql_query = "SELECT value from cluster_details Where name='supportsResign' AND cluster_id=%d" % cls.testdata[
            TestData.clusterId]

        sql_result = cls.dbConnection.execute(sql_query)
        logger.warn(sql_result)

        if len(sql_result) < 1:
            return False

        return bool(distutils.util.strtobool(sql_result[0][0].lower()))

    @classmethod
    def _purge_datera_template_volumes(cls):
        for ai in cls.dt_client.app_instances.get().values():
            if 'TEMPLATE' in ai['name']:
                ai.set(admin_state="offline")
                ai.delete()

    def _get_cs_storage_pool_db_id(self, storage_pool):
        return self._get_db_id("storage_pool", storage_pool)

    def _get_db_id(self, table, db_obj):
        sql_query = "Select id From " + table + " Where uuid = '" + str(db_obj.id) + "'"

        sql_result = self.dbConnection.execute(sql_query)
        return sql_result[0][0]

    def _get_native_snapshots_for_dt_volume(self, dt_volume):
        snapshots_dict = dt_volume['storage_instances']['storage-1']['volumes']['volume-1']['snapshots']
        return snapshots_dict.values()

    def _get_dt_volume_for_cs_volume(self, cs_volume, vol_type='VOLUME', should_exist=True):

        dt_volume_name = self._get_app_instance_name_from_cs_volume(cs_volume, vol_type)
        dt_volumes = self._get_dt_volumes()

        return self._check_and_get_dt_volume(dt_volumes, dt_volume_name, should_exist)

    def _create_and_test_snapshot(self, cs_vol_id, primary_storage_db_id, expected_num_snapshots, err_mesg):

        vol_snap = Snapshot.create(
            self.apiClient,
            volume_id=cs_vol_id
        )

        list_volumes_response = list_volumes(
            self.apiClient,
            id=cs_vol_id
        )

        cs_volume = list_volumes_response[0]
        dt_volume = self._get_dt_volume_for_cs_volume(cs_volume)

        dt_snapshots = self._get_native_snapshots_for_dt_volume(dt_volume)

        self._check_list(dt_snapshots, expected_num_snapshots, err_mesg)

        dt_snapshot = self._most_recent_dt_snapshot(dt_snapshots)

        vol_snap_db_id = self._get_cs_volume_snapshot_db_id(vol_snap)

        snapshot_details = self._get_snapshot_details(vol_snap_db_id)

        dt_volume_id = self._get_app_instance_name_from_cs_volume(cs_volume)

        dt_snapshot_id = dt_volume_id + ':' + dt_snapshot['timestamp']

        self._check_snapshot_details(snapshot_details, vol_snap_db_id, dt_volume_id, dt_snapshot_id,
                                     primary_storage_db_id)
        return vol_snap

    def _create_and_test_snapshot_2(self, volume_id_for_snapshot, dt_volume_id, dt_volume_id_for_volume_snapshot,
                                    primary_storage_db_id, dt_volume_size,
                                    dt_account_id, expected_num_volumes, volume_err_msg):
        pass

    def _delete_and_test_snapshot(self, vol_snap, check_volume=True):
        vol_snap_id = vol_snap.id
        vol_snap_db_id = self._get_cs_volume_snapshot_db_id(vol_snap)

        snapshot_details = self._get_snapshot_details(vol_snap_db_id)

        dt_volume_id = snapshot_details.get("DateraVolumeId")
        dt_snapshot_id = snapshot_details.get("DateraSnapshotId")

        vol_snap.delete(self.apiClient)

        if check_volume:
            dt_volume = self._get_datera_volume(dt_volume_id)

            dt_snapshots = self._get_native_snapshots_for_dt_volume(dt_volume)

            # check datera if it actually got deleted
            self._check_dt_snapshot_does_not_exist(dt_snapshots, dt_snapshot_id)
            self._check_snapshot_details_do_not_exist(vol_snap_db_id)

    def _most_recent_dt_snapshot(self, dt_snapshots):

        if dt_snapshots:
            return sorted(dt_snapshots, key=lambda x: int(x['timestamp'].split('.')[0]))[-1]

        return None

    def _get_cs_volume_snapshot_db_id(self, vol_snap):
        return self._get_db_id("snapshots", vol_snap)

    def _check_snapshot_details(self, snapshot_details, cs_snapshot_id, dt_volume_id, dt_snapshot_id,
                                storage_pool_id):

        self._check_list(snapshot_details.keys(), 5, TestSnapshots._should_be_five_items_in_list_err_msg)

        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "takeSnapshot", "true")
        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "DateraVolumeId", dt_volume_id)
        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "DateraSnapshotId", dt_snapshot_id)
        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "DateraStoragePoolId", str(storage_pool_id))

    # non-native
    def _check_snapshot_details_non_native(self, snapshot_details, cs_snapshot_id, dt_volume_id, storage_pool_id):

        self._check_list(snapshot_details.keys(), 5, TestSnapshots._should_be_five_items_in_list_err_msg)

        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "DateraStoragePoolId", str(storage_pool_id))
        self._check_snapshot_detail(snapshot_details, cs_snapshot_id, "DateraVolumeId", dt_volume_id)

    def _check_snapshot_detail(self, snapshot_details, cs_snapshot_id, snapshot_detail_key, snapshot_detail_value):

        if snapshot_detail_key in snapshot_details:
            if snapshot_details[snapshot_detail_key] == snapshot_detail_value:
                return

        raise Exception(
            "There is a problem with the snapshot details key '" + snapshot_detail_key + "' and value '" + str(
                snapshot_detail_value) + "'.")

    def _check_snapshot_detail_starts_with(self, snapshot_details, cs_snapshot_id, snapshot_detail_key,
                                           starts_with):

        if snapshot_detail_key in snapshot_details:
            if snapshot_details[snapshot_detail_key].startswith(starts_with):
                return

        raise Exception(
            "There is a problem with the snapshot details key '" + snapshot_detail_key + "' and 'starts with' value '" + starts_with + "'.")

    def _get_snapshot_details(self, snapshot_db_id):

        details = {}
        sql_query = "SELECT name,value FROM snapshot_details where snapshot_id=" + str(snapshot_db_id)
        sql_result = self.dbConnection.execute(sql_query)

        for row in sql_result:
            key = row[0]
            value = row[1]
            details[key] = value

        return details

    def _check_dt_snapshot_does_not_exist(self, dt_snapshots, dt_snapshot_id):
        timestamp = dt_snapshot_id.split(':')[-1]
        if timestamp in dt_snapshots:
            raise Exception("Snapshot %s still exists on Datera" % dt_snapshot_id)

    def _check_snapshot_details_do_not_exist(self, vol_snap_db_id):
        sql_query = "Select count(*) From snapshot_details Where snapshot_id = " + str(vol_snap_db_id)

        # make sure you can connect to MySQL: https://teamtreehouse.com/community/
        # cant-connect-remotely-to-mysql-server-with-mysql-workbench
        sql_result = self.dbConnection.execute(sql_query)

        self.assertEqual(
            sql_result[0][0],
            0,
            "Snapshot details should not exist for the following CloudStack volume snapshot DB ID: " + str(
                vol_snap_db_id)
        )

    def _get_datera_volume(self, vol_name):
        try:
            app_instance = self.dt_client.app_instances.get(vol_name)
            return app_instance
        except dfs_sdk.exceptions.ApiNotFoundError as e:
            pass
        return None

    def _delete_and_test_non_native_snapshot(self, vol_snap, check_volume=True):

        vol_snap_id = vol_snap.id
        vol_snap_db_id = self._get_cs_volume_snapshot_db_id(vol_snap)

        snapshot_details = self._get_snapshot_details(vol_snap_db_id)

        dt_snapshot_id = snapshot_details.get("DateraVolumeId")

        vol_snap.delete(self.apiClient)

        dt_snapshot_volume = self._get_datera_volume(dt_snapshot_id)

        self.assertEqual(dt_snapshot_volume,
                         None,
                         TestSnapshots._should_be_zero_volumes_in_list_err_msg
                         )

        # check db
        self._check_snapshot_details_do_not_exist(vol_snap_db_id)

    def _create_and_test_non_native_snapshot(self, cs_vol_id, primary_storage_db_id, expected_num_snapshots, err_mesg):

        vol_snap = Snapshot.create(
            self.apiClient,
            volume_id=cs_vol_id
        )

        dt_snapshot_volume_name = self._get_app_instance_name_from_cs_volume(vol_snap, vol_type='SNAPSHOT')

        dt_volumes = self._get_dt_volumes()
        dt_volume = self._check_and_get_dt_volume(dt_volumes, dt_snapshot_volume_name)

        vol_snap_db_id = self._get_cs_volume_snapshot_db_id(vol_snap)

        snapshot_details = self._get_snapshot_details(vol_snap_db_id)

        self._check_snapshot_details_non_native(snapshot_details, vol_snap_db_id, dt_snapshot_volume_name,
                                                primary_storage_db_id)

        return vol_snap
