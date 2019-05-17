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

package org.apache.cloudstack.virtualappliance;

import org.apache.cloudstack.virtualappliance.protobuf.ApplianceAgentGrpc;
import org.apache.cloudstack.virtualappliance.protobuf.PingRequest;
import org.apache.cloudstack.virtualappliance.protobuf.PingResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ApplianceAgentRpcClient {
    ApplianceAgentGrpc.ApplianceAgentBlockingStub blockingStub;

    public ApplianceAgentRpcClient() {
        ManagedChannel managedChannel = ManagedChannelBuilder
                .forAddress("localhost", 8200).usePlaintext().build();
        blockingStub = ApplianceAgentGrpc.newBlockingStub(managedChannel);
    }

    public ApplianceAgentRpcClient(String host, Integer port) {
        ManagedChannel managedChannel = ManagedChannelBuilder
                .forAddress(host, port).usePlaintext().build();
        blockingStub = ApplianceAgentGrpc.newBlockingStub(managedChannel);
    }

    public PingResponse ping(final PingRequest request) {
        return blockingStub.ping(request);
    }
}
