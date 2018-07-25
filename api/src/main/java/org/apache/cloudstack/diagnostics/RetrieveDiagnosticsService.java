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

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.PluggableService;
import org.apache.cloudstack.api.command.admin.diagnostics.RetrieveDiagnosticsCmd;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.framework.config.impl.DiagnosticsKey;

import javax.naming.ConfigurationException;
import java.util.List;
import java.util.Map;




public interface RetrieveDiagnosticsService extends Manager, PluggableService {

    String getDiagnosticsFiles(final RetrieveDiagnosticsCmd cmd) throws ResourceUnavailableException, OperationTimedoutException, ConcurrentOperationException, InvalidParameterValueException, ConfigurationException;

    boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException;

    Pair<List<? extends Configuration>, Integer> searchForDiagnosticsConfigurations(final RetrieveDiagnosticsCmd cmd);

    List<DiagnosticsKey> get(String key);

//    boolean cleanupDiagnostics(DeleteCommand cmd);
}
