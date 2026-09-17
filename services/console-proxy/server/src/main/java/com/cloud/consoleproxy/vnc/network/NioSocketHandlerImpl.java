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
package com.cloud.consoleproxy.vnc.network;


import com.cloud.consoleproxy.ConsoleProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;

public class NioSocketHandlerImpl implements NioSocketHandler {

    private NioSocketInputStream inputStream;
    private NioSocketOutputStream outputStream;
    private boolean isTLS = false;
    private final NioSocket socket;

    protected Logger logger = LogManager.getLogger(getClass());

    public NioSocketHandlerImpl(NioSocket socket) {
        this.socket = socket;
        this.inputStream = new NioSocketInputStream(ConsoleProxy.defaultBufferSize, socket);
        this.outputStream = new NioSocketOutputStream(ConsoleProxy.defaultBufferSize, socket);
    }

    @Override
    public int readUnsignedInteger(int sizeInBits) {
        return inputStream.readUnsignedInteger(sizeInBits);
    }

    @Override
    public void writeUnsignedInteger(int sizeInBits, int value) {
        outputStream.writeUnsignedInteger(sizeInBits, value);
    }

    @Override
    public void readBytes(ByteBuffer data, int length) {
        inputStream.readBytes(data, length);
    }

    private static final long STALL_WARNING_INTERVAL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);

    @Override
    public void waitForBytesAvailableForReading(int bytes) {
        long startTime = System.nanoTime();
        long lastLogTime = startTime;
        long cycles = 0;
        while (!inputStream.checkForSizeWithoutWait(bytes)) {
            logger.trace("Waiting for inStream to be ready");
            cycles++;
            if (cycles % 1_000_000 == 0) {
                long now = System.nanoTime();
                if (now - lastLogTime >= STALL_WARNING_INTERVAL_NANOS) {
                    logger.debug("Still waiting for {} byte(s) from the VNC backend socket after {} ms",
                            bytes, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(now - startTime));
                    lastLogTime = now;
                }
            }
        }
    }

    @Override
    public void writeBytes(byte[] data, int dataPtr, int length) {
        outputStream.writeBytes(data, dataPtr, length);
    }

    @Override
    public void writeBytes(ByteBuffer data, int length) {
        outputStream.writeBytes(data, length);
    }

    @Override
    public void flushWriteBuffer() {
        outputStream.flushWriteBuffer();
    }

    @Override
    public void startTLSConnection(NioSocketSSLEngineManager sslEngineManager) {
        this.inputStream = new NioSocketTLSInputStream(sslEngineManager, this.inputStream.socket);
        this.outputStream = new NioSocketTLSOutputStream(sslEngineManager, this.outputStream.socket);
        this.isTLS = true;
    }

    @Override
    public boolean isTLSConnection() {
        return this.isTLS;
    }

    @Override
    public String readString() {
        return inputStream.readString();
    }

    @Override
    public byte[] readServerInit() {
        return inputStream.readServerInit();
    }

    @Override
    public int readAvailableDataIntoBuffer(ByteBuffer buffer, int maxSize) {
        return inputStream.readAvailableDataIntoBuffer(buffer, maxSize);
    }

    @Override
    public NioSocketInputStream getInputStream() {
        return inputStream;
    }

    @Override
    public NioSocketOutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() {
        socket.close();
    }
}
