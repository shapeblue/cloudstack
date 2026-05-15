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
<template>
  <div>
    <a-button
      type="dashed"
      style="width: 100%; margin-bottom: 12px;"
      :disabled="!('createMeshNetwork' in $store.getters.apis)"
      @click="openAddModal">
      <template #icon><plus-outlined /></template>
      {{ $t('label.add.vpc.to.mesh.network') }}
    </a-button>

    <a-table
      size="small"
      :columns="columns"
      :dataSource="members"
      :rowKey="item => item.id"
      :pagination="false"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'vpcname'">
          <a-tag :color="record.kind === 'network' ? 'green' : 'blue'" style="margin-right: 6px;">
            {{ record.kind === 'network' ? $t('label.isolated.network') : $t('label.vpc') }}
          </a-tag>
          <router-link v-if="record.kind === 'network'" :to="{ path: '/guestnetwork/' + record.networkid }">
            {{ record.networkname || record.membername }}
          </router-link>
          <router-link v-else :to="{ path: '/vpc/' + record.vpcid }">
            {{ record.vpcname || record.membername }}
          </router-link>
        </template>
        <template v-if="column.key === 'vpccidr'">
          {{ record.membercidr || record.vpccidr || record.networkcidr }}
        </template>
        <template v-if="column.key === 'aclname'">
          <span v-if="record.aclname">{{ record.aclname }}</span>
          <span v-else style="color: #aaa;">{{ $t('label.default.allow.all') }}</span>
        </template>
        <template v-if="column.key === 'state'">
          <status :text="record.state" displayText />
        </template>
        <template v-if="column.key === 'actions'">
          <a-tooltip :title="$t('label.edit.acl')">
            <a-button
              type="link"
              size="small"
              style="margin-right: 4px;"
              :disabled="!('updateMeshNetwork' in $store.getters.apis)"
              @click="openEditAcl(record)">
              <template #icon><setting-outlined /></template>
            </a-button>
          </a-tooltip>
          <a-popconfirm
            :title="$t('message.confirm.remove.vpc.from.mesh.network')"
            @confirm="removeMember(record)"
            :okText="$t('label.yes')"
            :cancelText="$t('label.no')">
            <a-tooltip :title="$t('label.remove')">
              <a-button
                type="link"
                danger
                size="small"
                :disabled="members.length <= 2">
                <template #icon><delete-outlined /></template>
              </a-button>
            </a-tooltip>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal
      :visible="showAddModal"
      :title="$t('label.add.vpc.to.mesh.network')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="showAddModal = false"
      width="480px">
      <a-spin :spinning="adding">
        <a-form layout="vertical">
          <a-form-item :label="$t('label.mesh.network.members')">
            <a-select
              v-model:value="newMemberKey"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.select')"
              :filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())">
              <a-select-option
                v-for="item in membersAvailable"
                :key="item.key"
                :value="item.key"
                :label="`[${item.kindLabel}] ${item.name} (${item.cidr}) - ${item.zonename}`">
                <a-tag :color="item.kind === 'vpc' ? 'blue' : 'green'" style="margin-right: 6px;">
                  {{ item.kindLabel }}
                </a-tag>
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="showAddModal = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="addMember" :disabled="!newMemberKey">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <a-modal
      :visible="showAclModal"
      :title="$t('label.edit.acl')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="showAclModal = false"
      width="520px">
      <a-spin :spinning="aclLoading">
        <a-form layout="vertical">
          <a-form-item :label="$t('label.vpc')">
            <strong>{{ aclTarget && aclTarget.vpcname }}</strong>
            <span v-if="aclTarget" style="color: #888;"> ({{ aclTarget.vpccidr }})</span>
          </a-form-item>
          <a-form-item :label="$t('label.aclid')">
            <a-select
              v-model:value="newAclId"
              showSearch
              optionFilterProp="label"
              allowClear
              :placeholder="$t('label.default.allow.all')"
              :filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())">
              <a-select-option
                v-for="acl in aclList"
                :key="acl.id"
                :value="acl.id"
                :label="acl.name + (acl.description ? ' (' + acl.description + ')' : '')">
                {{ acl.name }}<span v-if="acl.description" style="color: #888;"> ({{ acl.description }})</span>
              </a-select-option>
            </a-select>
            <div style="color: #888; font-size: 12px; margin-top: 4px;">
              {{ $t('message.mesh.network.acl.scope') }}
            </div>
          </a-form-item>
          <div class="action-button">
            <a-button @click="showAclModal = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="saveAcl">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import Status from '@/components/widgets/Status'

export default {
  name: 'MeshNetworkMembersTab',
  components: { Status },
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      allVpcs: [],
      allIsolatedNetworks: [],
      usedKeys: new Set(),
      showAddModal: false,
      newMemberKey: undefined,
      adding: false,
      showAclModal: false,
      aclLoading: false,
      aclTarget: null,
      aclList: [],
      newAclId: undefined
    }
  },
  computed: {
    members () {
      return this.resource?.members || []
    },
    columns () {
      return [
        { key: 'vpcname', title: this.$t('label.vpc'), dataIndex: 'vpcname' },
        { key: 'vpccidr', title: this.$t('label.cidr'), dataIndex: 'vpccidr', width: 140 },
        { key: 'linklocalip', title: this.$t('label.link.local.ip'), dataIndex: 'linklocalip', width: 140 },
        { key: 'zonename', title: this.$t('label.zone'), dataIndex: 'zonename', width: 140 },
        { key: 'aclname', title: this.$t('label.aclid'), dataIndex: 'aclname', width: 180 },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state', width: 90 },
        { key: 'actions', title: '', dataIndex: 'actions', width: 90, align: 'center' }
      ]
    },
    membersAvailable () {
      const items = []
      for (const v of this.allVpcs) {
        if (this.usedKeys.has('vpc:' + v.id)) continue
        items.push({
          key: 'vpc:' + v.id,
          kind: 'vpc',
          kindLabel: this.$t('label.vpc'),
          id: v.id,
          name: v.name,
          cidr: v.cidr,
          zonename: v.zonename
        })
      }
      for (const n of this.allIsolatedNetworks) {
        if (this.usedKeys.has('network:' + n.id)) continue
        items.push({
          key: 'network:' + n.id,
          kind: 'network',
          kindLabel: this.$t('label.isolated.network'),
          id: n.id,
          name: n.name,
          cidr: n.cidr,
          zonename: n.zonename
        })
      }
      return items
    }
  },
  methods: {
    openAddModal () {
      this.newMemberKey = undefined
      this.showAddModal = true
      this.fetchAllMembers()
    },
    fetchAllMembers () {
      Promise.all([
        getAPI('listVPCs', { listAll: true }),
        getAPI('listNetworks', { listAll: true, type: 'Isolated' }),
        getAPI('listMeshNetworks')
      ]).then(([vpcResp, netResp, peerResp]) => {
        this.allVpcs = vpcResp.listvpcsresponse?.vpc || []
        const networks = netResp.listnetworksresponse?.network || []
        this.allIsolatedNetworks = networks.filter(n => {
          if (n.vpcid) return false
          if (n.broadcastdomaintype !== 'OVN') return false
          if (n.state !== 'Implemented') return false
          return true
        })
        const groups = peerResp.listmeshnetworksresponse?.meshnetwork || []
        const used = new Set()
        for (const g of groups) {
          for (const m of (g.members || [])) {
            if (m.vpcid) used.add('vpc:' + m.vpcid)
            if (m.networkid) used.add('network:' + m.networkid)
          }
        }
        this.usedKeys = used
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    paramsForKey (key, peer) {
      const [kind, id] = key.split(':')
      if (peer) {
        return kind === 'vpc' ? { peervpcid: id } : { peernetworkid: id }
      }
      return kind === 'vpc' ? { vpcid: id } : { networkid: id }
    },
    async addMember () {
      if (!this.newMemberKey || this.members.length === 0) return
      this.adding = true
      try {
        const existing = this.members[0]
        const existingKey = existing.kind === 'network'
          ? 'network:' + existing.networkid
          : 'vpc:' + existing.vpcid
        await postAPI('createMeshNetwork', {
          name: this.resource.name,
          ...this.paramsForKey(this.newMemberKey, false),
          ...this.paramsForKey(existingKey, true)
        })
        this.$message.success(this.$t('message.success.add.mesh.network'))
        this.showAddModal = false
        this.$emit('refresh-data')
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.adding = false
      }
    },
    async removeMember (record) {
      try {
        await postAPI('deleteMeshNetwork', { id: record.id })
        this.$message.success(this.$t('message.success.remove.vpc.from.mesh.network'))
        this.$emit('refresh-data')
      } catch (error) {
        this.$notifyError(error)
      }
    },
    openEditAcl (record) {
      this.aclTarget = record
      this.newAclId = record.aclid || undefined
      this.aclList = []
      this.showAclModal = true
      this.fetchAclsForVpc(record.vpcid)
    },
    fetchAclsForVpc (vpcid) {
      this.aclLoading = true
      // The networkacl listing returns ACLs that belong to either THIS VPC
      // (vpcid filter) OR the system "default_allow" / "default_deny" lists,
      // which apply to any VPC. listAll=true gives us both.
      getAPI('listNetworkACLLists', { vpcid, listAll: true }).then(json => {
        const lists = json.listnetworkacllistsresponse?.networkacllist || []
        this.aclList = lists.filter(a => !a.name || a.name !== 'default_deny' || a.vpcid)
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.aclLoading = false
      })
    },
    async saveAcl () {
      if (!this.aclTarget) return
      this.aclLoading = true
      try {
        const params = { id: this.aclTarget.id }
        if (this.newAclId) {
          params.aclid = this.newAclId
        }
        await postAPI('updateMeshNetwork', params)
        this.$message.success(this.$t('message.success.update.mesh.network'))
        this.showAclModal = false
        this.$emit('refresh-data')
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.aclLoading = false
      }
    }
  }
}
</script>

<style scoped>
.action-button {
  text-align: right;
  margin-top: 16px;
}
.action-button button {
  margin-left: 8px;
}
</style>
