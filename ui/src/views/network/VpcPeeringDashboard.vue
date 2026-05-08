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
  <a-spin :spinning="loading">
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
      <a-button
        type="primary"
        @click="openCreateModal"
        :disabled="!('createVpcPeering' in $store.getters.apis)">
        <template #icon><plus-outlined /></template>
        {{ $t('label.add.vpc.peering') }}
      </a-button>
      <a-button @click="fetchData" :loading="loading">
        <template #icon><reload-outlined /></template>
      </a-button>
    </div>

    <a-collapse v-model:activeKey="activeGroups" v-if="peeringGroups.length > 0">
      <a-collapse-panel
        v-for="group in peeringGroups"
        :key="group.groupuuid"
        :header="groupHeader(group)">
        <template #extra>
          <a-popconfirm
            :title="$t('message.confirm.delete.vpc.peering.group')"
            @confirm.stop="handleDeleteGroup(group)"
            :okText="$t('label.yes')"
            :cancelText="$t('label.no')">
            <a-button
              type="link"
              danger
              size="small"
              @click.stop>
              <template #icon><delete-outlined /></template>
              {{ $t('label.delete') }}
            </a-button>
          </a-popconfirm>
        </template>

        <a-table
          size="small"
          :columns="memberColumns"
          :dataSource="group.members"
          :rowKey="item => item.id"
          :pagination="false">
          <template #bodyCell="{ column, text, record }">
            <template v-if="column.key === 'vpcname'">
              <router-link :to="{ path: '/vpc/' + record.vpcid }">
                {{ text }}
              </router-link>
            </template>
            <template v-if="column.key === 'aclname'">
              <span>{{ text || $t('label.default.allow.all') }}</span>
              <a-button
                type="link"
                size="small"
                @click="openEditAclModal(record)"
                v-if="'updateVpcPeering' in $store.getters.apis">
                <template #icon><edit-outlined /></template>
              </a-button>
            </template>
            <template v-if="column.key === 'actions'">
              <a-popconfirm
                :title="$t('message.confirm.delete.vpc.peering')"
                @confirm="handleDeletePeering(record)"
                :okText="$t('label.yes')"
                :cancelText="$t('label.no')">
                <tooltip-button
                  tooltipPlacement="bottom"
                  :tooltip="$t('label.remove')"
                  type="primary"
                  :danger="true"
                  icon="delete-outlined"
                  size="small" />
              </a-popconfirm>
            </template>
          </template>
        </a-table>

        <div style="margin-top: 12px;">
          <a-button
            size="small"
            @click="openAddVpcToGroupModal(group)">
            <template #icon><plus-outlined /></template>
            {{ $t('label.add.vpc.to.peering') }}
          </a-button>
        </div>
      </a-collapse-panel>
    </a-collapse>

    <a-empty v-else :description="$t('label.no.data')" />

    <!-- Create Peering Modal -->
    <a-modal
      :visible="modals.create"
      :title="$t('label.add.vpc.peering')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="modals.create = false">
      <a-spin :spinning="modals.createLoading">
        <a-form layout="vertical" :model="form" :rules="rules" ref="createFormRef">
          <a-form-item :label="$t('label.vpc')" name="vpcid">
            <a-select
              v-model:value="form.vpcid"
              v-focus="true"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.select')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in allVpcs"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="$t('label.peer.vpc')" name="peervpcid">
            <a-select
              v-model:value="form.peervpcid"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.select')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in filteredPeerVpcs"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="$t('label.aclid')">
            <a-select
              v-model:value="form.aclid"
              showSearch
              optionFilterProp="label"
              allowClear
              :placeholder="$t('label.default.allow.all')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in aclListForVpc"
                :key="item.id"
                :value="item.id"
                :label="item.name">
                {{ item.name }} {{ item.description ? '(' + item.description + ')' : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="modals.create = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="handleCreatePeering">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- Add VPC to Group Modal -->
    <a-modal
      :visible="modals.addToGroup"
      :title="$t('label.add.vpc.to.peering')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="modals.addToGroup = false">
      <a-spin :spinning="modals.addToGroupLoading">
        <a-form layout="vertical" :model="form">
          <a-form-item :label="$t('label.vpc')">
            <a-select
              v-model:value="form.newvpcid"
              v-focus="true"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.select')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in vpcsNotInGroup"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="$t('label.aclid')">
            <a-select
              v-model:value="form.aclid"
              showSearch
              optionFilterProp="label"
              allowClear
              :placeholder="$t('label.default.allow.all')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in aclListForNewVpc"
                :key="item.id"
                :value="item.id"
                :label="item.name">
                {{ item.name }} {{ item.description ? '(' + item.description + ')' : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="modals.addToGroup = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="handleAddVpcToGroup">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- Edit ACL Modal -->
    <a-modal
      :visible="modals.editAcl"
      :title="$t('label.edit.acl')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="modals.editAcl = false">
      <a-spin :spinning="modals.editAclLoading">
        <a-form layout="vertical" :model="form">
          <a-form-item :label="$t('label.aclid')">
            <a-select
              v-model:value="form.aclid"
              v-focus="true"
              showSearch
              optionFilterProp="label"
              allowClear
              :placeholder="$t('label.default.allow.all')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in aclListForEdit"
                :key="item.id"
                :value="item.id"
                :label="item.name">
                {{ item.name }} {{ item.description ? '(' + item.description + ')' : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="modals.editAcl = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="handleUpdateAcl">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>
  </a-spin>
</template>

<script>
import { reactive } from 'vue'
import { getAPI, postAPI } from '@/api'
import TooltipButton from '@/components/widgets/TooltipButton'

export default {
  name: 'VpcPeeringDashboard',
  components: {
    TooltipButton
  },
  data () {
    return {
      loading: false,
      peeringGroups: [],
      allVpcs: [],
      activeGroups: [],
      aclLists: {},
      selectedGroup: null,
      editingPeering: null,
      modals: {
        create: false,
        createLoading: false,
        addToGroup: false,
        addToGroupLoading: false,
        editAcl: false,
        editAclLoading: false
      },
      form: reactive({
        vpcid: undefined,
        peervpcid: undefined,
        aclid: undefined,
        newvpcid: undefined
      }),
      rules: {
        vpcid: [{ required: true, message: this.$t('label.required') }],
        peervpcid: [{ required: true, message: this.$t('label.required') }]
      },
      memberColumns: [
        {
          key: 'vpcname',
          title: this.$t('label.vpc'),
          dataIndex: 'vpcname'
        },
        {
          title: 'CIDR',
          dataIndex: 'vpccidr'
        },
        {
          title: this.$t('label.zone'),
          dataIndex: 'zonename'
        },
        {
          title: this.$t('label.link.local.ip'),
          dataIndex: 'linklocalip'
        },
        {
          key: 'aclname',
          title: this.$t('label.aclid'),
          dataIndex: 'aclname'
        },
        {
          key: 'actions',
          title: '',
          dataIndex: 'actions',
          width: 60
        }
      ]
    }
  },
  computed: {
    filteredPeerVpcs () {
      return this.allVpcs.filter(v => v.id !== this.form.vpcid)
    },
    vpcsNotInGroup () {
      if (!this.selectedGroup) return this.allVpcs
      const memberVpcIds = new Set(this.selectedGroup.members.map(m => m.vpcid))
      return this.allVpcs.filter(v => !memberVpcIds.has(v.id))
    },
    aclListForVpc () {
      if (!this.form.vpcid) return []
      return this.aclLists[this.form.vpcid] || []
    },
    aclListForNewVpc () {
      if (!this.form.newvpcid) return []
      return this.aclLists[this.form.newvpcid] || []
    },
    aclListForEdit () {
      if (!this.editingPeering) return []
      return this.aclLists[this.editingPeering.vpcid] || []
    }
  },
  watch: {
    'form.vpcid' (val) {
      if (val) this.fetchAclListForVpc(val)
    },
    'form.newvpcid' (val) {
      if (val) this.fetchAclListForVpc(val)
    }
  },
  created () {
    this.fetchData()
  },
  methods: {
    filterOption (input, option) {
      return option.label && option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
    },
    groupHeader (group) {
      const names = group.members.map(m => m.vpcname).join(', ')
      return `${names} (${group.members.length} VPCs)`
    },
    fetchData () {
      this.loading = true
      getAPI('listVpcPeerings', {}).then(json => {
        const peerings = json.listvpcpeeringsresponse?.vpcpeering || []
        const groups = {}
        for (const p of peerings) {
          if (!groups[p.groupuuid]) {
            groups[p.groupuuid] = {
              groupuuid: p.groupuuid,
              members: []
            }
          }
          groups[p.groupuuid].members.push(p)
        }
        this.peeringGroups = Object.values(groups)
        if (this.activeGroups.length === 0 && this.peeringGroups.length > 0) {
          this.activeGroups = this.peeringGroups.map(g => g.groupuuid)
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    },
    fetchAllVpcs () {
      getAPI('listVPCs', { listAll: true }).then(json => {
        this.allVpcs = json.listvpcsresponse?.vpc || []
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    fetchAclListForVpc (vpcid) {
      getAPI('listNetworkACLLists', { vpcid: vpcid, listAll: true }).then(json => {
        this.aclLists[vpcid] = json.listnetworkacllistsresponse?.networkacllist || []
      }).catch(() => {})
    },
    openCreateModal () {
      this.form.vpcid = undefined
      this.form.peervpcid = undefined
      this.form.aclid = undefined
      this.modals.create = true
      this.fetchAllVpcs()
    },
    openAddVpcToGroupModal (group) {
      this.selectedGroup = group
      this.form.newvpcid = undefined
      this.form.aclid = undefined
      this.modals.addToGroup = true
      this.fetchAllVpcs()
    },
    openEditAclModal (record) {
      this.editingPeering = record
      this.form.aclid = record.aclid || undefined
      this.fetchAclListForVpc(record.vpcid)
      this.modals.editAcl = true
    },
    handleCreatePeering () {
      if (this.modals.createLoading) return
      this.modals.createLoading = true
      const params = {
        vpcid: this.form.vpcid,
        peervpcid: this.form.peervpcid
      }
      if (this.form.aclid) {
        params.aclid = this.form.aclid
      }
      postAPI('createVpcPeering', params).then(() => {
        this.$message.success(this.$t('message.success.add.vpc.peering'))
        this.modals.create = false
        this.fetchData()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.modals.createLoading = false
      })
    },
    handleAddVpcToGroup (group) {
      if (this.modals.addToGroupLoading) return
      this.modals.addToGroupLoading = true
      const existingMember = this.selectedGroup.members[0]
      const params = {
        vpcid: this.form.newvpcid,
        peervpcid: existingMember.vpcid
      }
      if (this.form.aclid) {
        params.aclid = this.form.aclid
      }
      postAPI('createVpcPeering', params).then(() => {
        this.$message.success(this.$t('message.success.add.vpc.peering'))
        this.modals.addToGroup = false
        this.fetchData()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.modals.addToGroupLoading = false
      })
    },
    handleUpdateAcl () {
      if (this.modals.editAclLoading) return
      this.modals.editAclLoading = true
      const params = { id: this.editingPeering.id }
      if (this.form.aclid) {
        params.aclid = this.form.aclid
      }
      postAPI('updateVpcPeering', params).then(() => {
        this.$message.success(this.$t('message.success.update.vpc.peering'))
        this.modals.editAcl = false
        this.fetchData()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.modals.editAclLoading = false
      })
    },
    handleDeletePeering (record) {
      this.loading = true
      postAPI('deleteVpcPeering', { id: record.id }).then(() => {
        this.$message.success(this.$t('label.action.delete.succeeded'))
        this.fetchData()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    },
    handleDeleteGroup (group) {
      this.loading = true
      const promises = group.members.map(m =>
        postAPI('deleteVpcPeering', { id: m.id })
      )
      Promise.all(promises).then(() => {
        this.$message.success(this.$t('label.action.delete.succeeded'))
        this.fetchData()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
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
