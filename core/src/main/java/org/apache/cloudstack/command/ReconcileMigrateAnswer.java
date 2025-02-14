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

package org.apache.cloudstack.command;

import com.cloud.vm.VirtualMachine;

import java.util.List;

public class ReconcileMigrateAnswer extends ReconcileAnswer {

    Long sourceHostId;
    VirtualMachine.State stateOnSourceHost;
    List<String> disksOnSourceHost;
    Long destinationHostId;
    VirtualMachine.State stateOnDestinationHost;
    List<String> disksOnDestinationHost;

    public ReconcileMigrateAnswer() {
    }

    public ReconcileMigrateAnswer(VirtualMachine.State stateOnSourceHost, VirtualMachine.State stateOnDestinationHost) {
        this.stateOnSourceHost = stateOnSourceHost;
        this.stateOnDestinationHost = stateOnDestinationHost;
    }

    public Long getSourceHostId() {
        return sourceHostId;
    }

    public void setSourceHostId(Long sourceHostId) {
        this.sourceHostId = sourceHostId;
    }

    public Long getDestinationHostId() {
        return destinationHostId;
    }

    public void setDestinationHostId(Long destinationHostId) {
        this.destinationHostId = destinationHostId;
    }

    public VirtualMachine.State getStateOnSourceHost() {
        return stateOnSourceHost;
    }

    public void setStateOnSourceHost(VirtualMachine.State stateOnSourceHost) {
        this.stateOnSourceHost = stateOnSourceHost;
    }

    public VirtualMachine.State getStateOnDestinationHost() {
        return stateOnDestinationHost;
    }

    public void setStateOnDestinationHost(VirtualMachine.State stateOnDestinationHost) {
        this.stateOnDestinationHost = stateOnDestinationHost;
    }

    public List<String> getDisksOnSourceHost() {
        return disksOnSourceHost;
    }

    public void setDisksOnSourceHost(List<String> disksOnSourceHost) {
        this.disksOnSourceHost = disksOnSourceHost;
    }

    public List<String> getDisksOnDestinationHost() {
        return disksOnDestinationHost;
    }

    public void setDisksOnDestinationHost(List<String> disksOnDestinationHost) {
        this.disksOnDestinationHost = disksOnDestinationHost;
    }
}
