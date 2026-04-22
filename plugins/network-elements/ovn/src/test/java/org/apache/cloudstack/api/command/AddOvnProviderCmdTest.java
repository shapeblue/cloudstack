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

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.network.ovn.OvnProvider;
import com.cloud.user.Account;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.OvnProviderResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.service.OvnProviderService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AddOvnProviderCmdTest {
    @Mock
    private OvnProviderService ovnProviderService;
    @Mock
    private CallContext callContext;

    private MockedStatic<CallContext> callContextMockedStatic;

    @InjectMocks
    private AddOvnProviderCmd cmd;

    private AutoCloseable closeable;

    @Before
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        callContextMockedStatic = Mockito.mockStatic(CallContext.class);
        callContextMockedStatic.when(CallContext::current).thenReturn(callContext);
    }

    @After
    public void tearDown() throws Exception {
        callContextMockedStatic.close();
        closeable.close();
    }

    @Test
    public void testExecuteSuccess() throws ConcurrentOperationException {
        OvnProvider provider = Mockito.mock(OvnProvider.class);
        OvnProviderResponse response = Mockito.mock(OvnProviderResponse.class);
        Mockito.when(ovnProviderService.addProvider(cmd)).thenReturn(provider);
        Mockito.when(ovnProviderService.createOvnProviderResponse(provider)).thenReturn(response);

        cmd.execute();

        Mockito.verify(ovnProviderService).addProvider(cmd);
        Mockito.verify(ovnProviderService).createOvnProviderResponse(provider);
        Mockito.verify(response).setResponseName(cmd.getCommandName());
        Assert.assertEquals(response, cmd.getResponseObject());
    }

    @Test(expected = ServerApiException.class)
    public void testExecuteFailure() throws ConcurrentOperationException {
        OvnProvider provider = Mockito.mock(OvnProvider.class);
        Mockito.when(ovnProviderService.addProvider(cmd)).thenReturn(provider);
        Mockito.when(ovnProviderService.createOvnProviderResponse(provider)).thenReturn(null);

        cmd.execute();
    }

    @Test
    public void testGetEntityOwnerId() {
        Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(123L);
        Mockito.when(callContext.getCallingAccount()).thenReturn(account);

        Assert.assertEquals(123L, cmd.getEntityOwnerId());
    }
}
