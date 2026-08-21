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
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.commons.lang3.StringUtils;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.net.NetUtils;

/**
 * Resolves and validates the destination of a {@link DnsProvider} API URL immediately before each
 * outbound call, closing the window between "the hostname was checked" and "the hostname was used"
 * that a one-time, registration-only check cannot: a hostname can be repointed to a different
 * address (including a private/internal one) at any time after a DNS server is registered.
 *
 * <p>Shared across every {@link DnsProvider} implementation (PowerDNS, and any future provider)
 * rather than duplicated per-provider, since the resolution/validation logic and the policy it is
 * governed by have nothing PowerDNS/Cloudflare/Route53-specific about them. The config keys are
 * registered once, by {@link DnsProviderManager}; individual providers only read them.
 */
public final class DnsProviderUrlValidator {

    public static final ConfigKey<String> DnsProviderUrlBlocklist = new ConfigKey<>("Advanced", String.class,
            "dns.provider.url.blocklist",
            "0.0.0.0/8,10.0.0.0/8,100.64.0.0/10,127.0.0.0/8,169.254.0.0/16,172.16.0.0/12,"
                    + "192.0.0.0/24,192.0.2.0/24,192.88.99.0/24,192.168.0.0/16,198.18.0.0/15,"
                    + "198.51.100.0/24,203.0.113.0/24,224.0.0.0/4,240.0.0.0/4,"
                    + "::1/128,::/128,::ffff:0:0/96,64:ff9b::/96,64:ff9b:1::/48,100::/64,"
                    + "2001::/32,2001:db8::/32,2002::/16,fc00::/7,fe80::/10,ff00::/8",
            "Comma-separated list of IPv4/IPv6 CIDR ranges that a DNS provider API URL is not allowed "
                    + "to resolve to. Validated against the resolved destination IP addresses on every request.",
            true, ConfigKey.Scope.Domain);

    public static final ConfigKey<Boolean> DnsProviderBlockLocalAddresses = new ConfigKey<>("Hidden", Boolean.class,
            "dns.provider.block.local.addresses", "true",
            "Whether DNS provider API requests are prohibited from accessing IP addresses assigned to "
                    + "the local management server. Validated against resolved destination IP addresses.",
            true, ConfigKey.Scope.Global);

    public static final ConfigKey<Boolean> DnsProviderAllowHttp = new ConfigKey<>("Advanced", Boolean.class,
            "dns.provider.allow.http", "true",
            "Whether unencrypted HTTP URLs are allowed as DNS provider API endpoints. Defaults to true "
                    + "to preserve existing behaviour; set to false to require HTTPS for all DNS provider URLs.",
            true, ConfigKey.Scope.Domain);

    private DnsProviderUrlValidator() {
    }

    public static URI validateAndResolveDestinationUrl(final String url, final boolean allowHttp,
            final String blocklist, final boolean blockLocalAddresses) {
        return validateAndResolveDestinationUri(URI.create(url), allowHttp, blocklist, blockLocalAddresses);
    }

    /**
     * @return a URI equivalent to {@code uri} but with its host replaced by one resolved, validated
     * address, so the connection that is subsequently made targets exactly the address that was checked.
     */
    public static URI validateAndResolveDestinationUri(final URI uri, final boolean allowHttp,
            final String blocklist, final boolean blockLocalAddresses) {
        validateScheme(uri, allowHttp);
        InetAddress[] resolved = validateResolvedAddresses(uri, blocklist, blockLocalAddresses);
        return buildResolvedUri(uri, resolved[0]);
    }

    private static URI buildResolvedUri(final URI uri, final InetAddress address) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), address.getHostAddress(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new InvalidParameterValueException(
                    String.format("Failed to build resolved DNS provider API URL from [%s]", uri));
        }
    }

    public static void validateScheme(final URI uri, final boolean allowHttp) {
        final String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if (allowHttp && "http".equalsIgnoreCase(scheme)) {
            return;
        }
        if (allowHttp) {
            throw new InvalidParameterValueException(
                    String.format("Unsupported DNS provider API URL scheme [%s], only HTTP/HTTPS are supported", scheme));
        }
        throw new InvalidParameterValueException(
                String.format("Only HTTPS DNS provider API URLs are allowed, got: %s", uri));
    }

    /**
     * A hostname can resolve to more than one address (multiple A/AAAA records); every one of them
     * has to clear the blocklist, since the provider's own DNS resolution (not ours) picks which one is used.
     */
    public static InetAddress[] validateResolvedAddresses(final URI uri, final String blocklist,
            final boolean blockLocalAddresses) {
        final String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new InvalidParameterValueException(
                    String.format("Invalid DNS provider API URL host in [%s]", uri));
        }

        final InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new InvalidParameterValueException(
                    String.format("Failed to resolve DNS provider API URL host [%s]", host));
        }

        if (resolved.length == 0) {
            throw new InvalidParameterValueException(
                    String.format("Failed to resolve DNS provider API URL host [%s]", host));
        }

        final String[] blockedCidrs = getNormalizedBlocklist(blocklist);
        for (InetAddress address : resolved) {
            if ((blockLocalAddresses && isLocalManagementServerAddress(address)) ||
                    NetUtils.isIpInCidrList(address, blockedCidrs)) {
                throw new InvalidParameterValueException(
                        String.format("DNS provider API URL [%s] resolves to a blocked IP address", uri));
            }
        }
        return resolved;
    }

    static boolean isLocalManagementServerAddress(final InetAddress address) {
        return address != null
                && (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || NetUtils.isLocalAddress(address));
    }

    public static String[] getNormalizedBlocklist(final String blocklist) {
        if (StringUtils.isBlank(blocklist)) {
            return new String[0];
        }
        return Arrays.stream(blocklist.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
    }
}
