/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.rolling.maintenance;

import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class RollingMaintenanceHelperImpl extends AdapterBase implements RollingMaintenanceHelper {

    private DatagramSocket serverSocket;
    private DatagramSocket clientSocket;
    private static final int ROLLING_MAINTENANCE_CLIENT_PORT = 1234;
    private static final int ROLLING_MAINTENANCE_SERVER_PORT = 1235;

    private byte[] receiveData = new byte[1024];

    private static final Logger s_logger = Logger.getLogger(RollingMaintenanceHelperImpl.class);
    private Map<String, Boolean> stageResults = new HashMap<>();

    private boolean running;
    private boolean complete;
    private boolean success;

    private String getErrorMessage(Exception e) {
        if (e instanceof SocketException) {
            return "Cannot connect to executor server on port " + ROLLING_MAINTENANCE_CLIENT_PORT;
        } else if (e instanceof UnknownHostException) {
            return "Unknown host";
        } else if (e instanceof IOException) {
            return "Cannot send message to executor server on port " + ROLLING_MAINTENANCE_CLIENT_PORT;
        } else {
            return "Unexpected exception";
        }
    }

    @Override
    public void startStage(String stage) throws CloudRuntimeException {
        try {
            clientSocket = new DatagramSocket();
            InetAddress address = InetAddress.getByName("localhost");
            String message = String.format("%s %s %s", stage, "test1", "1");
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.getBytes().length,
                    address, ROLLING_MAINTENANCE_CLIENT_PORT);
            clientSocket.send(packet);
            running = true;
            complete = false;
        } catch (IOException e) {
            String msg = getErrorMessage(e);
            s_logger.error(msg, e);
            throw new CloudRuntimeException(msg);
        }
    }

    @Override
    public boolean isStageCompleted(String stage) {
        return !running && complete;
    }

    @Override
    public boolean getCompletedStageResults() {
        return false;
    }

    private class Monitor extends ManagedContextRunnable {

        @Override
        protected void runInContext() {
            try {
                serverSocket = new DatagramSocket(ROLLING_MAINTENANCE_SERVER_PORT);
            } catch (SocketException e) {
                s_logger.error("Could not create socket for rolling maintenance on port " + ROLLING_MAINTENANCE_SERVER_PORT);
                return;
            }
            while (true) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);
                    String data = new String(receivePacket.getData());
                    s_logger.debug("Received rolling maintenance data on port " + ROLLING_MAINTENANCE_SERVER_PORT + " : " + data);
                    complete = true;
                } catch (IOException e) {
                    s_logger.error("Error when receiving rolling maintenance packet on port " + ROLLING_MAINTENANCE_SERVER_PORT);
                }
            }
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) {
        Thread thread = new Thread(new Monitor());
        thread.start();
        return true;
    }
}
