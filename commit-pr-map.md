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

# Commit to apache/cloudstack PR mapping

Range: `6fd8ddabe73` .. `c101689b6da` on `4.23.0-ceph` (50 commits, oldest first). No commit of the first 49 was changed.

| # | Commit | PR | Subject | Note |
|---|---|---|---|---|
| 1 | `6fd8ddabe73` | [#11931](https://github.com/apache/cloudstack/pull/11931) | smoke test: support ceph storage pools |  |
| 2 | `e913d82adf8` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: fix test failures |  |
| 3 | `5bc85a1f04f` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: add volume migration which has been fixed |  |
| 4 | `dc919fb6fba` | [#11931](https://github.com/apache/cloudstack/pull/11931), [#14386](https://github.com/apache/cloudstack/pull/14386) | test: support Ceph/RBD primary storage in NAS backup smoke test | shared: in both PRs (NAS backup smoke test for Ceph) |
| 5 | `6879406b9b6` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: fix smoke test failures on xen/vmware |  |
| 6 | `685225f4b14` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: sleep 10 seconds before attaching volume in test_events_resource.py |  |
| 7 | `c31f0a68efc` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: add more time.sleep(10) |  |
| 8 | `b00be0e7fc8` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: add more time.sleep(10) in test_vm_strict_host_tags.py |  |
| 9 | `1664f246b87` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: fix test_usage.py |  |
| 10 | `ddfcfd52779` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: move time.sleep(10) in test_host_maintenance.py |  |
| 11 | `37fd033ab71` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: retry domain deletion in tearDown of test_deploy_vms_in_parallel.py |  |
| 12 | `3a7b273a82b` | [#13628](https://github.com/apache/cloudstack/pull/13628) | test: fix tests failures caused by others in drs and host maintenance |  |
| 13 | `8890c121855` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: fix test_10_list_volumes for zone-wide storage pools |  |
| 14 | `689fb2cac63` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test_host_tags: fix cleanup to handle None host tags |  |
| 15 | `854a93feb7c` | [#14307](https://github.com/apache/cloudstack/pull/14307) | package: Add missing openssl and decompression utilities to KVM agent dependencies (PR 14307) | already ends with (PR 14307) |
| 16 | `e64a8d5f69e` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: support Host HA on Ceph RBD primary storage |  |
| 17 | `cfdd7fcd402` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: support zone-wide storage pools in test_list_storage_pools |  |
| 18 | `27d316ee542` | [#14362](https://github.com/apache/cloudstack/pull/14362) | KVM: allow cluster-wide storage pools for VM deployment from volumes |  |
| 19 | `270d76a24c4` | (none yet) | KVM: live volume migration on ceph | live volume migration between Ceph RBD: PR is still todo |
| 20 | `3bb8cc024c9` | (none yet) | smoke tests: exercise online volume migration for KVM on RBD | live volume migration between Ceph RBD: PR is still todo |
| 21 | `3a9e6091e93` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: address review comments on Host HA for Ceph RBD |  |
| 22 | `2296fb08d30` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: validate input of the RBD Host HA scripts and the Ceph settings of the pool |  |
| 23 | `be60d18dc8b` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: keep the Ceph key out of the logs and process list of the RBD Host HA checks, and harden the parsing |  |
| 24 | `6f92c9e8fcc` | [#11931](https://github.com/apache/cloudstack/pull/11931) | smoke test: cover RBD/librbd volume encryption in TestVolumeEncryption | added to PR 11931 on 2026-10-14 |
| 25 | `f08f4eb7768` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: run unmanage/import VM lifecycle test on KVM too | added to PR 11931 on 2026-10-14 |
| 26 | `88b92d6afe8` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: merge the direct download deploy tests into one test | added to PR 11931 on 2026-10-14 |
| 27 | `0771561166f` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvmvmactivity_rbd.sh: do not depend on python3, and tell failures from a DEAD host |  |
| 28 | `3f603969bb0` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvmvmactivity_rbd.sh: do not decide on an activity state which could not be saved |  |
| 29 | `ced2070ccdb` | [#14386](https://github.com/apache/cloudstack/pull/14386) | KVM: support incremental NAS backups of RBD volumes |  |
| 30 | `0c1c243801c` | [#14386](https://github.com/apache/cloudstack/pull/14386) | test: run the NAS backup smoke test on a cloud-init template |  |
| 31 | `d970fcbf131` | [#14386](https://github.com/apache/cloudstack/pull/14386) | test: validate a whole backup chain, including across a mid-chain delete |  |
| 32 | `700d5900f04` | [#14386](https://github.com/apache/cloudstack/pull/14386) | test: verify the guest of a VM created from a backup, not just that it boots |  |
| 33 | `f3b0305e8f0` | [#11931](https://github.com/apache/cloudstack/pull/11931) | test: sync the data to disk before taking the VM snapshot | added to PR 11931 on 2026-10-14 |
| 34 | `937542deebd` | [#14188](https://github.com/apache/cloudstack/pull/14188) | kvm: restore ErrorCode import removed by PR 13763 | per the author: fixes the compile error of #14188 (4.22), whose uses of ErrorCode lost their import when #13763 (main) removed it |
| 35 | `d964898c872` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: rewrite the RBD Host HA scripts in Python, with a faster VM-activity check |  |
| 36 | `d08fe694f36` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: choose the script which checks the VM activity by the type of the pool |  |
| 37 | `2a3717171c4` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: use only the Python bindings of librados and librbd in the Host HA scripts on Ceph RBD |  |
| 38 | `52cfddb59b8` | [#14393](https://github.com/apache/cloudstack/pull/14393) | kvm: remove the partial RBD image when a template copy fails |  |
| 39 | `1635bf004e0` | [#13877](https://github.com/apache/cloudstack/pull/13877) | kvm: test the NAS take-backup arguments with StringUtils and CollectionUtils | per the author: follow-up to the incremental NAS backup code from abh1sar's 4.22 backport (no commit title matches) |
| 40 | `a9505139484` | [#14386](https://github.com/apache/cloudstack/pull/14386) | nasbackup: carry the backup method separately from the mode |  |
| 41 | `c521e4c7c35` | [#14402](https://github.com/apache/cloudstack/pull/14402) | utils: add RbdUtils for the Ceph monitors of an RBD pool | owner is 14402; also stacked in 14320 and 14386 |
| 42 | `3472c0cece7` | [#14402](https://github.com/apache/cloudstack/pull/14402) | kvm: build every Ceph monitor list with RbdUtils |  |
| 43 | `a4fc4945c67` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: build the storage pool's Ceph monitors with RbdUtils |  |
| 44 | `91ee2e4cc71` | [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: tell a missing object apart from an unreachable cluster in the RBD HA scripts |  |
| 45 | `2dab8b2d86b` | [#14386](https://github.com/apache/cloudstack/pull/14386) | Add ceph-common as dependencies |  |
| 46 | `a48a4bcaeef` | [#14386](https://github.com/apache/cloudstack/pull/14386) | kvm: build the Ceph monitors of the restore backup command with RbdUtils |  |
| 47 | `320a603b4fd` | [#14386](https://github.com/apache/cloudstack/pull/14386) | backup: name the rbd method in the TakeBackupCommand comment |  |
| 48 | `f9b1fb9dbed` | [#14386](https://github.com/apache/cloudstack/pull/14386) | kvm: refuse an RBD restore that would destroy a snapshot we did not take |  |
| 49 | `ae74fa267a7` | [#14386](https://github.com/apache/cloudstack/pull/14386) | kvm: drop the cephx key file from a trap in nasbackup.sh |  |
| 50 | `c101689b6da` | [#14402](https://github.com/apache/cloudstack/pull/14402) | utils: test the escaped colons of an IPv6 Ceph monitor without a port | owner is 14402 (RbdUtils); the same change is the head commit of the other two PR branches that contain RbdUtils, as `708ba29888d` on #14402, `e9b6dda7d77` on [#14320](https://github.com/apache/cloudstack/pull/14320) and `39cc1041aeb` on [#14386](https://github.com/apache/cloudstack/pull/14386) |

## PRs

| PR | Title | Base | Author |
|---|---|---|---|
| [#11931](https://github.com/apache/cloudstack/pull/11931) | smoke test: support ceph storage pools | main | weizhouapache |
| [#13628](https://github.com/apache/cloudstack/pull/13628) | test: fix smoke test failures on xen/vmware | main | weizhouapache |
| [#14307](https://github.com/apache/cloudstack/pull/14307) | package: Add missing openssl and decompression utilities to KVM agent dependencies | main | weizhouapache |
| [#14320](https://github.com/apache/cloudstack/pull/14320) | kvm: support Host HA on Ceph RBD primary storage | main | weizhouapache |
| [#14362](https://github.com/apache/cloudstack/pull/14362) | KVM: allow cluster-wide storage pools for VM deployment from volumes | main | weizhouapache |
| [#14386](https://github.com/apache/cloudstack/pull/14386) | NAS backup: support incremental backups of RBD volumes | main | weizhouapache |
| [#14393](https://github.com/apache/cloudstack/pull/14393) | kvm: remove the partial RBD image when a template copy fails | 4.22 | weizhouapache |
| [#14402](https://github.com/apache/cloudstack/pull/14402) | utils,kvm: build every Ceph monitor list with RbdUtils | main | weizhouapache |
| [#14132](https://github.com/apache/cloudstack/pull/14132) | kvm: delete primary-only RBD snapshots before the volume | 4.22 | weizhouapache |
| [#13877](https://github.com/apache/cloudstack/pull/13877) | backport: Incremental NAS backup support for KVM (#13074) | 4.22 | abh1sar (merged) |
| [#14188](https://github.com/apache/cloudstack/pull/14188) | kvm: look RBD volumes up through librbd instead of refreshing the whole pool | 4.22 | bhouse-nexthop (merged) |

PR 14132 has no commit in this range. Live volume migration between Ceph RBD has no PR yet (todo).

Commit 50 was added after the first version of this table. Its three copies were pushed to the heads of PRs 14402, 14320 and 14386.
