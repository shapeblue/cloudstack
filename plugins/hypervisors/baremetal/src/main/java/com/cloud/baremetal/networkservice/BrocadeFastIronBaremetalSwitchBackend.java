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
package com.cloud.baremetal.networkservice;

import com.cloud.baremetal.manager.VlanType;
import com.cloud.utils.exception.CloudRuntimeException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;

public class BrocadeFastIronBaremetalSwitchBackend implements BaremetalSwitchBackend {

    private static final Logger s_logger = Logger.getLogger(BrocadeFastIronBaremetalSwitchBackend.class);
    public static final String TYPE = "Brocade";

    @Override
    public String getSwitchBackendType() {
        return TYPE;
    }

    @Override
    public void prepareVlan(BaremetalVlanStruct struct) {
        try {
            BrocadeManager bm = new BrocadeManager(struct.getSwitchIp(), struct.getSwitchUsername(), struct.getSwitchPassword());
            bm.assignVlanToPort(struct.getPort(), struct.getVlan(), struct.getVlanType());
        } catch (InterruptedException | JSchException | IOException e) {
            s_logger.warn("Error assigning VLAN to PORT", e);
            throw new CloudRuntimeException(e);
        }
    }

    @Override
    public void removePortFromVlan(BaremetalVlanStruct struct) {
        try {
            BrocadeManager bm = new BrocadeManager(struct.getSwitchIp(), struct.getSwitchUsername(), struct.getSwitchPassword());
            bm.removePortFromVlan(struct.getPort(), struct.getVlan(), struct.getVlanType());
        } catch (InterruptedException | JSchException | IOException e) {
            s_logger.warn("Error removing VLAN", e);
            throw new CloudRuntimeException(e);
        }
    }

    private class BrocadeManager {
        String user;
        String password;
        String ip;
        int port;

        public BrocadeManager(String ip, String user, String password) throws UnknownHostException {
            this.user = user;
            this.password = password;
            this.ip = ip;
            this.port = 22;

        }

        public void assignVlanToPort(String port, int vlanId, VlanType vlanType) throws IOException, JSchException, InterruptedException {

            String[] dualModeCmds = {
                    "en\n",
                    this.password + "\n",
                    "config t\n",
                    "int e " + port + "\n",
                    "dual-mode " + Integer.toString(vlanId) + "\n",
                    "end\n",
                    "exit\n",
                    "exit\n"
            };

            String[] tagCommands = {
                    "en\n",
                    this.password + "\n",
                    "config t\n",
                    "vlan " + Integer.toString(vlanId) + "\n",
                    "tagged e " + port + "\n",
                    "end\n",
                    "exit\n",
                    "exit\n"
            };

            executeCommands(tagCommands);

            //If it is a untagged VLAN, change the interface to dual mode and add it as a default VLAN
            if (vlanType.equals(VlanType.UNTAGGED)) {
                executeCommands(dualModeCmds);
            }

            // TODO: Check if vlan assignement was successful
        }

        public void removePortFromVlan(String port, int vlanId, VlanType vlanType) throws JSchException, InterruptedException {

            String[] dualModeCmds = {
                    "en\n",
                    this.password + "\n",
                    "config t\n",
                    "int e " + port + "\n",
                    "no dual-mode " + Integer.toString(vlanId) + "\n",
                    "end\n",
                    "exit\n",
                    "exit\n"
            };

            String[] untagCmds = {
                    "en\n",
                    this.password + "\n",
                    "config t\n",
                    "vlan " + Integer.toString(vlanId) + "\n",
                    "no tagged " + " e " + port + "\n",
                    "end\n",
                    "exit\n",
                    "exit\n"
            };

            if(vlanType.equals(VlanType.UNTAGGED)){
                executeCommands(dualModeCmds);
            }
            executeCommands(untagCmds);

            // TODO: Check if vlan removal was successful
        }

        private void executeCommands(String[] cmds) throws JSchException, InterruptedException {

            CommandInputStream cs = new CommandInputStream(cmds);

            JSch jsch=new JSch();
            Session session=jsch.getSession(user, ip, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect(300000);

            Channel channel = session.openChannel("shell");
            channel.setInputStream(cs);
            channel.connect(3 * 300000);

            while (!channel.isClosed()){
                Thread.sleep(1000);
            }
        }
    }

    private class CommandInputStream extends InputStream {

        private final String[] cmds;
        private int curCmd;
        private int curIdx;

        CommandInputStream(String[] cmds) {
            this.cmds =  cmds;
            this.curCmd = 0;
            this.curIdx = 0;
        }
        @Override
        public int read() throws IOException {

            if (curCmd >= cmds.length)
                return -1;


            String cmd = cmds[curCmd];

            char ch = cmd.charAt(curIdx);
            curIdx += 1;

            if (ch == '\n'){

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                curCmd++;
                curIdx = 0;
                s_logger.info("[BrocadeSwitchCmd] " + cmd);
            }

            return (int)ch;
        }
    }
}
