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
package org.apache.cloudstack.service;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.OvnProviderDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.element.OvnProviderVO;
import com.cloud.network.ovn.OvnService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.command.AddOvnProviderCmd;
import org.apache.cloudstack.api.response.OvnProviderResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class OvnProviderServiceImplTest {
    @Mock
    private DataCenterDao dataCenterDao;
    @Mock
    private OvnProviderDao ovnProviderDao;
    @Mock
    private PhysicalNetworkDao physicalNetworkDao;
    @Mock
    private NetworkDao networkDao;
    @Mock
    private OvnService ovnService;

    @InjectMocks
    private OvnProviderServiceImpl ovnProviderService;

    private AutoCloseable closeable;
    private MockedStatic<Transaction> transactionMockedStatic;

    private static final long ZONE_ID = 1L;
    private static final long PROVIDER_ID = 3L;
    private static final String NAME = "test-ovn";
    private static final String NB_CONNECTION = "tcp:127.0.0.1:6641";
    private static final String SB_CONNECTION = "tcp:127.0.0.1:6642";

    @Before
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        transactionMockedStatic = Mockito.mockStatic(Transaction.class);
    }

    @After
    public void tearDown() throws Exception {
        transactionMockedStatic.close();
        closeable.close();
    }

    @Test
    public void testAddProviderPersistsProvider() throws Exception {
        AddOvnProviderCmd cmd = new AddOvnProviderCmd();
        setPrivateField(cmd, "zoneId", ZONE_ID);
        setPrivateField(cmd, "name", NAME);
        setPrivateField(cmd, "nbConnection", NB_CONNECTION);
        setPrivateField(cmd, "sbConnection", SB_CONNECTION);

        Mockito.when(dataCenterDao.findById(ZONE_ID)).thenReturn(Mockito.mock(DataCenterVO.class));
        Mockito.when(ovnProviderDao.findByZoneId(ZONE_ID)).thenReturn(null);
        Mockito.when(ovnService.isValidConnectionString(NB_CONNECTION)).thenReturn(true);
        Mockito.when(ovnService.isValidConnectionString(SB_CONNECTION)).thenReturn(true);
        Mockito.when(ovnProviderDao.persist(Mockito.any(OvnProviderVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        transactionMockedStatic.when(() -> Transaction.execute(Mockito.<TransactionCallback<OvnProviderVO>>any())).thenAnswer(invocation -> {
            TransactionCallback<OvnProviderVO> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        OvnProviderVO provider = (OvnProviderVO) ovnProviderService.addProvider(cmd);

        Assert.assertEquals(ZONE_ID, provider.getZoneId());
        Assert.assertEquals(NAME, provider.getName());
        Assert.assertEquals(NB_CONNECTION, provider.getNbConnection());
        Assert.assertEquals(SB_CONNECTION, provider.getSbConnection());
        Mockito.verify(ovnProviderDao).persist(Mockito.any(OvnProviderVO.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testAddProviderRejectsInvalidNbConnection() throws Exception {
        AddOvnProviderCmd cmd = new AddOvnProviderCmd();
        setPrivateField(cmd, "zoneId", ZONE_ID);
        setPrivateField(cmd, "name", NAME);
        setPrivateField(cmd, "nbConnection", "invalid");
        Mockito.when(dataCenterDao.findById(ZONE_ID)).thenReturn(Mockito.mock(DataCenterVO.class));
        Mockito.when(ovnService.isValidConnectionString("invalid")).thenReturn(false);

        ovnProviderService.addProvider(cmd);
    }

    @Test
    public void testListOvnProvidersWithZoneId() {
        OvnProviderVO providerVO = Mockito.mock(OvnProviderVO.class);
        Mockito.when(ovnProviderDao.findByZoneId(ZONE_ID)).thenReturn(providerVO);
        Mockito.when(providerVO.getZoneId()).thenReturn(ZONE_ID);
        Mockito.when(dataCenterDao.findById(ZONE_ID)).thenReturn(getZone());

        List<BaseResponse> result = ovnProviderService.listOvnProviders(ZONE_ID);

        Assert.assertEquals(1, result.size());
        Assert.assertTrue(result.get(0) instanceof OvnProviderResponse);
    }

    @Test
    public void testDeleteOvnProviderSuccess() {
        OvnProviderVO providerVO = Mockito.mock(OvnProviderVO.class);
        Mockito.when(providerVO.getZoneId()).thenReturn(ZONE_ID);
        Mockito.when(ovnProviderDao.findById(PROVIDER_ID)).thenReturn(providerVO);
        Mockito.when(physicalNetworkDao.listByZone(ZONE_ID)).thenReturn(Arrays.asList(Mockito.mock(PhysicalNetworkVO.class)));

        NetworkVO network = Mockito.mock(NetworkVO.class);
        Mockito.when(networkDao.listByPhysicalNetwork(Mockito.anyLong())).thenReturn(Arrays.asList(network));
        Mockito.when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.OVN);
        Mockito.when(network.getState()).thenReturn(Network.State.Shutdown);

        Assert.assertTrue(ovnProviderService.deleteOvnProvider(PROVIDER_ID));
        Mockito.verify(ovnProviderDao).remove(PROVIDER_ID);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testDeleteOvnProviderWithActiveNetworks() {
        OvnProviderVO providerVO = Mockito.mock(OvnProviderVO.class);
        Mockito.when(providerVO.getZoneId()).thenReturn(ZONE_ID);
        Mockito.when(ovnProviderDao.findById(PROVIDER_ID)).thenReturn(providerVO);
        Mockito.when(physicalNetworkDao.listByZone(ZONE_ID)).thenReturn(Arrays.asList(Mockito.mock(PhysicalNetworkVO.class)));

        NetworkVO network = Mockito.mock(NetworkVO.class);
        Mockito.when(networkDao.listByPhysicalNetwork(Mockito.anyLong())).thenReturn(Arrays.asList(network));
        Mockito.when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.OVN);
        Mockito.when(network.getState()).thenReturn(Network.State.Implemented);

        ovnProviderService.deleteOvnProvider(PROVIDER_ID);
    }

    @Test
    public void testCreateOvnProviderResponse() {
        OvnProviderVO provider = Mockito.mock(OvnProviderVO.class);
        Mockito.when(provider.getZoneId()).thenReturn(ZONE_ID);
        Mockito.when(provider.getName()).thenReturn(NAME);
        Mockito.when(provider.getNbConnection()).thenReturn(NB_CONNECTION);
        Mockito.when(provider.getSbConnection()).thenReturn(SB_CONNECTION);
        Mockito.when(dataCenterDao.findById(ZONE_ID)).thenReturn(getZone());

        OvnProviderResponse response = ovnProviderService.createOvnProviderResponse(provider);

        Assert.assertNotNull(response);
        Assert.assertEquals(NAME, response.getName());
        Assert.assertEquals(NB_CONNECTION, response.getNbConnection());
        Assert.assertEquals(SB_CONNECTION, response.getSbConnection());
    }

    private DataCenterVO getZone() {
        DataCenterVO zone = Mockito.mock(DataCenterVO.class);
        Mockito.when(zone.getName()).thenReturn("test-zone");
        Mockito.when(zone.getUuid()).thenReturn("zone-uuid");
        return zone;
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
