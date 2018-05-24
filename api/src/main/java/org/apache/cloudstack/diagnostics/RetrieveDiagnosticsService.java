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

package org.apache.cloudstack.diagnostics;


import com.cloud.exception.AgentUnavailableException;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.PluggableService;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.api.response.RetrieveDiagnosticsResponse;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.framework.config.ConfigKey;

import javax.naming.ConfigurationException;
import java.util.List;
import java.util.Map;


public interface RetrieveDiagnosticsService extends Manager, PluggableService {

    ConfigKey<Long> RetrieveDiagnosticsTimeOut = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.retrieval.timeout", "3600",
            "The timeout setting in seconds for the overall API call", true, ConfigKey.Scope.Global);
    ConfigKey<Boolean> enabledGCollector = new ConfigKey<>("Advanced", Boolean.class, "retrieveDiagnostics.gc.enabled",
            "true", "Garbage collection on/off switch (true|false", true, ConfigKey.Scope.Global);
    ConfigKey<String> RetrieveDiagnosticsFilePath = new ConfigKey<String>("Advanced", String.class, "retrieveDiagnostics.filepath",
            "/tmp", "File path to use on the management server for all temporary data. This allows CloudStack administrators to determine where best to place the files.", true, ConfigKey.Scope.Global);
    ConfigKey<Float> RetrieveDiagnosticsDisableThreshold = new ConfigKey<Float>("Advanced", Float.class, "retrieveDiagnostics.disablethreshold", "0.95",
            "The percentage disk space cut-off before API call will fail", true, ConfigKey.Scope.Global);

    ConfigKey<Long> RetrieveDiagnosticsFileAge = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.max.fileage", "86400",
            "The diagnostics file age in seconds before considered for garbage collection", true, ConfigKey.Scope.Global);

    ConfigKey<Long> RetrieveDiagnosticsInterval = new ConfigKey<Long>("Advanced", Long.class, "retrieveDiagnostics.gc.interval", "86400",
            "The interval between garbage collection executions in seconds", true, ConfigKey.Scope.Global);

    RetrieveDiagnosticsResponse getDiagnosticsFiles(RetrieveDiagnosticsCmd cmd) throws AgentUnavailableException, ConfigurationException;

    ConfigKey<?>[] getConfigKeys();


    boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException;
    Pair<List<? extends Configuration>, Integer> searchForDiagnosticsConfigurations(final RetrieveDiagnosticsCmd cmd);


}
