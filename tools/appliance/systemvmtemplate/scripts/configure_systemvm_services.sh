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

CLOUDSTACK_RELEASE=4.16.0

function configure_issue() {
  cat > /etc/issue <<EOF

   __?.o/  Apache CloudStack SystemVM $CLOUDSTACK_RELEASE
  (  )#    https://cloudstack.apache.org
 (___(_)   Debian GNU/Linux 10 \n \l

EOF
}

function install_cloud_scripts() {
  # ./cloud_scripts/ has been put there by ../../cloud_scripts_shar_archive.sh
  rsync -av ./cloud_scripts/ /

  chmod +x /opt/cloud/bin/* /opt/cloud/bin/setup/* \
    /root/{clearUsageRules.sh,reconfigLB.sh,monitorServices.py} \
    /etc/profile.d/cloud.sh /etc/cron.daily/* /etc/cron.hourly/*

  chmod +x /root/health_checks/*
  chmod -x /etc/systemd/system/*

  systemctl daemon-reload
  systemctl enable cloud-early-config
  systemctl enable cloud-postinit
}

function do_signature() {
  mkdir -p /var/cache/cloud/ /usr/share/cloud/
  (cd ./cloud_scripts/; tar -cvf - * | gzip > /usr/share/cloud/cloud-scripts.tgz)
  md5sum /usr/share/cloud/cloud-scripts.tgz | awk '{print $1}' > /var/cache/cloud/cloud-scripts-signature
  echo "Cloudstack Release $CLOUDSTACK_RELEASE $(date)" > /etc/cloudstack-release
}

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

  install_cloud_scripts
  do_signature

  systemctl daemon-reload
  systemctl disable apt-daily.service
  systemctl disable apt-daily.timer
  systemctl disable apt-daily-upgrade.timer

  # Disable services that slow down boot and are not used anyway
  systemctl disable conntrackd
  systemctl disable console-setup
  systemctl disable x11-common
  systemctl disable vgauth
  systemctl disable sshd

  # Disable guest services which will selectively be started based on hypervisor
  systemctl disable open-vm-tools
  systemctl disable xe-daemon
  systemctl disable qemu-guest-agent

  configure_issue
}

return 2>/dev/null || configure_services
