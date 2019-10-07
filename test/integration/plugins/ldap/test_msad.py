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

#Import Local Modules
import marvin
from marvin.cloudstackTestCase import *
from marvin.cloudstackAPI import *
from marvin.lib.utils import *
from marvin.lib.base import *
from marvin.lib.common import *
from marvin.lib.utils import (random_gen)
from nose.plugins.attrib import attr

#Import System modules
import time
import logging

logger = logging.getLogger(__name__)
logger_handler = logging.FileHandler('/tmp/MarvinLogs/{}.log'.format(__name__))
logger_formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
logger_handler.setFormatter(logger_formatter)
logger.addHandler(logger_handler)
logger.setLevel(logging.DEBUG)


class TestData:
    #constants
    syncAccounts = "accountsToSync"
    parentDomain = "LDAP"
    manualDomain = "manual"
    importDomain = "import"
    syncDomain = "sync"
    name = "name"
    id = "id"
    notAvailable = "N/A"
    groups = "groups"
    group = "group"
    seniorAccount = "seniors"
    juniorAccount = "juniors"

    msad_ip_address = "localhost"
    msad_port = "389"
    hostname = "hostname"
    port = "port"
    dn = "dn"
    ou = "ou"
    cn = "cn"
    member = "uniqueMember"
    basedn = "basedn"
    ldapPw = "ldapPassword"
    principal = "ldapUsername"
    users = "users"
    objectClass = "objectClass"
    sn = "sn"
    givenName = "givenName"
    uid = "uid"
    domains = "domains"

    juniors = "ou=juniors,ou=groups,dc=echt,dc=net"
    seniors = "ou=seniors,ou=groups,dc=echt,dc=net"

    def __init__(self):
        self.testdata = {
            "ldap_configuration" : {
                "emailAttribute": "mail",
                "userObject": "person",
                "usernameAttribute": TestData.uid,
                # global values for use in all domains
                TestData.hostname: TestData.msad_ip_address,
                TestData.port: TestData.msad_port,
                TestData.basedn: "ou=system",
                TestData.ldapPw: "secret",
                TestData.principal: "uid=admin,ou=system",
            },
            TestData.groups: [
                {
                    TestData.dn : "ou=people,dc=echt,dc=net",
                    TestData.objectClass: [ "organizationalUnit", "top"],
                    TestData.ou : "People"
                },
                {
                    TestData.dn : "ou=groups,dc=echt,dc=net",
                    TestData.objectClass: ["organizationalUnit", "top"],
                    TestData.ou : "Groups"
                },
                {
                    TestData.dn : TestData.seniors,
                    TestData.objectClass: ["groupOfUniqueNames", "top"],
                    TestData.ou : "seniors",
                    TestData.cn : "seniors",
                    TestData.member : [ "uid=bobby,ou=people,dc=echt,dc=net", "uid=rohit,ou=people,dc=echt,dc=net"]
                },
                {
                    TestData.dn : TestData.juniors,
                    TestData.objectClass : [ "groupOfUniqueNames", "top"],
                    TestData.ou : "juniors",
                    TestData.cn : "juniors",
                    TestData.member : ["uid=dahn,ou=people,dc=echt,dc=net", "uid=paul,ou=people,dc=echt,dc=net"]
                }
            ],
            TestData.users: [
                {
                    TestData.dn : "uid=bobby,ou=people,dc=echt,dc=net",
                    TestData.objectClass : [ "inetOrgPerson", "top", "person"],
                    TestData.cn : "bobby",
                    TestData.sn: "Stoyanov",
                    TestData.givenName : "Boris",
                    TestData.uid : "bobby"
                },
                {
                    TestData.dn : "uid=dahn,ou=people,dc=echt,dc=net",
                    TestData.objectClass : [ "inetOrgPerson", "top", "person"],
                    TestData.cn : "dahn",
                    TestData.sn: "Hoogland",
                    TestData.givenName : "Daan",
                    TestData.uid : "dahn"
                },
                {
                    TestData.dn : "uid=paul,ou=people,dc=echt,dc=net",
                    TestData.objectClass : [ "inetOrgPerson", "top", "person"],
                    TestData.cn : "Paul",
                    TestData.sn: "Angus",
                    TestData.givenName : "Paul",
                    TestData.uid : "paul"
                },
                {
                    TestData.dn : "uid=rohit,ou=people,dc=echt,dc=net",
                    TestData.objectClass : [ "inetOrgPerson", "top", "person"],
                    TestData.cn : "rhtyd",
                    TestData.sn: "Yadav",
                    TestData.givenName : "Rohit",
                    TestData.uid : "rohit"
                },
            ],
            TestData.domains : [
                {
                    TestData.name : TestData.parentDomain,
                    TestData.id : TestData.notAvailable
                },
                {
                    TestData.name : TestData.manualDomain,
                    TestData.id : TestData.notAvailable
                },
                {
                    TestData.name : TestData.importDomain,
                    TestData.id : TestData.notAvailable
                },
                {
                    TestData.name : TestData.syncDomain,
                    TestData.id : TestData.notAvailable
                },
            ],
            TestData.syncAccounts : [
                {
                    TestData.name : TestData.juniorAccount,
                    TestData.id : TestData.notAvailable,
                    TestData.group : TestData.juniors
                },
                {
                    TestData.name : TestData.seniorAccount,
                    TestData.id : TestData.notAvailable,
                    TestData.group : TestData.seniors
                }
            ],
        }


class TestMSAD(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        '''
            needs to
             - create the applicable ldap accounts in the directory server
             - create three domains:
             -- LDAP/manual
             -- LDAP/import
             -- LDAP/sync
        '''
        logger.info("Setting up Class")
        testClient = super(TestMSAD, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        # Setup test data
        td = TestData()
        if cls.config.TestData and cls.config.TestData.Path:
            logger.debug("reading extra config from '" +cls.config.TestData.Path+ "'")
            td.update(cls.config.TestData.Path)
        logger.debug(td)

        cls.services = testClient.getParsedTestDataConfig()
        cls.services["configurableData"]["ldap_configuration"] = td.testdata["ldap_configuration"]
        logger.debug(cls.services["configurableData"]["ldap_configuration"])

        # Get Zone, Domain
        cls.domain = get_domain(cls.apiclient)
        logging.debug("standard domain: %s" % cls.domain)
        cls.zone = get_zone(cls.apiclient, cls.testClient.getZoneForTests())

        cls._cleanup = []

        # Build the test env
        # create a parent domain
        logger.info("Creating domain: " + TestData.parentDomain)
        tmpDomain = Domain.create(cls.apiclient,td.testdata["domains"][0])
        logger.debug("listing just created domain %s" % tmpDomain.id)
        cls.parentDomain = Domain.list(cls.apiclient,id=tmpDomain.id)[0]
        logger.debug("found just created domain by id %s" % cls.parentDomain)
        cls._cleanup.append( cls.parentDomain )

        logger.info("Creating domain: " + TestData.manualDomain)
        tmpDomain = Domain.create(cls.apiclient,td.testdata["domains"][1], parentdomainid=cls.parentDomain.id)
        cls.manualDomain = Domain.list(cls.apiclient,id=tmpDomain.id)[0]
        cls._cleanup.append( cls.manualDomain )

        for obj in reversed(cls._cleanup):
            logger.debug(obj)

        return

    @classmethod
    def tearDownClass(cls):
        logger.info("Tearing Down Class")
        try:
            for obj in reversed(cls._cleanup):
                logger.debug(obj)
            cleanup_resources(cls.apiClient, reversed(cls._cleanup))
            logging.debug("done cleaning up resources in tearDownClass(cls) %s")
        except Exception as e:
            logging.debug("Exception in tearDownClass(cls): %s" % e)

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.hypervisor = self.testClient.getHypervisorInfo()
        self.dbclient = self.testClient.getDbConnection()
        self.services = self.testClient.getParsedTestDataConfig()
        self.zone = get_zone(self.apiclient, self.testClient.getZoneForTests())
        self.pod = get_pod(self.apiclient, self.zone.id)
        self.cleanup = []
        return

    def tearDown(self):
        try:
            #Clean up, terminate the created templates
            cleanup_resources(self.apiclient, self.cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    @attr(tags=["smoke", "advanced"], required_hardware="false")
    def test_01_manual(self):
        '''
        test if an account can be imported

        prerequisite
        a ldap host is configured
        a domain is linked to cloudstack
        '''
        return

    @attr(tags=["smoke", "advanced"], required_hardware="false")
    def test_02_import(self):
        '''
        test if components are synced

        prerequisite
        a ldap host is configured
        a domain is linked to cloudstack
        '''
        return

    @attr(tags=["smoke", "advanced"], required_hardware="false")
    def test_03_sync(self):
        '''
        test if components are synced

        prerequisite
        a ldap host is configured
        a domain is linked to cloudstack
        some accounts in that domain are linked to groups in ldap
        '''
        return
