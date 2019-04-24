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
package org.apache.cloudstack.api.command;

import org.apache.cloudstack.api.response.LdapUserResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.UserResponse;
import org.apache.cloudstack.ldap.LdapManager;
import org.apache.cloudstack.ldap.LdapUser;
import org.apache.cloudstack.ldap.NoLdapUserMatchingQueryException;
import org.apache.cloudstack.query.QueryService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LdapListUsersCmdTest implements LdapConfigurationChanger {

    @Mock
    LdapManager ldapManager;
    @Mock
    QueryService queryService;

    LdapListUsersCmd ldapListUsersCmd;
    LdapListUsersCmd cmdSpy;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        ldapListUsersCmd = new LdapListUsersCmd(ldapManager, queryService);
        cmdSpy = spy(ldapListUsersCmd);
// no need to        setHiddenField(ldapListUsersCmd, .... );
    }

    /**
     * given: "We have an LdapManager, QueryService and LdapListUsersCmd"
     *  when: "Get entity owner id is called"
     *  then: "a 1 should be returned"
     *
     */
    @Test
    public void getEntityOwnerIdisOne() {
		long ownerId = ldapListUsersCmd.getEntityOwnerId();
		assertEquals(ownerId, 1);
    }

    /**
     * given: "We have an LdapManager with no users, QueryService and a LdapListUsersCmd"
     *  when: "LdapListUsersCmd is executed"
     * 	then: "An array of size 0 is returned"
     *
     * @throws NoLdapUserMatchingQueryException
     */
    @Test
    public void successfulEmptyResponseFromExecute() throws NoLdapUserMatchingQueryException {
        doThrow(new NoLdapUserMatchingQueryException("")).when(ldapManager).getUsers(null);
		ldapListUsersCmd.execute();
		assertEquals(0, ((ListResponse)ldapListUsersCmd.getResponseObject()).getResponses().size());
    }

    /**
     * given: "We have an LdapManager, one user, QueryService and a LdapListUsersCmd"
     *  when: "LdapListUsersCmd is executed"
     *  then: "a list of size not 0 is returned"
     */
    @Test
    public void successfulResponseFromExecute() throws NoLdapUserMatchingQueryException {
        mockACSUserSearch();

		List<LdapUser> users = new ArrayList();
		LdapUser murphy = new LdapUser("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);
        LdapUser bob = new LdapUser("bob", "bob@test.com", "Robert", "Young", "cn=bob,ou=engineering,dc=cloudstack,dc=org", "engineering", false, null);
        users.add(murphy);
        users.add(bob);

		doReturn(users).when(ldapManager).getUsers(null);

        LdapUserResponse response = new LdapUserResponse("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null);
        doReturn(response).when(ldapManager).createLdapUserResponse(murphy);
        LdapUserResponse bobResponse = new LdapUserResponse("bob", "bob@test.com", "Robert", "Young", "cn=bob,ou=engineering,dc=cloudstack,dc=org", "engineering");
        doReturn(bobResponse).when(ldapManager).createLdapUserResponse(bob);

		ldapListUsersCmd.execute();

		verify(queryService, times(1)).searchForUsers(any());
        assertNotEquals(0, ((ListResponse)ldapListUsersCmd.getResponseObject()).getResponses().size());
    }

    /**
     * given: "We have an LdapManager, QueryService and a LdapListUsersCmd"
     *  when: "Get command name is called"
     *  then: "ldapuserresponse is returned"
     */
    @Test
    public void successfulReturnOfCommandName() {
        String commandName = ldapListUsersCmd.getCommandName();

        assertEquals("ldapuserresponse", commandName);
    }

    /**
     * given: "We have an LdapUser and a CloudStack user whose username match"
     *  when: "isACloudstackUser is executed"
     *  then: "The result is true"
     *
     * TODO: is this really the valid behaviour? shouldn't the user also be linked to ldap and not accidentally match?
     */
    @Test
    public void isACloudstackUser() {
        mockACSUserSearch();

        LdapUser ldapUser = new LdapUser("rmurphy", "rmurphy@cloudstack.org", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);

		boolean result = ldapListUsersCmd.isACloudstackUser(ldapUser);

		assertTrue(result);
	}

    private void mockACSUserSearch() {
        UserResponse userResponse = new UserResponse();
        userResponse.setUsername("rmurphy");

        ArrayList<UserResponse> responses = new ArrayList<UserResponse>();
        responses.add(userResponse);

        ListResponse<UserResponse> queryServiceResponse = new ListResponse<UserResponse>();
        queryServiceResponse.setResponses(responses);

        doReturn(queryServiceResponse).when(queryService).searchForUsers(any());
    }

    /**
     * given: "We have an LdapUser and not a matching CloudstackUser"
     *  when: "isACloudstackUser is executed"
     *  then: "The result is false"
     */
	@Test
    public void isNotACloudstackUser() {
	    doReturn(new ListResponse<UserResponse>()).when(queryService).searchForUsers(any());

		LdapUser ldapUser = new LdapUser("rmurphy", "rmurphy@cloudstack.org", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);

		boolean result = ldapListUsersCmd.isACloudstackUser(ldapUser);

        assertFalse(result);
	}

    /**
     * test whether a value other than 'any' for 'listtype' leads to a good 'userfilter' value
     */
    @Test
    public void getListtypeOther() {
        when(cmdSpy.getListTypeString()).thenReturn("otHer", "anY");
        String userfilter = cmdSpy.getUserFilterString();

        assertEquals("AnyDomain", userfilter);

        // Big no-no: a second test in a test-method; don't do this at home
        userfilter = cmdSpy.getUserFilterString();
        assertEquals("AnyDomain", userfilter);
    }

    /**
     * test whether a value of 'any' for 'listtype' leads to a good 'userfilter' value
     */
    @Test
    public void getListtypeAny() {
        when(cmdSpy.getListTypeString()).thenReturn("any");
        String userfilter = cmdSpy.getUserFilterString();
        assertEquals("NoFilter", userfilter);
    }

    /**
     * test whether values for 'userfilter'
     */
    @Test
    public void getUserFilter() throws NoSuchFieldException, IllegalAccessException {

        when(cmdSpy.getListTypeString()).thenReturn("otHer");
        LdapListUsersCmd.UserFilter userfilter = cmdSpy.getUserFilter();

        assertEquals(LdapListUsersCmd.UserFilter.ANY_DOMAIN, userfilter);

        when(cmdSpy.getListTypeString()).thenReturn("anY");
        userfilter = cmdSpy.getUserFilter();
        assertEquals(LdapListUsersCmd.UserFilter.ANY_DOMAIN, userfilter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInvalidUserFilterValues() throws NoSuchFieldException, IllegalAccessException{
        setHiddenField(ldapListUsersCmd, "userFilter", "flase");
        LdapListUsersCmd.UserFilter userfilter = ldapListUsersCmd.getUserFilter();
    }

    @Test
    public void getUserFilterValues() {
        assertEquals("PotentialImport", LdapListUsersCmd.UserFilter.POTENTIAL_IMPORT.toString());
        assertEquals(LdapListUsersCmd.UserFilter.POTENTIAL_IMPORT, LdapListUsersCmd.UserFilter.fromString("PotentialImport"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInvalidUserFilterStringValue() {
        LdapListUsersCmd.UserFilter.fromString("PotentImport");
    }

    /**
     * apply no filter
     * todo make extensive userlist and check for annotations (usersources)
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Test
    public void applyNoFilter() throws NoSuchFieldException, IllegalAccessException {
        LdapUser ldapUser = new LdapUser("rmurphy", "rmurphy@cloudstack.org", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);
        LdapUserResponse response = new LdapUserResponse("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null);

        setHiddenField(ldapListUsersCmd, "userFilter", "NoFilter");
        ldapListUsersCmd.execute();
    }

    /**
     * todo generate an extensive configuration and check with an extensive user list
     *
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Test
    public void applyPotentialImport() throws NoSuchFieldException, IllegalAccessException {
        LdapUser ldapUser = new LdapUser("rmurphy", "rmurphy@cloudstack.org", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);
        LdapUserResponse response = new LdapUserResponse("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null);

        setHiddenField(ldapListUsersCmd, "userFilter", "NoFilter");
        ldapListUsersCmd.execute();
    }
}
