<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 -->

# Veeam Control Service Plugin

The Veeam Control Service runs an oVirt-compatible REST API on the management server, so that Veeam Backup &
Replication can back up and restore CloudStack KVM Instances through its oVirt integration.

## Settings

All settings are global settings, changed with `updateConfiguration` or in the UI. Settings marked as not
dynamic take effect after a management server restart.

| Setting | Default | Dynamic | Description |
|---|---|---|---|
| `integration.veeam.control.enabled` | `false` | No | Enables the Veeam Control Service REST API server. |
| `integration.veeam.control.bind.address` | empty | No | Address the REST API server binds to. Can be set per management server. |
| `integration.veeam.control.port` | `8090` | No | Port of the REST API server. |
| `integration.veeam.control.context.path` | `/ovirt-engine` | No | Context path of the REST API server. |
| `integration.veeam.control.api.username` | `veeam` | Yes | Username for Basic authentication. |
| `integration.veeam.control.api.password` | `change-me` | Yes | Password for Basic authentication. Change it before enabling the service. |
| `integration.veeam.control.service.account` | empty | Yes | ID of the account the service uses to act on resources. It must be a Root Admin account. |
| `integration.veeam.control.instance.restore.assign.owner` | `false` | Yes | Tries to assign a restored Instance to its owner, based on the OVF and network details. If this is `false` or the assignment fails, the Instance stays owned by the service account. |
| `integration.veeam.control.instance.encrypt.volumes` | `false` | Yes | Always uses offerings with encryption enabled when creating volumes and Instances. When `false`, offerings are chosen from backup metadata, availability and order, without looking at encryption. Can be set per zone. |
| `integration.veeam.control.allowed.client.cidrs` | empty | Yes | Comma-separated CIDRs of the clients allowed to use the API, e.g. `192.168.1.0/24,192.168.2.100/32`. When empty, all clients are allowed. |
| `integration.veeam.control.job.wait.timeout` | `3600` | Yes | Seconds that a synchronous request waits for its CloudStack job. When it runs out, the request fails with `504 Gateway Timeout`, but the job keeps running and may still complete. Values of 0 or less use the default. |
| `integration.veeam.control.guest.os.type.overrides` | empty | Yes | Overrides for the oVirt OS type reported for Instances. See [Guest OS type mapping](#guest-os-type-mapping). |
| `integration.veeam.control.developer.logs` | `false` | No | Logs API requests, responses and internal operations in detail, for troubleshooting. |

## Guest OS type mapping

Every Instance returned by the API has an oVirt OS type in `os.type`, e.g. `rhel_9x64` or `windows_2022`.
Veeam uses it to tell Windows guests from Linux guests, for example to pick the mount server for file-level
restore. A guest it can't identify is recorded as "Other", and file-level restore may not work for it.

The OS type comes from the display name of the Instance's guest OS type in CloudStack:

- Each distribution and version gets the closest oVirt key that Veeam recognizes, e.g. `Rocky Linux 9` gives
  `rockylinux_9x64`, `CentOS 7` gives `rhel_7x64` and `Windows Server 2012 R2 (64-bit)` gives
  `windows_2012R2x64`.
- Newer versions get the newest key Veeam recognizes for that distribution, e.g. Ubuntu 18.04 and later give
  `ubuntu_18_04`, Debian 9 and later give `debian_9`, and any SUSE version gives `sles_11`.
- Other Linux guests give `other_linux`, and anything else gives `other`.

### Overriding the OS type

If a guest OS type maps to the wrong key, for example a custom guest OS type, or a new key that Veeam has
started to recognize, set `integration.veeam.control.guest.os.type.overrides`. It takes entries of the form
`<guest OS display name>=<oVirt OS type>`, separated by `;` or by new lines:

```
VMware Photon (64 bit)=other_linux;My Hardened Linux 9=rhel_9x64
```

- Guest OS names must be the full display name. They are matched ignoring case and extra spaces.
- An override replaces the built-in mapping for that guest OS type only. Others keep the built-in mapping.
- OS types aren't checked. Use a key from oVirt's
  [osinfo-defaults.properties](https://github.com/oVirt/ovirt-engine/blob/master/packaging/conf/osinfo-defaults.properties)
  that Veeam recognizes.
- Malformed entries are ignored, with a warning in the management server log.
- Changes apply to the next API request, without a restart.

The OS family written to OVF descriptions follows the OS type: `windows_*` keys are Windows, `other`,
`freebsd*` and `solaris*` are other, and all other keys are Linux.
