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

package org.apache.cloudstack.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DnsProviderDeliveryPolicyTest {

    @Test
    public void testForServerMatchesForDomainWhenNotExempt() {
        DnsServer server = mock(DnsServer.class);
        when(server.getDomainId()).thenReturn(1L);
        when(server.getDetail(DnsProviderDeliveryPolicy.PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY)).thenReturn(null);

        DnsProviderDeliveryPolicy policy = DnsProviderDeliveryPolicy.forServer(server);

        assertEquals(DnsProviderDeliveryPolicy.forDomain(1L), policy);
        assertTrue(policy.isBlockLocalAddresses());
    }

    @Test
    public void testForServerMatchesForDomainWhenExplicitlyNotExempt() {
        DnsServer server = mock(DnsServer.class);
        when(server.getDomainId()).thenReturn(1L);
        when(server.getDetail(DnsProviderDeliveryPolicy.PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY)).thenReturn("false");

        DnsProviderDeliveryPolicy policy = DnsProviderDeliveryPolicy.forServer(server);

        assertEquals(DnsProviderDeliveryPolicy.forDomain(1L), policy);
    }

    @Test
    public void testForServerBypassesBlocklistWhenExempt() {
        DnsServer server = mock(DnsServer.class);
        when(server.getDomainId()).thenReturn(1L);
        when(server.getDetail(DnsProviderDeliveryPolicy.PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY)).thenReturn("true");

        DnsProviderDeliveryPolicy policy = DnsProviderDeliveryPolicy.forServer(server);

        assertFalse(policy.isBlockLocalAddresses());
        assertEquals("", policy.getBlocklist());
    }

    @Test
    public void testForServerExemptStillEnforcesScheme() {
        DnsServer server = mock(DnsServer.class);
        when(server.getDomainId()).thenReturn(1L);
        when(server.getDetail(DnsProviderDeliveryPolicy.PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY)).thenReturn("true");

        DnsProviderDeliveryPolicy policy = DnsProviderDeliveryPolicy.forServer(server);

        // Root-admin exemption only lifts the address-based blocklist, not the scheme policy.
        assertEquals(DnsProviderDeliveryPolicy.forDomain(1L).isAllowHttp(), policy.isAllowHttp());
    }

    @Test
    public void testExemptServerCanResolveToPrivateAddress() {
        DnsServer server = mock(DnsServer.class);
        when(server.getDomainId()).thenReturn(1L);
        when(server.getDetail(DnsProviderDeliveryPolicy.PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY)).thenReturn("true");
        DnsProviderDeliveryPolicy policy = DnsProviderDeliveryPolicy.forServer(server);

        // Would throw InvalidParameterValueException if the exemption weren't honoured.
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://192.168.1.5/api/v1",
                policy.isAllowHttp(), policy.getBlocklist(), policy.isBlockLocalAddresses());
    }
}
