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

package org.apache.cloudstack.hyperv;

import org.apache.http.client.config.AuthSchemes;
import org.apache.log4j.Logger;

import com.cloud.utils.Pair;

import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

public class HypervClient {
    private static final Logger LOG = Logger.getLogger(HypervClient.class);

    private WinRmTool winRmTool;
    private WinRmClientContext winRmContext;
    private String host;
    private String username;
    private String password;

    public HypervClient(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.init();
    }

    public void init() {
        if (this.winRmContext != null && this.winRmTool != null) {
            this.shutdown();
        }
        winRmContext = WinRmClientContext.newInstance();
        winRmTool = WinRmTool.Builder.builder(host, username, password)
                .authenticationScheme(AuthSchemes.NTLM)
                .port(5985)
                .disableCertificateChecks(true)
                .useHttps(false)
                .context(winRmContext)
                .build();
    }

    public void shutdown() {
        winRmContext.shutdown();
    }

    public WinRmTool getWinRmTool() {
        return winRmTool;
    }

    private WinRmToolResponse execute(String command) {
        final WinRmToolResponse response = winRmTool.executePs(command);
        if (response.getStatusCode() != 0) {
            LOG.error(String.format("Failed to execute command='%s', got stdout='%s', stderr='%s'",
                    command, response.getStdOut(), response.getStdErr()));
        }
        return response;
    }

    public Pair<Long, Long> getHostCpuInfo() {
        long cpuSpeed = 0;
        long cpuCores = 0;
        final WinRmToolResponse response = execute("Get-WmiObject Win32_Processor");
        for (final String line : response.getStdOut().split("\r\n")) {
            if (line.startsWith("MaxClockSpeed")) {
                cpuCores++;
                cpuSpeed = Long.valueOf(line.split(":")[1].trim(), 10);
            }
        }
        return new Pair<>(cpuCores, cpuSpeed);
    }

    public Long getHostMemory() {
        long memory = 0;
        final WinRmToolResponse response = execute("Get-WmiObject Win32_PhysicalMemory");
        for (final String line : response.getStdOut().split("\r\n")) {
            if (line.startsWith("Capacity")) {
                memory = Long.valueOf(line.split(":")[1].trim(), 10);
                break;
            }
        }
        return memory;
    }
}
