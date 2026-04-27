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
set -x
PATH="/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin"
CMDLINE=/var/cache/cloud/cmdline
PROPERTIES=usr/local/cloud/systemvm/conf/agent.properties

rm -f /var/cache/cloud/enabled_svcs
rm -f /var/cache/cloud/disabled_svcs

. /lib/lsb/init-functions
. /opt/cloud/bin/setup/common.sh

log_it() {
  echo "$(date) $@" >> /var/log/cloud.log
  log_action_msg "$@"
}

patch_systemvm() {
  local patchfile=$1
  local backupfolder="/tmp/.conf.backup"
  local logfile="/var/log/patchsystemvm.log"
  if [ -f /usr/local/cloud/systemvm/conf/cloud.jks ]; then
    rm -fr $backupfolder
    mkdir -p $backupfolder
    cp -r /usr/local/cloud/systemvm/conf/* $backupfolder/
  fi
  rm /usr/local/cloud/systemvm -rf
  mkdir -p /usr/local/cloud/systemvm
  ls -lrt $patchfile

  log_it "Unziping $patchfile"
  echo "All" | unzip $patchfile -d /usr/local/cloud/systemvm >>$logfile 2>&1

  if [ "$TYPE" = "secstorage" ]; then
    log_it "As system VM type is $TYPE, we will remove the default properties 'instance' and 'resource' from $PROPERTIES"
    sed -i '/instance=/d' $PROPERTIES
    sed -i '/resource=/d' $PROPERTIES
  fi

  log_it "Copying content of $CMDLINE to $PROPERTIES"
  for element in $(cat $CMDLINE)
  do
    echo $element >> $PROPERTIES
  done

  find /usr/local/cloud/systemvm/ -name \*.sh | xargs chmod 555
  if [ -f $backupfolder/cloud.jks ]; then
    cp -r $backupfolder/* /usr/local/cloud/systemvm/conf/
    echo "Restored keystore file and certs using backup" >> $logfile
  fi
  rm -fr $backupfolder

  setup_agent_keystore || return 1

  # Import global cacerts into 'cloud' service's keystore
  keytool -importkeystore -srckeystore /etc/ssl/certs/java/cacerts -destkeystore /usr/local/cloud/systemvm/certs/realhostip.keystore -srcstorepass changeit -deststorepass vmops.com -noprompt || true
  return 0
}

decode_boot_arg() {
  printf '%s' "$1" | base64 -d 2>/dev/null | tr '^' '\n' | tr '~' ' '
}

setup_agent_keystore() {
  parse_cmd_line

  if [ -z "${KEYSTORE_PSSWD// }" ] || [ -z "${CERTIFICATE// }" ] || [ -z "${CACERTIFICATE// }" ]; then
    log_it "Skipping agent keystore setup as certificate boot arguments are missing"
    return 0
  fi

  local propsfile="/usr/local/cloud/systemvm/conf/agent.properties"
  local ksfile="/usr/local/cloud/systemvm/conf/cloud.jks"
  local certfile="/usr/local/cloud/systemvm/conf/cloud.crt"
  local cacertfile="/usr/local/cloud/systemvm/conf/cloud.ca.crt"
  local keyfile="/usr/local/cloud/systemvm/conf/cloud.key"
  local import_script="/usr/local/cloud/systemvm/scripts/util/keystore-cert-import"
  local ks_pass
  local cert
  local cacert
  local privatekey

  ks_pass=$(decode_boot_arg "$KEYSTORE_PSSWD")
  cert=$(decode_boot_arg "$CERTIFICATE")
  cacert=$(decode_boot_arg "$CACERTIFICATE")
  if [ -n "${PRIVATEKEY// }" ]; then
    privatekey=$(decode_boot_arg "$PRIVATEKEY")
  fi

  sed -i "/^keystore.passphrase=/d" "$propsfile"
  echo "keystore.passphrase=$ks_pass" >> "$propsfile"

  if [ ! -x "$import_script" ]; then
    log_it "Unable to setup agent keystore, missing $import_script"
    return 1
  fi

  "$import_script" "$propsfile" "$ks_pass" "$ksfile" "agent" "$certfile" "$cert" "$cacertfile" "$cacert" "$keyfile" "$privatekey"
}

patch() {
  local PATCH_MOUNT=/var/cache/cloud/
  local logfile="/var/log/patchsystemvm.log"

  if { [ "$TYPE" == "consoleproxy" ] || [ "$TYPE" == "secstorage" ]; } && [ -f ${PATCH_MOUNT}/agent.zip ] && [ -f /var/cache/cloud/patch.required ]
  then
    echo "Patching systemvm for cloud service with mount=$PATCH_MOUNT for type=$TYPE" >> $logfile
    patch_systemvm ${PATCH_MOUNT}/agent.zip
    if [ $? -gt 0 ]
    then
      echo "Failed to apply patch systemvm\n" >> $logfile
      exit 1
    fi
  fi

  rm -f /var/cache/cloud/patch.required
  chmod -x /etc/systemd/system/cloud*.service
  systemctl daemon-reload

  return 0
}

config_sysctl() {
  # When there is more memory reset the cache back pressure to default 100
  physmem=$(free|awk '/^Mem:/{print $2}')
  if [ $((physmem)) -lt 409600 ]; then
      sed  -i "/^vm.vfs_cache_pressure/ c\vm.vfs_cache_pressure = 200" /etc/sysctl.conf
  else
      sed  -i "/^vm.vfs_cache_pressure/ c\vm.vfs_cache_pressure = 100" /etc/sysctl.conf
  fi

  sync
  sysctl -p
}

bootstrap() {
  log_it "Bootstrapping systemvm appliance"

  export TYPE=$(grep -Po 'type=\K[a-zA-Z]*' $CMDLINE)
  patch
  config_sysctl

  log_it "Configuring systemvm type=$TYPE"
  if [ -f "/opt/cloud/bin/setup/$TYPE.sh" ]; then
      /opt/cloud/bin/setup/$TYPE.sh
  else
      /opt/cloud/bin/setup/default.sh
  fi

  if [ -f /var/cache/cloud/cloud-scripts.tgz ];then
    sha512sum /var/cache/cloud/cloud-scripts.tgz | awk '{print $1}' > /var/cache/cloud/cloud-scripts-signature
  fi

  log_it "Finished setting up systemvm"
  exit 0
}

bootstrap
