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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OvirtOsTypeMapperTest {

    @Test
    public void testInferOsType_MatchesExpectedTypeForAllCloudStackGuestOsTypes() throws Exception {
        final Map<String, String> expected;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("guest-os-type-mapping.json")) {
            expected = new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() { });
        }
        final List<String> mismatches = new ArrayList<>();
        expected.forEach((name, type) -> {
            final String actual = OvirtOsTypeMapper.inferOsType(name);
            if (!type.equals(actual)) {
                mismatches.add(name + ": expected " + type + ", got " + actual);
            }
        });
        assertEquals(372, expected.size());
        assertTrue(String.join("\n", mismatches), mismatches.isEmpty());
    }

    @Test
    public void testInferOsType_NewVersionsOfKnownDistributions() {
        assertEquals("rockylinux_10x64", OvirtOsTypeMapper.inferOsType("Rocky Linux 10"));
        assertEquals("almalinux_10x64", OvirtOsTypeMapper.inferOsType("AlmaLinux 10.1"));
        assertEquals("ol_10x64", OvirtOsTypeMapper.inferOsType("Oracle Linux 10"));
        assertEquals("rhel_10x64", OvirtOsTypeMapper.inferOsType("Red Hat Enterprise Linux 10"));
        assertEquals("ubuntu_18_04", OvirtOsTypeMapper.inferOsType("Ubuntu 26.04 LTS"));
        assertEquals("debian_9", OvirtOsTypeMapper.inferOsType("Debian GNU/Linux 13 (64-bit)"));
        assertEquals("other_linux", OvirtOsTypeMapper.inferOsType("Fedora 41"));
    }

    @Test
    public void testInferOsType_UnknownOrBlankNames() {
        assertEquals("other", OvirtOsTypeMapper.inferOsType(null));
        assertEquals("other", OvirtOsTypeMapper.inferOsType("  "));
        assertEquals("other", OvirtOsTypeMapper.inferOsType("Custom Appliance 1234567890123"));
        assertEquals("other_linux", OvirtOsTypeMapper.inferOsType("My Custom Linux 99999999999"));
        assertEquals("windows_10", OvirtOsTypeMapper.inferOsType("Windows Small Business Server 2011"));
    }

    @Test
    public void testGetOsType_OverrideWinsAndOthersUseBuiltInMapping() {
        final String overrides = "VMware Photon (64 bit)=other_linux; Rocky Linux 9=rhel_9x64";

        assertEquals("other_linux", OvirtOsTypeMapper.getOsType("VMware Photon (64 bit)", overrides));
        assertEquals("rhel_9x64", OvirtOsTypeMapper.getOsType("Rocky Linux 9", overrides));
        assertEquals("rockylinux_8x64", OvirtOsTypeMapper.getOsType("Rocky Linux 8", overrides));
        assertEquals("other", OvirtOsTypeMapper.getOsType("VMware Photon (64 bit)", ""));
    }

    @Test
    public void testGetOsType_OverrideNameMatchingIgnoresCaseAndExtraSpaces() {
        final String overrides = "  vmware   PHOTON (64 bit) = other_linux ";

        assertEquals("other_linux", OvirtOsTypeMapper.getOsType("VMware Photon (64 bit)", overrides));
    }

    @Test
    public void testParseOverrides_SkipsMalformedEntriesAndAcceptsNewlines() {
        final Map<String, String> overrides = OvirtOsTypeMapper.parseOverrides(
                "Rocky Linux 9=rhel_9x64\nno separator;=missing_name;Missing Type=;;Oracle Linux 9=ol_9x64");

        assertEquals(2, overrides.size());
        assertEquals("rhel_9x64", overrides.get("rocky linux 9"));
        assertEquals("ol_9x64", overrides.get("oracle linux 9"));
        assertTrue(OvirtOsTypeMapper.parseOverrides(null).isEmpty());
    }

    @Test
    public void testGetFamily() {
        assertEquals(OvirtOsTypeMapper.Family.WINDOWS, OvirtOsTypeMapper.getFamily("windows_2012R2x64"));
        assertEquals(OvirtOsTypeMapper.Family.LINUX, OvirtOsTypeMapper.getFamily("rhel_9x64"));
        assertEquals(OvirtOsTypeMapper.Family.LINUX, OvirtOsTypeMapper.getFamily("debian_9"));
        assertEquals(OvirtOsTypeMapper.Family.LINUX, OvirtOsTypeMapper.getFamily("other_linux"));
        assertEquals(OvirtOsTypeMapper.Family.OTHER, OvirtOsTypeMapper.getFamily("other"));
        assertEquals(OvirtOsTypeMapper.Family.OTHER, OvirtOsTypeMapper.getFamily("freebsdx64"));
        assertEquals(OvirtOsTypeMapper.Family.OTHER, OvirtOsTypeMapper.getFamily("solaris_11"));
        assertEquals(OvirtOsTypeMapper.Family.OTHER, OvirtOsTypeMapper.getFamily(null));
    }
}
