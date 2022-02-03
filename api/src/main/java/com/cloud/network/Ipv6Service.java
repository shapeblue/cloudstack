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
package com.cloud.network;

import java.util.List;

import org.apache.cloudstack.api.command.user.ipv6.CreateIpv6FirewallRuleCmd;
import org.apache.cloudstack.api.command.user.ipv6.ListIpv6FirewallRulesCmd;
import org.apache.cloudstack.api.command.user.ipv6.UpdateIpv6FirewallRuleCmd;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterGuestIpv6Prefix;
import com.cloud.dc.Vlan;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.rules.FirewallRule;
import com.cloud.utils.Pair;
import com.cloud.utils.component.PluggableService;
import com.cloud.vm.NicProfile;

public interface Ipv6Service extends PluggableService {

    public static final int IPV6_SLAAC_CIDR_NETMASK = 64;

    Pair<Integer, Integer> getUsedTotalIpv6SubnetForPrefix(DataCenterGuestIpv6Prefix prefix);

    Pair<Integer, Integer> getUsedTotalIpv6SubnetForZone(long zoneId);

    Pair<String, String> preAllocateIpv6SubnetForNetwork(long zoneId) throws ResourceAllocationException;

    void assignIpv6SubnetToNetwork(String subnet, long networkId);

    void releaseIpv6SubnetForNetwork(long networkId);

    Pair<String, ? extends Vlan> assignPublicIpv6ToNetwork(Network network, String nicMacAddress);

    void updateNicIpv6(NicProfile nic, DataCenter dc, Network network);

    void releasePublicIpv6ForNic(Network network, String nicIpv6Address);

    List<String> getPublicIpv6AddressesForNetwork(Network network);

    FirewallRule updateIpv6FirewallRule(UpdateIpv6FirewallRuleCmd updateIpv6FirewallRuleCmd);

    Pair<List<? extends FirewallRule>,Integer> listIpv6FirewallRules(ListIpv6FirewallRulesCmd listIpv6FirewallRulesCmd);

    boolean revokeIpv6FirewallRule(Long id);

    FirewallRule createIpv6FirewallRule(CreateIpv6FirewallRuleCmd createIpv6FirewallRuleCmd) throws NetworkRuleConflictException;

    FirewallRule getIpv6FirewallRule(Long entityId);

    boolean applyIpv6FirewallRule(long id);
}