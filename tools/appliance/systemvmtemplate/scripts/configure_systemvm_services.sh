#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -e
set -x

function configure_services() {
  mkdir -p /var/www/html
  mkdir -p /opt/cloud/bin
  mkdir -p /var/cache/cloud
  mkdir -p /usr/share/cloud
  mkdir -p /usr/local/cloud

  # Fix dnsmasq directory issue
  mkdir -p /opt/tftpboot

  # Fix haproxy directory issue
  mkdir -p /var/lib/haproxy

  systemctl daemon-reload
  systemctl disable apt-daily.service
  systemctl disable apt-daily.timer
  systemctl disable apt-daily-upgrade.timer

  # Disable services that slow down boot and are not used anyway
  systemctl disable console-setup
  systemctl disable x11-common
  systemctl disable sshd
}

return 2>/dev/null || configure_services
