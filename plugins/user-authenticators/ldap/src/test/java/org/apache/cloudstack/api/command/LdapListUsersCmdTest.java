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

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        ldapListUsersCmd = spy(new LdapListUsersCmd(ldapManager, queryService));
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
		List<LdapUser> users = new ArrayList();
		LdapUser murphy = new LdapUser("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);
		users.add(murphy);

		doReturn(users).when(ldapManager).getUsers(null);

		LdapUserResponse response = new LdapUserResponse("rmurphy", "rmurphy@test.com", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null);
		doReturn(response).when(ldapManager).createLdapUserResponse(murphy);

		ldapListUsersCmd.execute();

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
		UserResponse userResponse = new UserResponse();
		userResponse.setUsername("rmurphy");

		ArrayList<UserResponse> responses = new ArrayList<UserResponse>();
		responses.add(userResponse);

		ListResponse<UserResponse> queryServiceResponse = new ListResponse<UserResponse>();
		queryServiceResponse.setResponses(responses);

		doReturn(queryServiceResponse).when(queryService).searchForUsers(any());

		LdapUser ldapUser = new LdapUser("rmurphy", "rmurphy@cloudstack.org", "Ryan", "Murphy", "cn=rmurphy,dc=cloudstack,dc=org", null, false, null);

		boolean result = ldapListUsersCmd.isACloudstackUser(ldapUser);

		assertTrue(result);
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
}
