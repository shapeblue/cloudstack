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

import com.cloud.host.Host;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.ImageStore;
import com.cloud.storage.ScopeType;
import com.cloud.storage.Storage;
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

import static org.junit.Assert.assertEquals;
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
    DataStore dataStore1;
    @Mock
    DataStore dataStore2;
    @Mock
    DataStore dataStore3;
    @Mock
    StoragePoolVO pool1;
    @Mock
    StoragePoolVO pool2;
    @Mock
    StoragePoolVO pool3;
    @Mock
    StoragePoolVO pool4;
    @Mock
    Host host1;
    @Mock
    Host host2;

    Map<VolumeInfo, DataStore> migrationMap;

    private static final Long POOL_1_ID = 1L;
    private static final Long POOL_2_ID = 2L;
    private static final Long POOL_3_ID = 3L;
    private static final Long POOL_4_ID = 4L;
    private static final Long HOST_1_ID = 1L;
    private static final Long HOST_2_ID = 2L;
    private static final Long CLUSTER_ID = 1L;

    @Before public void setUp() throws Exception {
        sourceStore = mock(PrimaryDataStoreImpl.class);
        destinationStore = mock(ImageStoreImpl.class);
        source = mock(VolumeObject.class);
        destination = mock(VolumeObject.class);

                initMocks(strategy);

        migrationMap = new HashMap<>();
        migrationMap.put(volumeInfo1, dataStore2);
        migrationMap.put(volumeInfo2, dataStore2);

        when(volumeInfo1.getPoolId()).thenReturn(POOL_1_ID);
        when(_storagePoolDao.findById(POOL_1_ID)).thenReturn(pool1);
        when(pool1.isManaged()).thenReturn(false);
        when(dataStore2.getId()).thenReturn(POOL_2_ID);
        when(_storagePoolDao.findById(POOL_2_ID)).thenReturn(pool2);
        when(pool2.isManaged()).thenReturn(true);
        when(volumeInfo1.getDataStore()).thenReturn(dataStore1);

        when(volumeInfo2.getPoolId()).thenReturn(POOL_1_ID);
        when(volumeInfo2.getDataStore()).thenReturn(dataStore1);

        when(host1.getId()).thenReturn(HOST_1_ID);
        when(host1.getClusterId()).thenReturn(CLUSTER_ID);
        when(host1.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        when(host2.getId()).thenReturn(HOST_2_ID);
        when(host2.getClusterId()).thenReturn(CLUSTER_ID);
        when(host2.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);

        when(dataStore1.getId()).thenReturn(POOL_1_ID);
        when(pool1.getPoolType()).thenReturn(Storage.StoragePoolType.NetworkFilesystem);
        when(pool2.getPoolType()).thenReturn(Storage.StoragePoolType.NetworkFilesystem);
        when(pool2.getScope()).thenReturn(ScopeType.CLUSTER);

        when(dataStore3.getId()).thenReturn(POOL_3_ID);
        when(_storagePoolDao.findById(POOL_3_ID)).thenReturn(pool3);
        when(pool3.getPoolType()).thenReturn(Storage.StoragePoolType.NetworkFilesystem);
        when(pool3.getScope()).thenReturn(ScopeType.CLUSTER);
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
    public void testVerifyLiveMigrationMapForKVM() {
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationForKVM(migrationMap, host2);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMNotExistingSource() {
        when(_storagePoolDao.findById(POOL_1_ID)).thenReturn(null);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationForKVM(migrationMap, host2);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMNotExistingDest() {
        when(_storagePoolDao.findById(POOL_2_ID)).thenReturn(null);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationForKVM(migrationMap, host2);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testVerifyLiveMigrationMapForKVMMixedManagedUnmagedStorage() {
        when(pool1.isManaged()).thenReturn(true);
        when(pool2.isManaged()).thenReturn(false);
        ((StorageSystemDataMotionStrategy) strategy).verifyLiveMigrationForKVM(migrationMap, host2);
    }

    @Test
    public void canHandleKVMLiveStorageMigrationSameHost() {
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        StrategyPriority priority = st.canHandleKVMNonManagedLiveStorageMigration(migrationMap, host1, host1);
        assertEquals(StrategyPriority.CANT_HANDLE, priority);
    }

    @Test
    public void canHandleKVMLiveStorageMigrationInterCluster() {
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        when(host2.getClusterId()).thenReturn(5L);
        StrategyPriority priority = st.canHandleKVMNonManagedLiveStorageMigration(migrationMap, host1, host2);
        assertEquals(StrategyPriority.CANT_HANDLE, priority);
    }

    @Test
    public void canHandleKVMLiveStorageMigration() {
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        StrategyPriority priority = st.canHandleKVMNonManagedLiveStorageMigration(migrationMap, host1, host2);
        assertEquals(StrategyPriority.HIGHEST, priority);
    }

    @Test
    public void canHandleKVMLiveStorageMigrationMultipleSources() {
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        when(volumeInfo1.getDataStore()).thenReturn(dataStore2);
        StrategyPriority priority = st.canHandleKVMNonManagedLiveStorageMigration(migrationMap, host1, host2);
        assertEquals(StrategyPriority.HIGHEST, priority);
    }

    @Test
    public void canHandleKVMLiveStorageMigrationMultipleDestination() {
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        migrationMap.put(volumeInfo2, dataStore3);
        StrategyPriority priority = st.canHandleKVMNonManagedLiveStorageMigration(migrationMap, host1, host2);
        assertEquals(StrategyPriority.HIGHEST, priority);
    }

    @Test
    public void testCanHandleLiveMigrationUnmanagedStorage() {
        when(pool2.isManaged()).thenReturn(false);
        StorageSystemDataMotionStrategy st = ((StorageSystemDataMotionStrategy) strategy);
        StrategyPriority priority = st.canHandle(migrationMap, host1, host2);
        assertEquals(StrategyPriority.HIGHEST, priority);
    }
}