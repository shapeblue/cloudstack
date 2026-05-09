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
package org.apache.cloudstack.service;

import com.cloud.network.element.OvnVpcPeeringVO;
import org.apache.cloudstack.api.command.CreateVpcPeeringCmd;
import org.apache.cloudstack.api.command.DeleteVpcPeeringCmd;
import org.apache.cloudstack.api.command.DisableVpcPeeringCmd;
import org.apache.cloudstack.api.command.EnableVpcPeeringCmd;
import org.apache.cloudstack.api.command.ListVpcPeeringsCmd;
import org.apache.cloudstack.api.command.UpdateVpcPeeringCmd;
import org.apache.cloudstack.api.response.VpcPeeringResponse;

import java.util.List;

public interface OvnPeeringService {
    OvnVpcPeeringVO createVpcPeering(CreateVpcPeeringCmd cmd);
    OvnVpcPeeringVO updateVpcPeering(UpdateVpcPeeringCmd cmd);
    boolean deleteVpcPeering(DeleteVpcPeeringCmd cmd);
    boolean enableVpcPeering(EnableVpcPeeringCmd cmd);
    boolean disableVpcPeering(DisableVpcPeeringCmd cmd);
    List<VpcPeeringResponse> listVpcPeerings(ListVpcPeeringsCmd cmd);
    VpcPeeringResponse createVpcPeeringResponse(OvnVpcPeeringVO peering);
}
