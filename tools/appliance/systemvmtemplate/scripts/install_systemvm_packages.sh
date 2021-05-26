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

function debconf_packages() {
  echo 'sysstat sysstat/enable boolean true' | debconf-set-selections
  echo "libc6 libraries/restart-without-asking boolean false" | debconf-set-selections
}

function install_packages() {
  export DEBIAN_FRONTEND=noninteractive
  export DEBIAN_PRIORITY=critical
  local arch=`dpkg --print-architecture`

  debconf_packages

  local apt_get="apt-get --no-install-recommends -q -y"

  ${apt_get} install grub-legacy openssh-server coreutils systemd ca-certificates \
    bash tar gzip zip unzip grep lsof rsync acpid sudo

  apt-get -y autoremove --purge
  apt-get clean
  apt-get autoclean
}

return 2>/dev/null || install_packages
