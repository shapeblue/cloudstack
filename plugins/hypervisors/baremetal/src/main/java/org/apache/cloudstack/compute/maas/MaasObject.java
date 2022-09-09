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

package org.apache.cloudstack.compute.maas;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MaasObject {

    private static final String ARCH_AMD64 = "amd64";
    private static final String POWER_TYPE_IPMI = "ipmi";

    enum MaasState {
        Ready, Allocated, Deploying, Deployed;
    }

    enum InterfaceType {
        physical, bond;
    }

    public static class MaasConnection {

        public String scheme;
        public String ip;
        public int port;
        public String key;
        public String secret;
        public String consumerKey;

        public MaasConnection(String scheme, String ip, int port, String key, String secret, String consumerKey) {
            this.scheme = scheme;
            this.ip = ip;
            this.port = port;
            this.key = key;
            this.secret = secret;
            this.consumerKey = consumerKey;
        }

        public String getScheme() {
            return scheme;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public String getKey() {
            return key;
        }

        public String getSecret() {
            return secret;
        }

        public String getConsumerKey() {
            return consumerKey;
        }
    }

    public class MaasNode {

        public String hostname;

        @SerializedName("power_state")
        public String powerState;

        @SerializedName("power_type")
        public String powerType;

        @SerializedName("system_id")
        public String systemId;

        @SerializedName("status_name")
        public String statusName;

        @SerializedName("cpu_count")
        public Integer cpuCount;

        @SerializedName("cpu_speed")
        public Long cpuSpeed;

        @SerializedName("memory")
        public Long memory;

        @SerializedName("storage")
        public Double storage;

        @SerializedName("boot_interface")
        public MaasInterface bootInterface;

        @SerializedName("interface_set")
        public MaasInterface[] interfaceSet;

        public String getSystemId() {
            return systemId;
        }

        public String getStatusName() {
            return statusName;
        }

        public Integer getCpuCount() {
            return cpuCount;
        }

        public Long getCpuSpeed() {
            return cpuSpeed;
        }

        public Long getMemory() {
            return memory;
        }

        public Double getStorage() {
            return storage;
        }

        public MaasInterface getBootInterface() {
            return bootInterface;
        }

        public MaasInterface[] getInterfaceSet() {
            return interfaceSet;
        }
    }

    public class MaasInterface {

        public int id;

        public String name;

        public String type;

        public MaasLink[] links;

        public boolean enabled;

        @SerializedName("mac_address")
        public String macAddress;
    }

    public class MaasLink {
        public int id;
        public String mode;
        public MaasSubnet subnet;
    }

    public class MaasSubnet {
        public int id;
        public String name;
        public MaasVlan vlan;
    }

    public class MaasVlan {
        public int id;

        @SerializedName("dhcp_on")
        public boolean dhcpOn;
    }

    public static class AddMachineParameters {

        @SerializedName("mac_addresses") /* For now only one pxe mac address */
        public String macAddress;

        @SerializedName("power_type")
        public String powerType;

        @SerializedName("architecture")
        public String arch;

        @SerializedName("power_parameters_power_user")
        public String powerUser;

        @SerializedName("power_parameters_power_pass")
        public String powerPassword;

        @SerializedName("power_parameters_power_address")
        public String powerAddress;

        public String hostname;

        public AddMachineParameters(String powerAddress, String macAddress, String powerUser, String powerPassword, String hostname) {
            this.powerAddress = powerAddress;
            this.macAddress = macAddress;
            this.powerUser = powerUser;
            this.powerPassword = powerPassword;
            this.hostname = hostname;
            this.arch = ARCH_AMD64;
            this.powerType = POWER_TYPE_IPMI;
        }

        public String getMacAddress() {
            return macAddress;
        }

        public String getPowerType() {
            return powerType;
        }

        public String getArch() {
            return arch;
        }

        public String getPowerUser() {
            return powerUser;
        }

        public String getPowerPassword() {
            return powerPassword;
        }

        public String getPowerAddress() {
            return powerAddress;
        }

        public String getHostname() {
            return hostname;
        }
    }

    public static class DeployMachineParameters{

        @SerializedName("distro_series")
        String distroSeries;

        public DeployMachineParameters(String distroSeries) {
            this.distroSeries = distroSeries;
        }

        public String getDistroSeries() {
            return distroSeries;
        }
    }

    public static class AllocateMachineParameters {

        @SerializedName("system_id")
        String systemId;

        public AllocateMachineParameters(String systemId) {
            this.systemId = systemId;
        }

        public String getSystemId() {
            return systemId;
        }
    }

    public static class UnlinkSubnetParameters {
        Integer id;

        public UnlinkSubnetParameters(Integer id) {
            this.id = id;
        }
    }

    public static class UpdateHostnameParams {

        String hostname;

        public UpdateHostnameParams(String hostname) {
            this.hostname = hostname;
        }
    }

    public static class LinkSubnetParameters {
        String mode;
        Integer subnet;

        public LinkSubnetParameters(String mode, Integer subnet) {
            this.mode = mode;
            this.subnet = subnet;
        }
    }

    public static class ReleaseMachineParameters {
        Boolean erase;

        @SerializedName("secure_erase")
        Boolean secureErase;

        @SerializedName("quick_erase")
        Boolean quickErase;

        public ReleaseMachineParameters(Boolean erase, Boolean secureErase, Boolean quickErase) {
            this.erase = erase;
            this.secureErase = secureErase;
            this.quickErase = quickErase;
        }
    }

    public static class CreateBondInterfaceParameters {
        String name;

        List<Integer> parents;

        @SerializedName("system_id")
        String systemId;

        public CreateBondInterfaceParameters(String name, List<Integer> parents, String systemId) {
            this.name = name;
            this.parents = parents;
            this.systemId = systemId;
        }
    }

        public static class RackController {
        @SerializedName("system_id")
        String systemId;
    }

    public static class BootImage {
        String name;
    }

    public static class ListImagesResponse {
        List<BootImage> images;
    }
}
