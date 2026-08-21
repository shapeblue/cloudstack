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

import java.util.Objects;

/**
 * The destination-validation policy applied to every outbound {@link DnsProvider} API call,
 * resolved fresh from domain-scoped configuration for each request rather than cached on a client.
 *
 * <p>Every {@link DnsProvider} implementation builds this the same way, via {@link #forServer(DnsServer)} -
 * there is nothing provider-specific about the policy itself, so a new provider (Cloudflare, Route53,
 * etc.) gets the same destination-validation behaviour for free instead of reimplementing it.
 */
public class DnsProviderDeliveryPolicy {

    /**
     * Detail key, set on a {@link DnsServer} at registration/update time, recording whether a root
     * admin approved this specific server's URL - root admin has always been allowed to point a DNS
     * server at a private/internal address (see the registration-time check), and that has to keep
     * being true for every later connection this server makes, not just the one at registration.
     * Without this, a root-admin-configured private DNS server would pass registration but then fail
     * every subsequent operation once the address-based blocklist below is enforced per-call.
     */
    public static final String PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY = "dnsProviderPrivateAddressExempt";

    private final boolean allowHttp;
    private final String blocklist;
    private final boolean blockLocalAddresses;

    public DnsProviderDeliveryPolicy(boolean allowHttp, String blocklist, boolean blockLocalAddresses) {
        this.allowHttp = allowHttp;
        this.blocklist = blocklist;
        this.blockLocalAddresses = blockLocalAddresses;
    }

    public static DnsProviderDeliveryPolicy forDomain(long domainId) {
        return new DnsProviderDeliveryPolicy(
                DnsProviderUrlValidator.DnsProviderAllowHttp.valueIn(domainId),
                DnsProviderUrlValidator.DnsProviderUrlBlocklist.valueIn(domainId),
                DnsProviderUrlValidator.DnsProviderBlockLocalAddresses.value());
    }

    /**
     * Like {@link #forDomain(long)}, but honours {@link #PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY} on
     * {@code server}: a server a root admin approved against a private/internal address is exempt
     * from the address-based blocklist on every call, not just at registration.
     */
    public static DnsProviderDeliveryPolicy forServer(DnsServer server) {
        DnsProviderDeliveryPolicy domainPolicy = forDomain(server.getDomainId());
        if (Boolean.parseBoolean(server.getDetail(PRIVATE_ADDRESS_EXEMPT_DETAIL_KEY))) {
            return new DnsProviderDeliveryPolicy(domainPolicy.isAllowHttp(), "", false);
        }
        return domainPolicy;
    }

    public boolean isAllowHttp() {
        return allowHttp;
    }

    public String getBlocklist() {
        return blocklist;
    }

    public boolean isBlockLocalAddresses() {
        return blockLocalAddresses;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsProviderDeliveryPolicy)) {
            return false;
        }
        DnsProviderDeliveryPolicy that = (DnsProviderDeliveryPolicy) obj;
        return allowHttp == that.allowHttp
                && blockLocalAddresses == that.blockLocalAddresses
                && Objects.equals(blocklist, that.blocklist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowHttp, blocklist, blockLocalAddresses);
    }
}
