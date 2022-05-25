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

package com.cloud.upgrade.dao;

import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.cloud.storage.GuestOSHypervisorMapping;
import com.cloud.upgrade.GuestOsMapper;
import com.cloud.utils.exception.CloudRuntimeException;

public class Upgrade41500to41510 implements DbUpgrade, DbUpgradeSystemVmTemplate {

    final static Logger LOG = Logger.getLogger(Upgrade41500to41510.class);
    private GuestOsMapper guestOsMapper = new GuestOsMapper();

    @Override
    public String[] getUpgradableVersionRange() {
        return new String[] {"4.15.0.0", "4.15.1.0"};
    }

    @Override
    public String getUpgradedVersion() {
        return "4.15.1.0";
    }

    @Override
    public boolean supportsRollingUpgrade() {
        return false;
    }

    @Override
    public InputStream[] getPrepareScripts() {
        final String scriptFile = "META-INF/db/schema-41500to41510.sql";
        final InputStream script = Thread.currentThread().getContextClassLoader().getResourceAsStream(scriptFile);
        if (script == null) {
            throw new CloudRuntimeException("Unable to find " + scriptFile);
        }

        return new InputStream[] {script};
    }

    @Override
    public void performDataMigration(Connection conn) {
        correctGuestOsNames(conn);
        updateGuestOsMappings(conn);
    }

    @Override
    @SuppressWarnings("serial")
    public void updateSystemVmTemplates(final Connection conn) {
    }

    private void correctGuestOsNames(final Connection conn) {
        guestOsMapper.updateGuestOsName(7, "Fedora Linux", "Fedora Linux (32 bit)");
        guestOsMapper.updateGuestOsName(7, "Mandriva Linux", "Mandriva Linux (32 bit)");

        GuestOSHypervisorMapping mapping = new GuestOSHypervisorMapping("VMware", "6.7.3", "opensuseGuest");
        guestOsMapper.updateGuestOsNameFromMapping("OpenSUSE Linux (32 bit)", mapping);
    }

    private void updateGuestOsMappings(final Connection conn) {
        LOG.debug("Updating guest OS mappings");

        // Add support for SUSE Linux Enterprise Desktop 12 SP3 (64-bit) for Xenserver 8.1.0
        List<GuestOSHypervisorMapping> mappings = new ArrayList<GuestOSHypervisorMapping>();
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "SUSE Linux Enterprise Desktop 12 SP3 (64-bit)"));
        guestOsMapper.addGuestOsAndHypervisorMappings (5, "SUSE Linux Enterprise Desktop 12 SP3 (64-bit)", mappings);
        mappings.clear();

        // Add support for SUSE Linux Enterprise Desktop 12 SP4 (64-bit) for Xenserver 8.1.0
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "SUSE Linux Enterprise Desktop 12 SP4 (64-bit)"));
        guestOsMapper.addGuestOsAndHypervisorMappings (5, "SUSE Linux Enterprise Desktop 12 SP4 (64-bit)", mappings);
        mappings.clear();

        // Add support for SUSE Linux Enterprise Server 12 SP4 (64-bit) for Xenserver 8.1.0
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "SUSE Linux Enterprise Server 12 SP4 (64-bit)"));
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "NeoKylin Linux Server 7"));
        guestOsMapper.addGuestOsAndHypervisorMappings(5, "SUSE Linux Enterprise Server 12 SP4 (64-bit)", mappings);
        mappings.clear();

        // Add support for Scientific Linux 7 for Xenserver 8.1.0
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "Scientific Linux 7"));
        guestOsMapper.addGuestOsAndHypervisorMappings (9, "Scientific Linux 7", mappings);
        mappings.clear();

        // Add support for NeoKylin Linux Server 7 for Xenserver 8.1.0
        guestOsMapper.addGuestOsAndHypervisorMappings(9, "NeoKylin Linux Server 7", mappings); //334
        mappings.clear();

        // Pass Guest OS Ids to update pre-4.14 mappings
        // Add support CentOS 8 for Xenserver 8.1.0
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "CentOS 8"), 297);

        // Add support for Debian Buster 10 for Xenserver 8.1.0
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "Debian Buster 10"), 292);
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "Debian Buster 10"), 293);

        // Add support for SUSE Linux Enterprise 15 (64-bit) for Xenserver 8.1.0
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("Xenserver", "8.1.0", "SUSE Linux Enterprise 15 (64-bit)"), 291);

        // Add support for Ubuntu Focal Fossa 20.04 for Xenserver 8.2.0
        mappings.add(new GuestOSHypervisorMapping("Xenserver", "8.2.0", "Ubuntu Focal Fossa 20.04"));
        guestOsMapper.addGuestOsAndHypervisorMappings(10, "Ubuntu 20.04 LTS", mappings);
        mappings.clear();

        // Add support for darwin19_64Guest from VMware 7.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0", "darwin19_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(7, "macOS 10.15 (64 bit)", mappings);
        mappings.clear();

        // Add support for debian11_64Guest from VMware 7.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0", "debian11_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(2, "Debian GNU/Linux 11 (64-bit)", mappings);
        mappings.clear();

        // Add support for debian11Guest from VMware 7.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0", "debian11Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(2, "Debian GNU/Linux 11 (32-bit)", mappings);
        mappings.clear();

        // Add support for windows2019srv_64Guest from VMware 7.0
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("VMware", "7.0", "windows2019srv_64Guest"), 276);

        // Add support for amazonlinux3_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "amazonlinux3_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(7, "Amazon Linux 3 (64 bit)", mappings);
        mappings.clear();

        // Add support for asianux9_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "asianux9_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(7, "Asianux Server 9 (64 bit)", mappings);
        mappings.clear();

        // Add support for centos9_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "centos9_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(1, "CentOS 9", mappings);
        mappings.clear();

        // Add support for darwin20_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "darwin20_64Guest"));
        // Add support for darwin21_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "darwin21_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(7, "macOS 11 (64 bit)", mappings);
        mappings.clear();

        // Add support for freebsd13_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "freebsd13_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(9, "FreeBSD 13 (64-bit)", mappings);
        mappings.clear();

        // Add support for freebsd13Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "freebsd13Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(9, "FreeBSD 13 (32-bit)", mappings);
        mappings.clear();

        // Add support for oracleLinux9_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "oracleLinux9_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(3, "Oracle Linux 9", mappings);
        mappings.clear();

        // Add support for other5xLinux64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "other5xLinux64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(2, "Linux 5.x Kernel (64-bit)", mappings);
        mappings.clear();

        // Add support for other5xLinuxGuest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "other5xLinuxGuest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(2, "Linux 5.x Kernel (32-bit)", mappings);
        mappings.clear();

        // Add support for rhel9_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "rhel9_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(4, "Red Hat Enterprise Linux 9.0", mappings);
        mappings.clear();

        // Add support for sles16_64Guest from VMware 7.0.1.0
        mappings.add(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "sles16_64Guest"));
        guestOsMapper.addGuestOsAndHypervisorMappings(5, "SUSE Linux Enterprise Server 16 (64-bit)", mappings);
        mappings.clear();

        // Add support for windows2019srvNext_64Guest from VMware 7.0.1.0 - Pass Guest OS Ids to update pre-4.14 mappings
        guestOsMapper.addGuestOsHypervisorMapping(new GuestOSHypervisorMapping("VMware", "7.0.1.0", "windows2019srvNext_64Guest"), 276);
    }

    @Override
    public InputStream[] getCleanupScripts() {
        final String scriptFile = "META-INF/db/schema-41500to41510-cleanup.sql";
        final InputStream script = Thread.currentThread().getContextClassLoader().getResourceAsStream(scriptFile);
        if (script == null) {
            throw new CloudRuntimeException("Unable to find " + scriptFile);
        }

        return new InputStream[] {script};
    }
}
