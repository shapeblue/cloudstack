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
package org.apache.cloudstack.storage.datastore.util;

import com.google.gson.annotations.SerializedName;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class DateraObject {

    public static final String DEFAULT_STORAGE_NAME = "storage-1";
    public static final String DEFAULT_VOLUME_NAME = "volume-1";
    public static final String DEFAULT_ACL = "deny_all";

    public enum AppState {
        ONLINE, OFFLINE;

        @Override
        public String toString(){
            return this.name().toLowerCase();
        }
    }


    public enum DateraOperation {
        ADD, REMOVE;

        @Override
        public String toString(){
            return this.name().toLowerCase();
        }
    }

    public enum DateraErrorTypes {
        PermissionDeniedError, InvalidRouteError, AuthFailedError,
        ValidationFailedError, InvalidRequestError, NotFoundError,
        NotConnectedError, InvalidSessionKeyError, DatabaseError,
        InternalError;

        public boolean equals(DateraError err){
            return this.name().equals(err.getName());
        }
    }

    public static class DateraApiResponse {
        public String path;
        public String version;
        public String tenant;
        public String data;

        public String getResponseObjectString() {
            return data;
        }
    }

    public static class DateraConnection {

        private int managementPort;
        private String managementIp;
        private String username;
        private String password;
        private String token;

        public DateraConnection(String managementIp, int managementPort, String username, String password) throws UnsupportedEncodingException, DateraError {
            this.managementPort = managementPort;
            this.managementIp = managementIp;
            this.username = username;
            this.password = password;
            this.token = DateraUtil.login(this);
        }

        public int getManagementPort() {
            return managementPort;
        }

        public String getManagementIp() {
            return managementIp;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getToken() {
            return token;
        }
    }

    public static class DateraLogin {

        private final String name;
        private final String password;

        public DateraLogin(String username, String password) {
            this.name = username;
            this.password = password;
        }
    }

    public static class DateraLoginResponse {

        private String key;

        public String getKey() {
            return key;
        }
    }

    public class Access {
        private String iqn;
        private List<String> ips;


        public Access(String iqn, List<String> ips) {
            this.iqn = iqn;
            this.ips = ips;
        }

        public String getIqn() {
            return iqn;
        }
    }

    public static class PerformancePolicy {

        @SerializedName("total_bandwidth_max")
        private Integer totalBandwidth;


        public PerformancePolicy(int totalBandwidthKiBps) {
            this.totalBandwidth = totalBandwidthKiBps;
        }

        public Integer getTotalBandwidth() {
            return totalBandwidth;
        }
    }

    public static class Volume {

        private String name;
        private String path;
        private Integer size;

        @SerializedName("replica_count")
        private Integer replicaCount;

        @SerializedName("performance_policy")
        private PerformancePolicy performancePolicy;

        @SerializedName("op_state")
        private String opState;

        public Volume(int size, int totalBandwidthKiBps, int replicaCount) {
            this.name = DEFAULT_VOLUME_NAME;
            this.size = size;
            this.replicaCount = replicaCount;
            this.performancePolicy = new PerformancePolicy(totalBandwidthKiBps);
        }

        public Volume(Integer newSize) {
            this.size=newSize;
        }

        public Volume(String path) {
            this.path=path;
        }

        public PerformancePolicy getPerformancePolicy() {
            return performancePolicy;
        }

        public int getSize() {
            return size;
        }

        public String getPath(){
            return path;
        }

        public String getOpState() {
            return opState;
        }
    }

    public static class StorageInstance {

        private final String name = DEFAULT_STORAGE_NAME;
        private List<Volume> volumes;
        private Access access;

        public StorageInstance(int size, int totalBandWidthKiBps, int replicaCount) {
            Volume volume = new Volume(size, totalBandWidthKiBps, replicaCount);
            volumes = new ArrayList<>();
            volumes.add(volume);
        }

        public Access getAccess(){
            return access;
        }

        public Volume getVolume() {
            return volumes.get(0);
        }

        public int getSize() {
            return getVolume().getSize();
        }

    }

    public static class AppInstance {

        private String name;

        @SerializedName("descr")
        private String description;

        @SerializedName("access_control_mode")
        private String accessControlMode;

        @SerializedName("create_mode")
        private String createMode;

        @SerializedName("storage_instances")
        private List<StorageInstance> storageInstances;

        @SerializedName("clone_volume_src")
        private Volume cloneVolumeSrc;

        @SerializedName("clone_snapshot_src")
        private VolumeSnapshot cloneSnapshotSrc;

        @SerializedName("admin_state")
        private String adminState;

        private Boolean force;


        public AppInstance(String name, String description, int size, int totalBandwidthKiBps, int replicaCount) {
            this.name = name;
            this.description = description;
            StorageInstance storageInstance = new StorageInstance(size, totalBandwidthKiBps, replicaCount);
            this.storageInstances = new ArrayList<>();
            this.storageInstances.add(storageInstance);
            this.accessControlMode = DEFAULT_ACL;
        }

        public AppInstance(AppState state) {
            this.adminState = state.toString();
            this.force = true;
        }

        public AppInstance(String name, String description, Volume cloneSrc) {
            this.name = name;
            this.description = description;
            this.cloneVolumeSrc = cloneSrc;
        }

        public AppInstance(String name, String description, VolumeSnapshot cloneSrc) {
            this.name = name;
            this.description = description;
            this.cloneSnapshotSrc = cloneSrc;
        }

        public String getIqn() {
            StorageInstance storageInstance = storageInstances.get(0);
            return storageInstance.getAccess().getIqn();
        }

        // Commenting this out because we are using bandwidth instead for now
        /* public int getTotalIops() {
            StorageInstance storageInstance = storageInstances.get(DEFAULT_STORAGE_NAME) ;
            PerformancePolicy performancePolicy = storageInstance.getVolume().getPerformancePolicy();

            return performancePolicy == null? -1 : performancePolicy.getTotalIops();
        }*/

        public int getTotalBandwidthKiBps() {
            StorageInstance storageInstance = storageInstances.get(0) ;
            PerformancePolicy performancePolicy = storageInstance.getVolume().getPerformancePolicy();

            return performancePolicy == null? -1 : performancePolicy.getTotalBandwidth();
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            StorageInstance storageInstance = storageInstances.get(0);
            return storageInstance.getSize();
        }

        public String getVolumePath(){
            StorageInstance storageInstance = storageInstances.get(0);
            return storageInstance.getVolume().getPath();
        }

        public String getVolumeOpState(){
            StorageInstance storageInstance = storageInstances.get(0);
            return storageInstance.getVolume().getOpState();
        }

        public String getAdminState() {
            return adminState;
        }
    }

    public static class Initiator {

        private String id; // IQN
        private String name;
        private String path;
        private String op;
        private boolean force;

        public Initiator(String name, String id, boolean force) {
            this.id = id;
            this.name = name;
            this.force = force;
        }

        public Initiator(String path, DateraOperation op){
            this.path = path;
            this.op = op.toString();
        }

        public String getPath() {
            return path;
        }
    }

    public static class InitiatorGroup {

        private String name;
        private List<Initiator> members;
        private String path;
        private String op;
        private boolean force;

        public InitiatorGroup(String name, List<Initiator> members, boolean force) {
            this.name = name;
            this.members = members;
            this.force = force;
        }

        public InitiatorGroup(String path, DateraOperation op) {
            this.path = path;
            this.op = op.toString();
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        public List<Initiator> getMembers() {
            return members;
        }
    }


    public static class VolumeSnapshot {

        private String uuid;
        private String timestamp;
        private String path;

        @SerializedName("op_state")
        private String opState;


        VolumeSnapshot() {
        }

        VolumeSnapshot(String path) {
            this.path = path;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getOpState() {
            return opState;
        }

        public String getPath(){
            return path;
        }
    }

    public static class DateraError extends Exception {

        private String name;
        private int code;
        private List<String> errors;
        private String message;

        public DateraError(String name, int code, List<String> errors, String message) {
            this.name = name;
            this.code = code;
            this.errors = errors;
            this.message = message;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean isError() {
            return message != null && name.endsWith("Error");
        }

        public String getMessage() {

            String errMesg = name  + "\n";
            if (message != null) {
                errMesg += message + "\n";
            }

            if (errors != null) {
                errMesg += StringUtils.join(errors, "\n");

            }

            return errMesg;
        }

        public String getName(){
            return name;
        }
    }
}
