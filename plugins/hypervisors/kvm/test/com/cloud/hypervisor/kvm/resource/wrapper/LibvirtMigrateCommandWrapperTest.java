//
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
//
package com.cloud.hypervisor.kvm.resource.wrapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloud.agent.api.MigrateCommand;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LibvirtMigrateCommandWrapperTest {
    String fullfile =
"<domain type='kvm' id='4'>\n" +
"  <name>i-6-6-VM</name>\n" +
"  <uuid>f197b32b-8da2-4a57-bb8a-d01bacc5cd33</uuid>\n" +
"  <description>Other PV (64-bit)</description>\n" +
"  <memory unit='KiB'>262144</memory>\n" +
"  <currentMemory unit='KiB'>262144</currentMemory>\n" +
"  <vcpu placement='static'>1</vcpu>\n" +
"  <cputune>\n" +
"    <shares>100</shares>\n" +
"  </cputune>\n" +
"  <resource>\n" +
"    <partition>/machine</partition>\n" +
"  </resource>\n" +
"  <sysinfo type='smbios'>\n" +
"    <system>\n" +
"      <entry name='manufacturer'>Apache Software Foundation</entry>\n" +
"      <entry name='product'>CloudStack KVM Hypervisor</entry>\n" +
"      <entry name='uuid'>f197b32b-8da2-4a57-bb8a-d01bacc5cd33</entry>\n" +
"    </system>\n" +
"  </sysinfo>\n" +
"  <os>\n" +
"    <type arch='x86_64' machine='pc-i440fx-rhel7.0.0'>hvm</type>\n" +
"    <boot dev='cdrom'/>\n" +
"    <boot dev='hd'/>\n" +
"    <smbios mode='sysinfo'/>\n" +
"  </os>\n" +
"  <features>\n" +
"    <acpi/>\n" +
"    <apic/>\n" +
"    <pae/>\n" +
"  </features>\n" +
"  <clock offset='utc'>\n" +
"    <timer name='kvmclock'/>\n" +
"  </clock>\n" +
"  <on_poweroff>destroy</on_poweroff>\n" +
"  <on_reboot>restart</on_reboot>\n" +
"  <on_crash>destroy</on_crash>\n" +
"  <devices>\n" +
"    <emulator>/usr/libexec/qemu-kvm</emulator>\n" +
"    <disk type='file' device='disk'>\n" +
"      <driver name='qemu' type='qcow2' cache='none'/>\n" +
"      <source file='/mnt/812ea6a3-7ad0-30f4-9cab-01e3f2985b98/4650a2f7-fce5-48e2-beaa-bcdf063194e6'/>\n" +
"      <backingStore type='file' index='1'>\n" +
"        <format type='raw'/>\n" +
"        <source file='/mnt/812ea6a3-7ad0-30f4-9cab-01e3f2985b98/bb4d4df4-c004-11e5-94ed-5254001daa61'/>\n" +
"        <backingStore/>\n" +
"      </backingStore>\n" +
"      <target dev='vda' bus='virtio'/>\n" +
"      <serial>4650a2f7fce548e2beaa</serial>\n" +
"      <alias name='virtio-disk0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x04' function='0x0'/>\n" +
"    </disk>\n" +
"    <disk type='file' device='cdrom'>\n" +
"      <driver name='qemu' type='raw' cache='none'/>\n" +
"      <backingStore/>\n" +
"      <target dev='hdc' bus='ide'/>\n" +
"      <readonly/>\n" +
"      <alias name='ide0-1-0'/>\n" +
"      <address type='drive' controller='0' bus='1' target='0' unit='0'/>\n" +
"    </disk>\n" +
"    <controller type='usb' index='0'>\n" +
"      <alias name='usb'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x2'/>\n" +
"    </controller>\n" +
"    <controller type='pci' index='0' model='pci-root'>\n" +
"      <alias name='pci.0'/>\n" +
"    </controller>\n" +
"    <controller type='ide' index='0'>\n" +
"      <alias name='ide'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x1'/>\n" +
"    </controller>\n" +
"    <interface type='bridge'>\n" +
"      <mac address='06:fe:b4:00:00:06'/>\n" +
"      <source bridge='breth0-50'/>\n" +
"      <bandwidth>\n" +
"        <inbound average='25600' peak='25600'/>\n" +
"        <outbound average='25600' peak='25600'/>\n" +
"      </bandwidth>\n" +
"      <target dev='vnet4'/>\n" +
"      <model type='virtio'/>\n" +
"      <alias name='net0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x03' function='0x0'/>\n" +
"    </interface>\n" +
"    <serial type='pty'>\n" +
"      <source path='/dev/pts/2'/>\n" +
"      <target port='0'/>\n" +
"      <alias name='serial0'/>\n" +
"    </serial>\n" +
"    <console type='pty' tty='/dev/pts/2'>\n" +
"      <source path='/dev/pts/2'/>\n" +
"      <target type='serial' port='0'/>\n" +
"      <alias name='serial0'/>\n" +
"    </console>\n" +
"    <input type='tablet' bus='usb'>\n" +
"      <alias name='input0'/>\n" +
"    </input>\n" +
"    <input type='mouse' bus='ps2'/>\n" +
"    <input type='keyboard' bus='ps2'/>\n" +
"    <graphics type='vnc' port='5902' autoport='yes' listen='192.168.22.22'>\n" +
"      <listen type='address' address='192.168.22.22'/>\n" +
"    </graphics>\n" +
"    <video>\n" +
"      <model type='cirrus' vram='16384' heads='1'/>\n" +
"      <alias name='video0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x02' function='0x0'/>\n" +
"    </video>\n" +
"    <memballoon model='none'>\n" +
"      <alias name='balloon0'/>\n" +
"    </memballoon>\n" +
"  </devices>\n" +
"</domain>";

    String targetfile =
"<domain type='kvm' id='4'>\n" +
"  <name>i-6-6-VM</name>\n" +
"  <uuid>f197b32b-8da2-4a57-bb8a-d01bacc5cd33</uuid>\n" +
"  <description>Other PV (64-bit)</description>\n" +
"  <memory unit='KiB'>262144</memory>\n" +
"  <currentMemory unit='KiB'>262144</currentMemory>\n" +
"  <vcpu placement='static'>1</vcpu>\n" +
"  <cputune>\n" +
"    <shares>100</shares>\n" +
"  </cputune>\n" +
"  <resource>\n" +
"    <partition>/machine</partition>\n" +
"  </resource>\n" +
"  <sysinfo type='smbios'>\n" +
"    <system>\n" +
"      <entry name='manufacturer'>Apache Software Foundation</entry>\n" +
"      <entry name='product'>CloudStack KVM Hypervisor</entry>\n" +
"      <entry name='uuid'>f197b32b-8da2-4a57-bb8a-d01bacc5cd33</entry>\n" +
"    </system>\n" +
"  </sysinfo>\n" +
"  <os>\n" +
"    <type arch='x86_64' machine='pc-i440fx-rhel7.0.0'>hvm</type>\n" +
"    <boot dev='cdrom'/>\n" +
"    <boot dev='hd'/>\n" +
"    <smbios mode='sysinfo'/>\n" +
"  </os>\n" +
"  <features>\n" +
"    <acpi/>\n" +
"    <apic/>\n" +
"    <pae/>\n" +
"  </features>\n" +
"  <clock offset='utc'>\n" +
"    <timer name='kvmclock'/>\n" +
"  </clock>\n" +
"  <on_poweroff>destroy</on_poweroff>\n" +
"  <on_reboot>restart</on_reboot>\n" +
"  <on_crash>destroy</on_crash>\n" +
"  <devices>\n" +
"    <emulator>/usr/libexec/qemu-kvm</emulator>\n" +
"    <disk type='file' device='disk'>\n" +
"      <driver name='qemu' type='qcow2' cache='none'/>\n" +
"      <source file='/mnt/812ea6a3-7ad0-30f4-9cab-01e3f2985b98/4650a2f7-fce5-48e2-beaa-bcdf063194e6'/>\n" +
"      <backingStore type='file' index='1'>\n" +
"        <format type='raw'/>\n" +
"        <source file='/mnt/812ea6a3-7ad0-30f4-9cab-01e3f2985b98/bb4d4df4-c004-11e5-94ed-5254001daa61'/>\n" +
"        <backingStore/>\n" +
"      </backingStore>\n" +
"      <target dev='vda' bus='virtio'/>\n" +
"      <serial>4650a2f7fce548e2beaa</serial>\n" +
"      <alias name='virtio-disk0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x04' function='0x0'/>\n" +
"    </disk>\n" +
"    <disk type='file' device='cdrom'>\n" +
"      <driver name='qemu' type='raw' cache='none'/>\n" +
"      <backingStore/>\n" +
"      <target dev='hdc' bus='ide'/>\n" +
"      <readonly/>\n" +
"      <alias name='ide0-1-0'/>\n" +
"      <address type='drive' controller='0' bus='1' target='0' unit='0'/>\n" +
"    </disk>\n" +
"    <controller type='usb' index='0'>\n" +
"      <alias name='usb'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x2'/>\n" +
"    </controller>\n" +
"    <controller type='pci' index='0' model='pci-root'>\n" +
"      <alias name='pci.0'/>\n" +
"    </controller>\n" +
"    <controller type='ide' index='0'>\n" +
"      <alias name='ide'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x1'/>\n" +
"    </controller>\n" +
"    <interface type='bridge'>\n" +
"      <mac address='06:fe:b4:00:00:06'/>\n" +
"      <source bridge='breth0-50'/>\n" +
"      <bandwidth>\n" +
"        <inbound average='25600' peak='25600'/>\n" +
"        <outbound average='25600' peak='25600'/>\n" +
"      </bandwidth>\n" +
"      <target dev='vnet4'/>\n" +
"      <model type='virtio'/>\n" +
"      <alias name='net0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x03' function='0x0'/>\n" +
"    </interface>\n" +
"    <serial type='pty'>\n" +
"      <source path='/dev/pts/2'/>\n" +
"      <target port='0'/>\n" +
"      <alias name='serial0'/>\n" +
"    </serial>\n" +
"    <console type='pty' tty='/dev/pts/2'>\n" +
"      <source path='/dev/pts/2'/>\n" +
"      <target type='serial' port='0'/>\n" +
"      <alias name='serial0'/>\n" +
"    </console>\n" +
"    <input type='tablet' bus='usb'>\n" +
"      <alias name='input0'/>\n" +
"    </input>\n" +
"    <input type='mouse' bus='ps2'/>\n" +
"    <input type='keyboard' bus='ps2'/>\n" +
"    <graphics type='vnc' port='5902' autoport='yes' listen='192.168.22.21'>\n" +
"      <listen type='address' address='192.168.22.21'/>\n" +
"    </graphics>\n" +
"    <video>\n" +
"      <model type='cirrus' vram='16384' heads='1'/>\n" +
"      <alias name='video0'/>\n" +
"      <address type='pci' domain='0x0000' bus='0x00' slot='0x02' function='0x0'/>\n" +
"    </video>\n" +
"    <memballoon model='none'>\n" +
"      <alias name='balloon0'/>\n" +
"    </memballoon>\n" +
"  </devices>\n" +
"</domain>";

    private static final String sourcePoolUuid = "07eb495b-5590-3877-9fb7-23c6e9a40d40";
    private static final String destPoolUuid = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String disk1SourceFilename = "981ab1dc-40f4-41b5-b387-6539aeddbf47";
    private static final String disk2SourceFilename = "bf8621b3-027c-497d-963b-06319650f048";
    private static final String sourceMultidiskDomainXml =
    "<domain type='kvm' id='6'>\n" +
            "  <name>i-2-3-VM</name>\n" +
            "  <uuid>91860126-7dda-4876-ac1e-48d06cd4b2eb</uuid>\n" +
            "  <description>Apple Mac OS X 10.6 (32-bit)</description>\n" +
            "  <memory unit='KiB'>524288</memory>\n" +
            "  <currentMemory unit='KiB'>524288</currentMemory>\n" +
            "  <vcpu placement='static'>1</vcpu>\n" +
            "  <cputune>\n" +
            "    <shares>250</shares>\n" +
            "  </cputune>\n" +
            "  <sysinfo type='smbios'>\n" +
            "    <system>\n" +
            "      <entry name='manufacturer'>Apache Software Foundation</entry>\n" +
            "      <entry name='product'>CloudStack KVM Hypervisor</entry>\n" +
            "      <entry name='uuid'>91860126-7dda-4876-ac1e-48d06cd4b2eb</entry>\n" +
            "    </system>\n" +
            "  </sysinfo>\n" +
            "  <os>\n" +
            "    <type arch='x86_64' machine='rhel6.6.0'>hvm</type>\n" +
            "    <boot dev='cdrom'/>\n" +
            "    <boot dev='hd'/>\n" +
            "    <smbios mode='sysinfo'/>\n" +
            "  </os>\n" +
            "  <features>\n" +
            "    <acpi/>\n" +
            "    <apic/>\n" +
            "    <pae/>\n" +
            "  </features>\n" +
            "  <cpu>\n" +
            "  </cpu>\n" +
            "  <clock offset='utc'/>\n" +
            "  <on_poweroff>destroy</on_poweroff>\n" +
            "  <on_reboot>restart</on_reboot>\n" +
            "  <on_crash>destroy</on_crash>\n" +
            "  <devices>\n" +
            "    <emulator>/usr/libexec/qemu-kvm</emulator>\n" +
            "    <disk type='file' device='disk'>\n" +
            "      <driver name='qemu' type='qcow2' cache='none'/>\n" +
            "      <source file='/mnt/07eb495b-5590-3877-9fb7-23c6e9a40d40/981ab1dc-40f4-41b5-b387-6539aeddbf47'/>\n" +
            "      <target dev='hda' bus='ide'/>\n" +
            "      <serial>e8141f63b5364a7f8cbb</serial>\n" +
            "      <alias name='ide0-0-0'/>\n" +
            "      <address type='drive' controller='0' bus='0' target='0' unit='0'/>\n" +
            "    </disk>\n" +
            "    <disk type='file' device='cdrom'>\n" +
            "      <driver name='qemu' type='raw' cache='none'/>\n" +
            "      <target dev='hdc' bus='ide'/>\n" +
            "      <readonly/>\n" +
            "      <alias name='ide0-1-0'/>\n" +
            "      <address type='drive' controller='0' bus='1' target='0' unit='0'/>\n" +
            "    </disk>\n" +
            "    <disk type='file' device='disk'>\n" +
            "      <driver name='qemu' type='qcow2' cache='none'/>\n" +
            "      <source file='/mnt/07eb495b-5590-3877-9fb7-23c6e9a40d40/bf8621b3-027c-497d-963b-06319650f048'/>\n" +
            "      <target dev='vdb' bus='virtio'/>\n" +
            "      <serial>bf8621b3027c497d963b</serial>\n" +
            "      <alias name='virtio-disk1'/>\n" +
            "      <address type='pci' domain='0x0000' bus='0x00' slot='0x04' function='0x0'/>\n" +
            "    </disk>\n" +
            "    <controller type='usb' index='0'>\n" +
            "      <alias name='usb0'/>\n" +
            "      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x2'/>\n" +
            "    </controller>\n" +
            "    <controller type='ide' index='0'>\n" +
            "      <alias name='ide0'/>\n" +
            "      <address type='pci' domain='0x0000' bus='0x00' slot='0x01' function='0x1'/>\n" +
            "    </controller>\n" +
            "    <interface type='bridge'>\n" +
            "      <mac address='02:00:4c:5f:00:01'/>\n" +
            "      <source bridge='breth1-511'/>\n" +
            "      <target dev='vnet6'/>\n" +
            "      <model type='e1000'/>\n" +
            "      <alias name='net0'/>\n" +
            "      <address type='pci' domain='0x0000' bus='0x00' slot='0x03' function='0x0'/>\n" +
            "    </interface>\n" +
            "    <serial type='pty'>\n" +
            "      <source path='/dev/pts/2'/>\n" +
            "      <target port='0'/>\n" +
            "      <alias name='serial0'/>\n" +
            "    </serial>\n" +
            "    <console type='pty' tty='/dev/pts/2'>\n" +
            "      <source path='/dev/pts/2'/>\n" +
            "      <target type='serial' port='0'/>\n" +
            "      <alias name='serial0'/>\n" +
            "    </console>\n" +
            "    <input type='tablet' bus='usb'>\n" +
            "      <alias name='input0'/>\n" +
            "    </input>\n" +
            "    <input type='mouse' bus='ps2'/>\n" +
            "    <graphics type='vnc' port='5902' autoport='yes' listen='10.2.2.31' passwd='LEm_y8SIs-8hXimtxnyEnA'>\n" +
            "      <listen type='address' address='10.2.2.31'/>\n" +
            "    </graphics>\n" +
            "    <video>\n" +
            "      <model type='cirrus' vram='9216' heads='1'/>\n" +
            "      <alias name='video0'/>\n" +
            "      <address type='pci' domain='0x0000' bus='0x00' slot='0x02' function='0x0'/>\n" +
            "    </video>\n" +
            "    <memballoon model='none'>\n" +
            "      <alias name='balloon0'/>\n" +
            "    </memballoon>\n" +
            "  </devices>\n" +
            "</domain>\n";

    @Test
    public void testReplaceIpForVNCInDescFile() {
        final String targetIp = "192.168.22.21";
        final LibvirtMigrateCommandWrapper lw = new LibvirtMigrateCommandWrapper();
        final String result = lw.replaceIpForVNCInDescFile(fullfile, targetIp);
        assertTrue("transformation does not live up to expectation:\n" + result, targetfile.equals(result));
    }

    @Test
    public void testReplaceIpForVNCInDesc() {
        final String xmlDesc =
                "<domain type='kvm' id='3'>" +
                "  <devices>" +
                "    <graphics type='vnc' port='5900' autoport='yes' listen='10.10.10.1'>" +
                "      <listen type='address' address='10.10.10.1'/>" +
                "    </graphics>" +
                "  </devices>" +
                "</domain>";
        final String expectedXmlDesc =
                "<domain type='kvm' id='3'>" +
                "  <devices>" +
                "    <graphics type='vnc' port='5900' autoport='yes' listen='10.10.10.10'>" +
                "      <listen type='address' address='10.10.10.10'/>" +
                "    </graphics>" +
                "  </devices>" +
                "</domain>";
        final String targetIp = "10.10.10.10";
        final LibvirtMigrateCommandWrapper lw = new LibvirtMigrateCommandWrapper();
        final String result = lw.replaceIpForVNCInDescFile(xmlDesc, targetIp);
        assertTrue("transformation does not live up to expectation:\n" + result, expectedXmlDesc.equals(result));
    }

    @Test
    public void testReplaceFqdnForVNCInDesc() {
        final String xmlDesc =
                "<domain type='kvm' id='3'>" +
                "  <devices>" +
                "    <graphics type='vnc' port='5900' autoport='yes' listen='localhost.local'>" +
                "      <listen type='address' address='localhost.local'/>" +
                "    </graphics>" +
                "  </devices>" +
                "</domain>";
        final String expectedXmlDesc =
                "<domain type='kvm' id='3'>" +
                "  <devices>" +
                "    <graphics type='vnc' port='5900' autoport='yes' listen='localhost.localdomain'>" +
                "      <listen type='address' address='localhost.localdomain'/>" +
                "    </graphics>" +
                "  </devices>" +
                "</domain>";
        final String targetIp = "localhost.localdomain";
        final LibvirtMigrateCommandWrapper lw = new LibvirtMigrateCommandWrapper();
        final String result = lw.replaceIpForVNCInDescFile(xmlDesc, targetIp);
        assertTrue("transformation does not live up to expectation:\n" + result, expectedXmlDesc.equals(result));
    }

    @Test
    public void testReplaceStorageXmlDiskNotManagedStorage() throws ParserConfigurationException, TransformerException, SAXException, IOException {
        final LibvirtMigrateCommandWrapper lw = new LibvirtMigrateCommandWrapper();

        String destDisk1FileName = "XXXXXXXXXXXXXX";
        String destDisk2FileName = "YYYYYYYYYYYYYY";
        String destDisk1Path = String.format("/mnt/%s/%s", destPoolUuid, destDisk1FileName);
        MigrateCommand.MigrateDiskInfo migrateDisk1Info = new MigrateCommand.MigrateDiskInfo(disk1SourceFilename,
                MigrateCommand.MigrateDiskInfo.DiskType.FILE, MigrateCommand.MigrateDiskInfo.DriverType.QCOW2,
                MigrateCommand.MigrateDiskInfo.Source.FILE, destDisk1Path);

        String destDisk2Path = String.format("/mnt/%s/%s", destPoolUuid, destDisk2FileName);
        MigrateCommand.MigrateDiskInfo migrateDisk2Info = new MigrateCommand.MigrateDiskInfo(disk2SourceFilename,
                MigrateCommand.MigrateDiskInfo.DiskType.FILE, MigrateCommand.MigrateDiskInfo.DriverType.QCOW2,
                MigrateCommand.MigrateDiskInfo.Source.FILE, destDisk2Path);

        Map<String, MigrateCommand.MigrateDiskInfo> migrateStorage = new HashMap<>();
        migrateStorage.put(disk1SourceFilename, migrateDisk1Info);
        migrateStorage.put(disk2SourceFilename, migrateDisk2Info);
        String newXml = lw.replaceStorage(sourceMultidiskDomainXml, migrateStorage, false);

        assertTrue(newXml.contains(destDisk1Path));
        assertTrue(newXml.contains(destDisk2Path));
        assertFalse(newXml.contains("/mnt/" + sourcePoolUuid + "/" + disk1SourceFilename));
        assertFalse(newXml.contains("/mnt/" + sourcePoolUuid + "/" + disk2SourceFilename));
    }
}
