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
package org.apache.cloudstack.network.tungsten.api.command;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.network.tungsten.api.response.PemDataResponse;
import org.apache.log4j.Logger;

import javax.inject.Inject;

@APICommand(name = "getLoadBalancerSslCertificate", description = "get load balancer certificate", responseObject =
    PemDataResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class GetLoadBalancerSslCertificateCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(GetLoadBalancerSslCertificateCmd.class.getName());
    private static final String s_name = "getloadbalancersllcertificateresponse";

    @Inject
    private LoadBalancingRulesManager _lbMgr;

    @Parameter(name = ApiConstants.ID, type = CommandType.LONG, required = true, description = "the ID of Lb")
    private Long id;

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException,
        ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        LoadBalancingRule.LbSslCert lbSslCert = _lbMgr.getLbSslCert(id);
        if (lbSslCert != null) {
            String pemData = lbSslCert.getCert() + lbSslCert.getKey();
            PemDataResponse pemDataResponse = new PemDataResponse();
            pemDataResponse.setPemData(pemData);
            pemDataResponse.setResponseName(s_name);
            pemDataResponse.setObjectName("pemdata");
            setResponseObject(pemDataResponse);
        } else {
            throw new CloudRuntimeException("can not get pem data");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        Account account = CallContext.current().getCallingAccount();
        if (account != null) {
            return account.getId();
        }
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
