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
package org.apache.cloudstack.api.command.admin.storage;

import com.cloud.exception.DiscoveryException;
import com.cloud.storage.StorageService;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

public class AddObjectStoragePoolCmdTest {
    public static final Logger s_logger = Logger.getLogger(AddObjectStoragePoolCmdTest.class.getName());

    @Mock
    private StorageService storageService;

    @InjectMocks
    private AddObjectStoragePoolCmd addObjectStoragePoolCmd = new AddObjectStoragePoolCmd();

    private String name = "testObjStore";

    private String url = "testURL";

    private String provider = "Simulator";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        ReflectionTestUtils.setField(addObjectStoragePoolCmd, "name", name);
        ReflectionTestUtils.setField(addObjectStoragePoolCmd, "url", url);
        ReflectionTestUtils.setField(addObjectStoragePoolCmd, "providerName", provider);
        ReflectionTestUtils.setField(addObjectStoragePoolCmd, "details", new HashMap<>());
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregister();
    }

    @Test
    public void testAddObjectStore() throws DiscoveryException {
        try {
            addObjectStoragePoolCmd.execute();
        } catch (Exception e) {
            Assert.assertEquals("Failed to add object storage", e.getMessage());
        }
        Mockito.verify(storageService, Mockito.times(1))
                .discoverObjectStore(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

}
