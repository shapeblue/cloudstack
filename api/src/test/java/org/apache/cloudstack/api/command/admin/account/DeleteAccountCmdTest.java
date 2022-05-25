package org.apache.cloudstack.api.command.admin.account;


import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.User;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.region.RegionService;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;


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
public class DeleteAccountCmdTest {

    public static final Logger s_logger = Logger.getLogger(DeleteAccountCmd.class.getName());

    @Mock
    AccountService accountService;

    @Mock
    RegionService regionService;
    @InjectMocks
    private DeleteAccountCmd deleteAccountCmd = new DeleteAccountCmd();

    public static final Long accountId = 1L;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        CallContext.register(Mockito.mock(User.class), Mockito.mock(Account.class));
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregister();
    }

    @Test
    public void deleteAccountWithoutIdTest() {
        Mockito.when(accountService.getAccount(ArgumentMatchers.anyLong())).thenReturn(null);
        try {
            deleteAccountCmd.execute();
            Assert.fail("Test should fail as account id is not passed");
        } catch (Exception e) {
        }
        Mockito.verify(regionService, Mockito.never()).deleteUserAccount(deleteAccountCmd);
    }

    @Test
    public void deleteAccountTest() {
        ReflectionTestUtils.setField(deleteAccountCmd, "id", accountId);
        Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getUuid()).thenReturn("uuid");
        Mockito.when(accountService.getAccount(ArgumentMatchers.anyLong())).thenReturn(account);
        try {
            deleteAccountCmd.execute();
        } catch (Exception e) {
            System.out.println(e);
            Assert.fail("Should successfully delete the specified account");
        }
        Mockito.verify(regionService, Mockito.never()).deleteUserAccount(deleteAccountCmd);
    }


}