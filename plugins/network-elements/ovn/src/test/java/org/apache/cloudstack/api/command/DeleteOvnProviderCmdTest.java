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
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.service.OvnProviderService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

public class DeleteOvnProviderCmdTest {
    @Mock
    private OvnProviderService ovnProviderService;

    @InjectMocks
    private DeleteOvnProviderCmd cmd;

    private AutoCloseable closeable;
    private static final long PROVIDER_ID = 1L;

    @Before
    public void setup() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);
        setPrivateField("id", PROVIDER_ID);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testExecuteSuccess() throws ConcurrentOperationException {
        Mockito.when(ovnProviderService.deleteOvnProvider(PROVIDER_ID)).thenReturn(true);

        cmd.execute();

        Mockito.verify(ovnProviderService).deleteOvnProvider(PROVIDER_ID);
        Assert.assertTrue(cmd.getResponseObject() instanceof SuccessResponse);
        SuccessResponse response = (SuccessResponse) cmd.getResponseObject();
        Assert.assertEquals(cmd.getCommandName(), response.getResponseName());
    }

    @Test(expected = ServerApiException.class)
    public void testExecuteFailure() throws ConcurrentOperationException {
        Mockito.when(ovnProviderService.deleteOvnProvider(PROVIDER_ID)).thenReturn(false);
        cmd.execute();
    }

    @Test(expected = ServerApiException.class)
    public void testExecuteInvalidParameterException() throws ConcurrentOperationException {
        Mockito.when(ovnProviderService.deleteOvnProvider(PROVIDER_ID)).thenThrow(new InvalidParameterValueException("invalid"));
        cmd.execute();
    }

    @Test(expected = ServerApiException.class)
    public void testExecuteCloudRuntimeException() throws ConcurrentOperationException {
        Mockito.when(ovnProviderService.deleteOvnProvider(PROVIDER_ID)).thenThrow(new CloudRuntimeException("runtime"));
        cmd.execute();
    }

    @Test
    public void testGetEntityOwnerId() {
        Assert.assertEquals(0L, cmd.getEntityOwnerId());
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field field = DeleteOvnProviderCmd.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(cmd, value);
    }
}
