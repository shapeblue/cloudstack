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
package org.apache.cloudstack.storage.motion;

import com.cloud.storage.DataStoreRole;
import com.cloud.storage.ImageStore;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.engine.subsystem.api.storage.DataMotionStrategy;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.StrategyPriority;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.storage.datastore.PrimaryDataStoreImpl;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.image.store.ImageStoreImpl;
import org.apache.cloudstack.storage.volume.VolumeObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class StorageSystemDataMotionStrategyTest {

    @Mock
    VolumeObject source;
    @Mock
    DataObject destination;
    @Mock
    PrimaryDataStore sourceStore;
    @Mock
    ImageStore destinationStore;

    @InjectMocks
    DataMotionStrategy strategy = new StorageSystemDataMotionStrategy();
    @Mock
    PrimaryDataStoreDao _storagePoolDao;

    @Mock
    VolumeInfo volumeInfo1;
    @Mock
    VolumeInfo volumeInfo2;
    @Mock
    DataStore destDataStore3;
    @Mock
    DataStore destDataStore4;
    @Mock
    StoragePoolVO srcPool1;
    @Mock
    StoragePoolVO srcPool2;
    @Mock
    StoragePoolVO destPool3;
    @Mock
    StoragePoolVO destPool4;

    Map<VolumeInfo, DataStore> migrationMap;

    private static final Long SRC_POOL_1_ID = 1L;
    private static final Long SRC_POOL_2_ID = 2L;
    private static final Long DEST_POOL_3_ID = 3L;
    private static final Long DEST_POOL_4_ID = 4L;

    @Before public void setUp() throws Exception {
        sourceStore = mock(PrimaryDataStoreImpl.class);
        destinationStore = mock(ImageStoreImpl.class);
        source = mock(VolumeObject.class);
        destination = mock(VolumeObject.class);

        initMocks(strategy);

        migrationMap = new HashMap<>();
        migrationMap.put(volumeInfo1, destDataStore3);
        migrationMap.put(volumeInfo2, destDataStore4);

        when(volumeInfo1.getPoolId()).thenReturn(SRC_POOL_1_ID);
        when(_storagePoolDao.findById(SRC_POOL_1_ID)).thenReturn(srcPool1);
        when(srcPool1.isManaged()).thenReturn(false);
        when(destDataStore3.getId()).thenReturn(DEST_POOL_3_ID);
        when(_storagePoolDao.findById(DEST_POOL_3_ID)).thenReturn(destPool3);
        when(destPool3.isManaged()).thenReturn(true);

        when(volumeInfo2.getPoolId()).thenReturn(SRC_POOL_2_ID);
        when(_storagePoolDao.findById(SRC_POOL_2_ID)).thenReturn(srcPool2);
        when(srcPool2.isManaged()).thenReturn(false);
        when(destDataStore4.getId()).thenReturn(DEST_POOL_4_ID);
        when(_storagePoolDao.findById(DEST_POOL_4_ID)).thenReturn(destPool4);
        when(destPool4.isManaged()).thenReturn(true);
    }

    @Test
    public void cantHandleSecondary() {
        doReturn(sourceStore).when(source).getDataStore();
        doReturn(DataStoreRole.Primary).when(sourceStore).getRole();
        doReturn(destinationStore).when(destination).getDataStore();
        doReturn(DataStoreRole.Image).when((DataStore)destinationStore).getRole();
        doReturn(sourceStore).when(source).getDataStore();
        doReturn(destinationStore).when(destination).getDataStore();
        StoragePoolVO storeVO = new StoragePoolVO();
        doReturn(storeVO).when(_storagePoolDao).findById(0l);

        assertTrue(strategy.canHandle(source,destination) == StrategyPriority.CANT_HANDLE);
    }

    @Test
    public void testVerifyLiveMigrationMapForKVMAllManagedOrAllNotManagedDestStorage() {
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationMapForKVM(migrationMap);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMAllManagedOrAllNotManagedDestStorageNotExistingSource() {
        when(_storagePoolDao.findById(SRC_POOL_1_ID)).thenReturn(null);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationMapForKVM(migrationMap);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMAllManagedOrAllNotManagedDestStorageNotExistingDest() {
        when(_storagePoolDao.findById(DEST_POOL_3_ID)).thenReturn(null);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationMapForKVM(migrationMap);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMAllManagedOrAllNotManagedDestStorageManagedSource() {
        when(srcPool2.isManaged()).thenReturn(true);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationMapForKVM(migrationMap);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMMixedManagedUnmagedStorage() {
        when(srcPool1.isManaged()).thenReturn(true);
        when(srcPool2.isManaged()).thenReturn(false);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationMapForKVM(migrationMap);
    }
}