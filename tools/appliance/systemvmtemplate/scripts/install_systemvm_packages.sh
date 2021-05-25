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

  ${apt_get} install grub-legacy \
    rsyslog logrotate cron net-tools ifupdown tmux vim-tiny htop netbase iptables nftables \
    openssh-server e2fsprogs tcpdump iftop socat wget coreutils systemd \
    ca-certificates bzip2 sed gawk diffutils grep gzip less tar telnet ftp rsync traceroute psmisc lsof procps \
    inetutils-ping iputils-arping httping curl \
    dnsutils zip unzip ethtool uuid file iproute2 acpid sudo \
    ipvsadm conntrackd libnetfilter-conntrack3 \
    sysstat \
    sharutils \
    xenstore-utils libxenstore3.0 \
    virt-what open-vm-tools qemu-guest-agent

  # Install xenserver guest utilities as debian repos don't have it
  wget https://mirrors.kernel.org/ubuntu/pool/main/x/xe-guest-utilities/xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb
  dpkg -i xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb
  rm -f xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb

  apt-get -y autoremove --purge
  apt-get clean
  apt-get autoclean
}

return 2>/dev/null || install_packages
