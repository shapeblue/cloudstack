#!/usr/bin/python
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#   http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import logging
import socket
import os

UDP_IP_ADDRESS = "127.0.0.1"
UDP_PORT_NO = 1234

LOG_FILE='/var/log/cloudstack/agent/rolling-maintenance.log'
EXEC_FILE_PATH = "/etc/cloudstack/agent/rolling.d/exec"
ROLLING_MAINTENANCE_DIR = "/etc/cloudstack/agent/rolling.d"

logging.basicConfig(filename=LOG_FILE,
                    filemode='a',
                    format='%(asctime)s,%(msecs)d %(name)s %(levelname)s %(message)s',
                    datefmt='%H:%M:%S',
                    level=logging.INFO)
logger = logging.getLogger('rolling-maintenance')


def filter_custom_scripts(filename):
    return filename is not None and filename != "" and (filename.endswith(".sh") or filename.endswith(".py"))


def list_sorted_stage_scripts(stage):
    files = os.listdir(ROLLING_MAINTENANCE_DIR + "/" + stage)
    return sorted(filter(filter_custom_scripts, files))


def process(data):
    split = data.split(' ')
    stage = split[0]
    operation = split[1]
    scripts = list_sorted_stage_scripts(stage)
    for script in scripts:

    if operation == "start":
        exec_file = open(EXEC_FILE_PATH, 'w')
        exec_file.write(scripts)
        exec_file.close()


serverSock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
serverSock.bind((UDP_IP_ADDRESS, UDP_PORT_NO))

while True:
    data, addr = serverSock.recvfrom(1024)
    logger.debug("Received: " + data)
    process(data)

