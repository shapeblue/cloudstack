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
package com.cloud.agent.api;

import static com.cloud.resource.RollingMaintenanceService.Stage;

public class RollingMaintenanceCommand extends Command {

    private Stage stage;
    private String command;
    private String type;
    private boolean terminate;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public boolean isTerminate() {
        return terminate;
    }

    public void setTerminate(boolean terminate) {
        this.terminate = terminate;
    }

    public RollingMaintenanceCommand(Stage stage) {
        this.stage = stage;
    }

    public RollingMaintenanceCommand(String command, String type) {
        this.command = command;
        this.type = type;
    }

    public String getCommand() {
        return command;
    }

    public Stage getStage() {
        return this.stage;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
