/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.api.dispatch;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import org.apache.cloudstack.api.ApiArgValidator;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.context.CallContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
public class ParamProcessWorkerTest {

    @Mock
    protected AccountManager accountManager;

    protected ParamProcessWorker paramProcessWorker;

    public static class TestCmd extends BaseCmd {

        @Parameter(name = "strparam1")
        String strparam1;

        @Parameter(name = "intparam1", type = CommandType.INTEGER)
        int intparam1;

        @Parameter(name = "boolparam1", type = CommandType.BOOLEAN)
        boolean boolparam1;

        @Parameter(name = "doubleparam1", type = CommandType.DOUBLE)
        double doubleparam1;

        @Parameter(name = "vmHostNameParam", type = CommandType.STRING, validations = {ApiArgValidator.RFCComplianceDomainName})
        String vmHostNameParam;

        @Override
        public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
            ResourceAllocationException, NetworkRuleConflictException {
            // well documented nothing
        }

        @Override
        public String getCommandName() {
            return "test";
        }

        @Override
        public long getEntityOwnerId() {
            return 0;
        }

    }

    @Before
    public void setup() {
        CallContext.register(Mockito.mock(User.class), Mockito.mock(Account.class));
        paramProcessWorker = new ParamProcessWorker();
        paramProcessWorker._accountMgr = accountManager;
    }

    @After
    public void cleanup() {
        CallContext.unregister();
    }

    @Test
    public void processParameters() {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("strparam1", "foo");
        params.put("intparam1", "100");
        params.put("boolparam1", "true");
        params.put("doubleparam1", "11.89");
        params.put("vmHostNameParam", "test-host-name-123");
        final TestCmd cmd = new TestCmd();
        paramProcessWorker.processParameters(cmd, params);
        Assert.assertEquals("foo", cmd.strparam1);
        Assert.assertEquals(100, cmd.intparam1);
        Assert.assertTrue(Double.compare(cmd.doubleparam1, 11.89) == 0);
        Assert.assertEquals("test-host-name-123", cmd.vmHostNameParam);
    }

    @Test(expected = ServerApiException.class)
    public void processVmHostNameParameter_CannotStartWithDigit() {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("vmHostNameParam", "123test");
        final TestCmd cmd = new TestCmd();
        paramProcessWorker.processParameters(cmd, params);
    }

    @Test(expected = ServerApiException.class)
    public void processVmHostNameParameter_CannotStartWithHypen() {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("vmHostNameParam", "-test");
        final TestCmd cmd = new TestCmd();
        paramProcessWorker.processParameters(cmd, params);
    }

    @Test(expected = ServerApiException.class)
    public void processVmHostNameParameter_CannotEndWithHypen() {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("vmHostNameParam", "test-");
        final TestCmd cmd = new TestCmd();
        paramProcessWorker.processParameters(cmd, params);
    }

    @Test(expected = ServerApiException.class)
    public void processVmHostNameParameter_NotMoreThan63Chars() {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("vmHostNameParam", "test-f2405112-d5a1-47c1-9f00-976909e3a6d3-1e6f3264-955ee76011a99");
        final TestCmd cmd = new TestCmd();
        paramProcessWorker.processParameters(cmd, params);
    }
}
