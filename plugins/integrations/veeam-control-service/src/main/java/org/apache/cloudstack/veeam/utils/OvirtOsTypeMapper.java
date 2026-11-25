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

package org.apache.cloudstack.veeam.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cloudstack.veeam.VeeamControlService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps a CloudStack guest OS display name to the oVirt OS type key (os.type) that Veeam expects.
 * <p>
 * Keys are those of the
 * <a href="https://github.com/oVirt/ovirt-engine/blob/master/packaging/conf/osinfo-defaults.properties">
 * oVirt OS defaults</a>, limited to the ones Veeam recognizes. Veeam uses the key to tell Windows from
 * Linux guests, e.g. to pick the mount server for file-level restore.
 * </p>
 * Operator overrides from {@link VeeamControlService#GuestOsTypeOverrides} take precedence over the
 * built-in rules.
 */
public final class OvirtOsTypeMapper {
    private static final Logger LOGGER = LogManager.getLogger(OvirtOsTypeMapper.class);

    public static final String OTHER = "other";
    public static final String OTHER_LINUX = "other_linux";

    private static final Pattern BITS_64 = Pattern.compile("64[ -]?bit");
    private static final Pattern BITS_32 = Pattern.compile("32[ -]?bit");
    private static final Pattern BITS = Pattern.compile("\\((32|64)[ -]?bit\\)|(32|64)[ -]?bit");
    private static final Pattern VERSION = Pattern.compile("(?<![\\d.])(\\d{1,6})(?:\\.(\\d{1,6}))?(?!\\d)");
    private static final Pattern WINDOWS_11 = Pattern.compile("windows 11\\b");
    private static final Pattern WINDOWS_10 = Pattern.compile("windows 10\\b");
    private static final Pattern WINDOWS_8 = Pattern.compile("windows 8(\\.1)?\\b");
    private static final Pattern WINDOWS_7 = Pattern.compile("windows (7|vista)\\b");
    private static final Pattern WINDOWS_XP = Pattern.compile("windows (xp|2000|nt|9[58]|3\\.1)\\b");
    private static final Pattern GENERIC_LINUX = Pattern.compile("linux|kernel|asianux|coreos|fedora|gooroom|mandr");

    private static volatile String cachedOverridesValue;
    private static volatile Map<String, String> cachedOverrides = Collections.emptyMap();

    public enum Family { WINDOWS, LINUX, OTHER }

    private OvirtOsTypeMapper() {
    }

    /**
     * Returns the oVirt OS type for the guest OS name, applying the configured overrides first.
     */
    public static String getOsType(final String guestOsName) {
        return getOsType(guestOsName, VeeamControlService.GuestOsTypeOverrides.value());
    }

    static String getOsType(final String guestOsName, final String overridesValue) {
        if (StringUtils.isNotBlank(guestOsName)) {
            final String override = getOverrides(overridesValue).get(normalizeName(guestOsName));
            if (override != null) {
                return override;
            }
        }
        return inferOsType(guestOsName);
    }

    /**
     * Returns the OS family of an oVirt OS type: Windows for windows_* keys, other for other, freebsd* and
     * solaris* keys, Linux for the rest.
     */
    public static Family getFamily(final String osType) {
        if (StringUtils.isBlank(osType)) {
            return Family.OTHER;
        }
        final String type = osType.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("windows")) {
            return Family.WINDOWS;
        }
        if (type.equals(OTHER) || type.startsWith("freebsd") || type.startsWith("solaris")) {
            return Family.OTHER;
        }
        return Family.LINUX;
    }

    /**
     * Parses semicolon- or newline-separated {@code <guest OS name>=<oVirt OS type>} entries. Names are
     * matched case-insensitively. Malformed entries are skipped with a warning.
     */
    static Map<String, String> parseOverrides(final String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        final Map<String, String> overrides = new HashMap<>();
        for (String entry : value.split("[;\\r\\n]+")) {
            if (StringUtils.isBlank(entry)) {
                continue;
            }
            final int separator = entry.lastIndexOf('=');
            final String name = separator > 0 ? normalizeName(entry.substring(0, separator)) : "";
            final String type = separator > 0 ? entry.substring(separator + 1).trim() : "";
            if (name.isEmpty() || type.isEmpty()) {
                LOGGER.warn("Ignoring malformed guest OS type override '{}', expected '<guest OS name>=<oVirt OS type>'",
                        entry.trim());
                continue;
            }
            overrides.put(name, type);
        }
        return overrides;
    }

    private static Map<String, String> getOverrides(final String value) {
        if (!StringUtils.equals(value, cachedOverridesValue)) {
            cachedOverrides = parseOverrides(value);
            cachedOverridesValue = value;
        }
        return cachedOverrides;
    }

    private static String normalizeName(final String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Built-in mapping from the guest OS name. Matches Veeam's expected values for all CloudStack guest OS
     * types, and handles new versions of known distributions, e.g. Rocky Linux 10 or Ubuntu 26.04.
     */
    static String inferOsType(final String guestOsName) {
        if (StringUtils.isBlank(guestOsName)) {
            return OTHER;
        }
        final String name = normalizeName(guestOsName);
        final Integer bits = getBits(name);
        if (name.contains("windows") || name.contains("small business server")) {
            return getWindowsOsType(name, bits);
        }
        if (name.equals("dos")) {
            return OTHER;
        }
        if (name.contains("solaris")) {
            return "solaris_11";
        }
        if (name.contains("freebsd")) {
            return Integer.valueOf(64).equals(bits) ? "freebsdx64" : "freebsd";
        }
        if (name.contains("suse")) {
            return "sles_11";
        }
        final int[] version = getVersion(BITS.matcher(name).replaceAll(""));
        if (name.contains("ubuntu")) {
            if (version == null || compare(version, 14, 4) < 0) {
                return "ubuntu_12_04";
            }
            return compare(version, 18, 4) < 0 ? "ubuntu_14_04" : "ubuntu_18_04";
        }
        if (name.contains("debian") && version != null) {
            return version[0] >= 9 ? "debian_9" : "debian_7";
        }
        if (name.contains("rocky")) {
            return getEnterpriseLinuxOsType("rockylinux", version, bits, 1);
        }
        if (name.contains("alma")) {
            return getEnterpriseLinuxOsType("almalinux", version, bits, 1);
        }
        if (name.contains("oracle")) {
            return getEnterpriseLinuxOsType("ol", version, bits, 1);
        }
        if (name.contains("centos") || name.contains("red hat")) {
            return getEnterpriseLinuxOsType("rhel", version, bits, 3);
        }
        if (name.contains("photon")) {
            return OTHER;
        }
        if (GENERIC_LINUX.matcher(name).find()) {
            return OTHER_LINUX;
        }
        return OTHER;
    }

    private static String getWindowsOsType(final String name, final Integer bits) {
        final boolean is64 = Integer.valueOf(64).equals(bits);
        if (name.contains("server") || name.contains("small business")) {
            if (name.contains("2025")) {
                return "windows_2025";
            }
            if (name.contains("2022")) {
                return "windows_2022";
            }
            if (name.contains("2019")) {
                return "windows_2019x64";
            }
            if (name.contains("2016")) {
                return "windows_2016x64";
            }
            final boolean r2 = name.contains(" r2");
            if (name.contains("2012")) {
                return r2 ? "windows_2012R2x64" : "windows_2012x64";
            }
            if (name.contains("2008")) {
                return r2 ? "windows_2008R2x64" : (is64 ? "windows_2008x64" : "windows_2008");
            }
            if (name.contains("2003")) {
                return is64 ? "windows_2003x64" : "windows_2003";
            }
        }
        if (WINDOWS_11.matcher(name).find()) {
            return "windows_11";
        }
        if (WINDOWS_10.matcher(name).find()) {
            return is64 ? "windows_10x64" : "windows_10";
        }
        if (WINDOWS_8.matcher(name).find()) {
            return is64 ? "windows_8x64" : "windows_8";
        }
        if (WINDOWS_7.matcher(name).find()) {
            return is64 ? "windows_7x64" : "windows_7";
        }
        if (WINDOWS_XP.matcher(name).find()) {
            return "windows_xp";
        }
        return "windows_10";
    }

    /**
     * Returns {@code <prefix>_<major>x64}, or {@code <prefix>_<major>} for a 32-bit or unspecified-bitness
     * guest before version 7. Versions below minMajor, or no version, give other_linux.
     */
    private static String getEnterpriseLinuxOsType(final String prefix, final int[] version, final Integer bits,
                final int minMajor) {
        if (version == null || version[0] < minMajor) {
            return OTHER_LINUX;
        }
        final int major = version[0];
        if (major >= 7 || Integer.valueOf(64).equals(bits)) {
            return prefix + "_" + major + "x64";
        }
        return prefix + "_" + major;
    }

    private static Integer getBits(final String name) {
        if (BITS_64.matcher(name).find()) {
            return 64;
        }
        if (BITS_32.matcher(name).find()) {
            return 32;
        }
        return null;
    }

    private static int[] getVersion(final String name) {
        final Matcher matcher = VERSION.matcher(name);
        if (!matcher.find()) {
            return null;
        }
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        return new int[] {major, minor};
    }

    private static int compare(final int[] version, final int major, final int minor) {
        return version[0] != major ? Integer.compare(version[0], major) : Integer.compare(version[1], minor);
    }
}
