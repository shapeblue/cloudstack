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

import java.net.InetAddress;
import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.net.NetUtils;

public class DnsProviderUrlValidatorTest {

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateAndResolveDestinationUrlRejectsHttpWhenDisallowed() {
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("http://8.8.8.8/api/v1", false, "", true);
    }

    @Test
    public void testValidateAndResolveDestinationUrlAllowsHttpWhenAllowed() {
        URI resolvedUri = DnsProviderUrlValidator.validateAndResolveDestinationUrl("http://8.8.8.8/api/v1", true, "", true);
        Assert.assertEquals(URI.create("http://8.8.8.8/api/v1"), resolvedUri);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateAndResolveDestinationUrlRejectsBlockedAddress() {
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://127.0.0.1/api/v1", true, "127.0.0.0/8", true);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateAndResolveDestinationUrlRejectsLoopbackWhenNotInBlocklist() {
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://127.0.0.1/api/v1", true, "", true);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateAndResolveDestinationUrlFailsClosedOnResolutionFailure() {
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://nonexistent.invalid/api/v1", true, "", true);
    }

    @Test
    public void testValidateAndResolveDestinationUrlRejectsLocalManagementServerAddressWhenNotInBlocklist() throws Exception {
        InetAddress address = InetAddress.getByName("8.8.8.8");

        try (MockedStatic<NetUtils> netUtilsMock = Mockito.mockStatic(NetUtils.class, Mockito.CALLS_REAL_METHODS)) {
            netUtilsMock.when(() -> NetUtils.isLocalAddress(address)).thenReturn(true);

            try {
                DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://8.8.8.8/api/v1", false, "", true);
                Assert.fail("Expected InvalidParameterValueException");
            } catch (InvalidParameterValueException e) {
                Assert.assertTrue(e.getMessage().contains("blocked IP address"));
                Assert.assertTrue(e.getMessage().contains("8.8.8.8"));
            }
        }
    }

    @Test
    public void testValidateAndResolveDestinationUrlAllowsLocalManagementServerAddressWhenDisabled() throws Exception {
        InetAddress address = InetAddress.getByName("8.8.8.8");

        try (MockedStatic<NetUtils> netUtilsMock = Mockito.mockStatic(NetUtils.class, Mockito.CALLS_REAL_METHODS)) {
            netUtilsMock.when(() -> NetUtils.isLocalAddress(address)).thenReturn(true);

            URI resolvedUri = DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://8.8.8.8/api/v1", false, "", false);
            Assert.assertEquals(URI.create("https://8.8.8.8/api/v1"), resolvedUri);
        }
    }

    @Test
    public void testValidateAndResolveDestinationUrlAcceptsAllowedHttpsIpLiteral() {
        URI resolvedUri = DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://8.8.8.8/api/v1", false,
                "127.0.0.0/8,::1/128", true);
        Assert.assertEquals(URI.create("https://8.8.8.8/api/v1"), resolvedUri);
    }

    @Test
    public void testValidateAndResolveDestinationUrlAllowsLoopbackWhenLocalAddressBlockingDisabled() {
        URI resolvedUri = DnsProviderUrlValidator.validateAndResolveDestinationUrl("https://127.0.0.1/api/v1", true, "", false);
        Assert.assertEquals(URI.create("https://127.0.0.1/api/v1"), resolvedUri);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateAndResolveDestinationUrlRejectsBlankHost() {
        DnsProviderUrlValidator.validateAndResolveDestinationUrl("https:///api/v1", true, "", true);
    }

    @Test
    public void testGetNormalizedBlocklist() {
        String[] cidrs = DnsProviderUrlValidator.getNormalizedBlocklist(" 127.0.0.0/8, ::1/128 ,, 192.168.0.0/16 ");
        Assert.assertArrayEquals(new String[] {"127.0.0.0/8", "::1/128", "192.168.0.0/16"}, cidrs);
    }

    @Test
    public void testGetNormalizedBlocklistEmptyForBlank() {
        Assert.assertArrayEquals(new String[0], DnsProviderUrlValidator.getNormalizedBlocklist("  "));
    }
}
