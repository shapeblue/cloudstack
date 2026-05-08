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
    <!-- ==================== LIST VIEW ==================== -->
    <div v-if="!detailId">
      <a-card :bordered="true">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
          <a-breadcrumb style="font-size: 14px;">
            <a-breadcrumb-item>
              <router-link to="/"><home-outlined /></router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item>{{ $t('label.vpc.peering') }}</a-breadcrumb-item>
          </a-breadcrumb>
          <div style="display: flex; align-items: center; gap: 8px;">
            <a-input-search
              v-model:value="searchQuery"
              :placeholder="$t('label.search')"
              style="width: 200px;"
              @search="fetchData" />
            <a-button
              @click="fetchData"
              :loading="loading">
              <template #icon><reload-outlined /></template>
            </a-button>
            <a-button
              type="primary"
              @click="openCreateModal"
              :disabled="!('createVpcPeering' in $store.getters.apis)">
              <template #icon><plus-outlined /></template>
              {{ $t('label.add.vpc.peering') }}
            </a-button>
          </div>
        </div>

        <a-table
          size="middle"
          :loading="loading"
          :columns="listColumns"
          :dataSource="filteredGroups"
          :rowKey="item => item.groupuuid"
          :pagination="{ showSizeChanger: true, pageSizeOptions: ['10', '20', '50'] }"
          :customRow="(record) => ({ onClick: () => navigateToDetail(record) })"
          class="peering-list-table">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <swap-outlined style="margin-right: 8px; color: #1890ff;" />
              <router-link :to="'/vpcpeering/' + record.groupuuid">
                {{ record.name || record.groupuuid.substring(0, 8) + '...' }}
              </router-link>
            </template>
            <template v-if="column.key === 'description'">
              {{ record.description || '—' }}
            </template>
            <template v-if="column.key === 'membercount'">
              {{ record.members.length }}
            </template>
            <template v-if="column.key === 'state'">
              <status :text="record.state" displayText />
            </template>
            <template v-if="column.key === 'zonename'">
              {{ record.zonename }}
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <!-- ==================== DETAIL VIEW ==================== -->
    <div v-else>
      <a-spin :spinning="detailLoading">
        <!-- Breadcrumb bar -->
        <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
          <a-breadcrumb style="font-size: 14px;">
            <a-breadcrumb-item>
              <router-link to="/"><home-outlined /></router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item>
              <router-link to="/vpcpeering">{{ $t('label.vpc.peering') }}</router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item>{{ currentGroup.name || detailId.substring(0, 8) + '...' }}</a-breadcrumb-item>
          </a-breadcrumb>
          <div style="display: flex; gap: 8px;">
            <a-button @click="fetchGroupDetail">
              <template #icon><reload-outlined /></template>
            </a-button>
            <a-popconfirm
              :title="$t('message.confirm.delete.vpc.peering.group')"
              @confirm="handleDeleteGroup(currentGroup)"
              :okText="$t('label.yes')"
              :cancelText="$t('label.no')">
              <a-button danger>
                <template #icon><delete-outlined /></template>
                {{ $t('label.delete') }}
              </a-button>
            </a-popconfirm>
          </div>
        </div>

        <!-- Info Card (like InfoCard.vue) -->
        <a-card :bordered="true" style="margin-bottom: 16px;">
          <div style="display: flex; align-items: center;">
            <div style="margin-right: 20px;">
              <a-avatar :size="48" style="background-color: #1890ff;">
                <template #icon><swap-outlined /></template>
              </a-avatar>
            </div>
            <div style="flex: 1;">
              <h4 style="margin: 0 0 4px 0; font-size: 18px;">{{ currentGroup.name }}</h4>
              <div v-if="currentGroup.description" style="color: rgba(0,0,0,0.45); margin-bottom: 4px;">
                {{ currentGroup.description }}
              </div>
              <status :text="currentGroup.state || 'Active'" displayText />
            </div>
          </div>
        </a-card>

        <!-- Tabs: Details / VPC Peers -->
        <a-card :bordered="true" :bodyStyle="{ padding: 0 }">
          <a-tabs v-model:activeKey="activeTab" style="padding: 0 16px;">
            <!-- ===== Details Tab ===== -->
            <a-tab-pane key="details" :tab="$t('label.details')">
              <a-list size="small" :split="true" style="padding: 0 8px 16px 8px;">
                <a-list-item v-for="field in detailFields" :key="field.key">
                  <div style="width: 100%;">
                    <strong>{{ field.label }}</strong>
                    <br/>
                    <div>{{ field.value || '—' }}</div>
                  </div>
                </a-list-item>
              </a-list>
            </a-tab-pane>

            <!-- ===== VPC Peers Tab ===== -->
            <a-tab-pane key="peers" :tab="$t('label.vpc.peers')">
              <div style="padding: 16px 8px;">
                <a-button
                  type="dashed"
                  style="width: 100%; margin-bottom: 16px;"
                  :disabled="!('createVpcPeering' in $store.getters.apis)"
                  @click="openAddVpcModal(currentGroup)">
                  <template #icon><plus-outlined /></template>
                  {{ $t('label.add.vpc.to.peering') }}
                </a-button>

                <a-table
                  size="small"
                  :columns="memberColumns"
                  :dataSource="currentGroup.members"
                  :rowKey="item => item.id"
                  :pagination="false">
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'vpcname'">
                      <router-link :to="'/vpc/' + record.vpcid">{{ record.vpcname }}</router-link>
                    </template>
                    <template v-if="column.key === 'vpccidr'">
                      {{ record.vpccidr }}
                    </template>
                    <template v-if="column.key === 'linklocalip'">
                      <code>{{ record.linklocalip }}</code>
                    </template>
                    <template v-if="column.key === 'zonename'">
                      {{ record.zonename }}
                    </template>
                    <template v-if="column.key === 'state'">
                      <status :text="record.state" displayText />
                    </template>
                    <template v-if="column.key === 'actions'">
                      <a-popconfirm
                        :title="$t('message.confirm.remove.vpc.from.peering')"
                        @confirm="handleRemoveMember(record)"
                        :okText="$t('label.yes')"
                        :cancelText="$t('label.no')">
                        <a-tooltip :title="$t('label.remove')">
                          <a-button
                            type="link"
                            danger
                            size="small"
                            :disabled="currentGroup.members.length <= 2">
                            <template #icon><delete-outlined /></template>
                          </a-button>
                        </a-tooltip>
                      </a-popconfirm>
                    </template>
                  </template>
                </a-table>
              </div>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-spin>
    </div>

    <!-- ==================== Create VPC Peering Modal ==================== -->
    <a-modal
      v-model:visible="modals.create"
      :title="$t('label.add.vpc.peering')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="modals.create = false"
      width="520px">
      <a-spin :spinning="modals.createLoading">
        <a-form layout="vertical" :model="form" :rules="createRules" ref="createFormRef">
          <a-form-item :label="$t('label.name')" name="name">
            <a-input
              v-model:value="form.name"
              v-focus="true"
              :placeholder="$t('label.name')" />
          </a-form-item>
          <a-form-item :label="$t('label.description')">
            <a-input
              v-model:value="form.description"
              :placeholder="$t('label.description')" />
          </a-form-item>
          <a-form-item :label="$t('label.vpc.peering.members')" name="vpcids">
            <a-select
              v-model:value="form.vpcids"
              mode="multiple"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.vpc.peering.select.vpcs')"
              :filterOption="filterOption">
              <a-select-option
                v-for="item in availableVpcs"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`"
                :disabled="isVpcInPeering(item.id)">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
                <a-tag v-if="isVpcInPeering(item.id)" color="orange" style="margin-left: 8px;">{{ $t('label.vpc.peering.already.peered') }}</a-tag>
              </a-select-option>
            </a-select>
            <div v-if="form.vpcids && form.vpcids.length < 2" style="color: #faad14; margin-top: 4px; font-size: 12px;">
              {{ $t('label.vpc.peering.select.min') }}
            </div>
          </a-form-item>
          <div class="action-button">
            <a-button @click="modals.create = false">{{ $t('label.cancel') }}</a-button>
            <a-button
              type="primary"
              @click="handleCreatePeering"
              :disabled="!form.name || !form.vpcids || form.vpcids.length < 2">
              {{ $t('label.ok') }}
            </a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- ==================== Add VPC to Group Modal ==================== -->
    <a-modal
      v-model:visible="modals.addToGroup"
      :title="$t('label.add.vpc.to.peering')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="modals.addToGroup = false"
      width="480px">
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
                v-for="item in vpcsNotInAnyGroup"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="modals.addToGroup = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="handleAddVpcToGroup" :disabled="!form.newvpcid">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>
  </div>
</template>

<script>
import { reactive } from 'vue'
import { getAPI, postAPI } from '@/api'
import Status from '@/components/widgets/Status'

export default {
  name: 'VpcPeeringDashboard',
  components: {
    Status
  },
  data () {
    return {
      loading: false,
      detailLoading: false,
      searchQuery: '',
      activeTab: 'details',
      peeringGroups: [],
      allVpcs: [],
      peeredVpcIds: new Set(),
      currentGroup: { members: [] },
      selectedGroup: null,
      modals: {
        create: false,
        createLoading: false,
        addToGroup: false,
        addToGroupLoading: false
      },
      form: reactive({
        name: '',
        description: '',
        vpcids: [],
        newvpcid: undefined
      }),
      createRules: {
        name: [{ required: true, message: this.$t('label.required') }],
        vpcids: [{ required: true, type: 'array', min: 2, message: this.$t('label.vpc.peering.select.min') }]
      },
      listColumns: [
        {
          key: 'name',
          title: this.$t('label.name'),
          dataIndex: 'name'
        },
        {
          key: 'description',
          title: this.$t('label.description'),
          dataIndex: 'description',
          ellipsis: true
        },
        {
          key: 'membercount',
          title: this.$t('label.vpc.peering.count'),
          dataIndex: 'membercount',
          width: 100,
          align: 'center'
        },
        {
          key: 'state',
          title: this.$t('label.state'),
          dataIndex: 'state',
          width: 100
        },
        {
          key: 'zonename',
          title: this.$t('label.zone'),
          dataIndex: 'zonename',
          width: 160
        }
      ],
      memberColumns: [
        {
          key: 'vpcname',
          title: this.$t('label.vpc'),
          dataIndex: 'vpcname'
        },
        {
          key: 'vpccidr',
          title: this.$t('label.cidr'),
          dataIndex: 'vpccidr',
          width: 160
        },
        {
          key: 'linklocalip',
          title: this.$t('label.link.local.ip'),
          dataIndex: 'linklocalip',
          width: 160
        },
        {
          key: 'zonename',
          title: this.$t('label.zone'),
          dataIndex: 'zonename',
          width: 160
        },
        {
          key: 'state',
          title: this.$t('label.state'),
          dataIndex: 'state',
          width: 100
        },
        {
          key: 'actions',
          title: '',
          dataIndex: 'actions',
          width: 60,
          align: 'center'
        }
      ]
    }
  },
  computed: {
    detailId () {
      return this.$route.params.id || null
    },
    filteredGroups () {
      if (!this.searchQuery) return this.peeringGroups
      const q = this.searchQuery.toLowerCase()
      return this.peeringGroups.filter(g => {
        return (g.name && g.name.toLowerCase().includes(q)) ||
          (g.description && g.description.toLowerCase().includes(q)) ||
          g.members.some(m => m.vpcname && m.vpcname.toLowerCase().includes(q))
      })
    },
    detailFields () {
      const g = this.currentGroup
      return [
        { key: 'name', label: this.$t('label.name'), value: g.name },
        { key: 'id', label: this.$t('label.id'), value: g.groupuuid },
        { key: 'description', label: this.$t('label.description'), value: g.description },
        { key: 'state', label: this.$t('label.state'), value: g.state },
        { key: 'zonename', label: this.$t('label.zone'), value: g.zonename },
        { key: 'membercount', label: this.$t('label.vpc.peering.count'), value: g.members ? String(g.members.length) : '0' },
        { key: 'created', label: this.$t('label.created'), value: g.created }
      ]
    },
    availableVpcs () {
      return this.allVpcs
    },
    vpcsNotInAnyGroup () {
      return this.allVpcs.filter(v => !this.peeredVpcIds.has(v.id))
    }
  },
  watch: {
    '$route.params.id': {
      handler (newId) {
        if (newId) {
          this.fetchGroupDetail()
        } else {
          this.fetchData()
        }
      },
      immediate: false
    }
  },
  created () {
    if (this.detailId) {
      this.fetchGroupDetail()
    } else {
      this.fetchData()
    }
  },
  methods: {
    filterOption (input, option) {
      return option.label && option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
    },
    isVpcInPeering (vpcId) {
      return this.peeredVpcIds.has(vpcId)
    },
    navigateToDetail (record) {
      this.$router.push('/vpcpeering/' + record.groupuuid)
    },
    buildGroupMap (peerings) {
      const groups = {}
      const peeredIds = new Set()
      for (const p of peerings) {
        peeredIds.add(p.vpcid)
        if (!groups[p.groupuuid]) {
          groups[p.groupuuid] = {
            groupuuid: p.groupuuid,
            name: p.name,
            description: p.description,
            state: p.state,
            zonename: p.zonename,
            created: p.created,
            members: []
          }
        }
        groups[p.groupuuid].members.push(p)
        if (p.name && !groups[p.groupuuid].name) {
          groups[p.groupuuid].name = p.name
        }
        if (p.description && !groups[p.groupuuid].description) {
          groups[p.groupuuid].description = p.description
        }
      }
      return { groups, peeredIds }
    },
    fetchData () {
      this.loading = true
      getAPI('listVpcPeerings', {}).then(json => {
        const peerings = json.listvpcpeeringsresponse?.vpcpeering || []
        const { groups, peeredIds } = this.buildGroupMap(peerings)
        this.peeringGroups = Object.values(groups)
        this.peeredVpcIds = peeredIds
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    },
    fetchGroupDetail () {
      if (!this.detailId) return
      this.detailLoading = true
      getAPI('listVpcPeerings', { groupuuid: this.detailId }).then(json => {
        const peerings = json.listvpcpeeringsresponse?.vpcpeering || []
        if (peerings.length === 0) {
          this.$message.warning(this.$t('label.not.found'))
          this.$router.push('/vpcpeering')
          return
        }
        const { groups, peeredIds } = this.buildGroupMap(peerings)
        this.currentGroup = Object.values(groups)[0]
        this.peeredVpcIds = peeredIds
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.detailLoading = false
      })
    },
    fetchAllVpcs () {
      getAPI('listVPCs', { listAll: true }).then(json => {
        this.allVpcs = json.listvpcsresponse?.vpc || []
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    openCreateModal () {
      this.form.name = ''
      this.form.description = ''
      this.form.vpcids = []
      this.modals.create = true
      this.fetchAllVpcs()
      this.fetchData()
    },
    openAddVpcModal (group) {
      this.selectedGroup = group
      this.form.newvpcid = undefined
      this.modals.addToGroup = true
      this.fetchAllVpcs()
      this.fetchData()
    },
    async handleCreatePeering () {
      if (this.modals.createLoading) return
      if (!this.form.vpcids || this.form.vpcids.length < 2) return
      this.modals.createLoading = true
      try {
        const vpcids = this.form.vpcids
        const params = {
          name: this.form.name,
          vpcid: vpcids[0],
          peervpcid: vpcids[1]
        }
        if (this.form.description) {
          params.description = this.form.description
        }
        await postAPI('createVpcPeering', params)
        for (let i = 2; i < vpcids.length; i++) {
          await postAPI('createVpcPeering', {
            name: this.form.name,
            vpcid: vpcids[i],
            peervpcid: vpcids[0]
          })
        }
        this.$message.success(this.$t('message.success.add.vpc.peering'))
        this.modals.create = false
        this.fetchData()
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.modals.createLoading = false
      }
    },
    async handleAddVpcToGroup () {
      if (this.modals.addToGroupLoading || !this.form.newvpcid) return
      this.modals.addToGroupLoading = true
      try {
        const existingMember = this.selectedGroup.members[0]
        await postAPI('createVpcPeering', {
          name: this.selectedGroup.name,
          vpcid: this.form.newvpcid,
          peervpcid: existingMember.vpcid
        })
        this.$message.success(this.$t('message.success.add.vpc.peering'))
        this.modals.addToGroup = false
        if (this.detailId) {
          this.fetchGroupDetail()
        } else {
          this.fetchData()
        }
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.modals.addToGroupLoading = false
      }
    },
    async handleRemoveMember (record) {
      this.detailLoading = true
      try {
        await postAPI('deleteVpcPeering', { id: record.id })
        this.$message.success(this.$t('message.success.remove.vpc.from.peering'))
        this.fetchGroupDetail()
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.detailLoading = false
      }
    },
    async handleDeleteGroup (group) {
      this.loading = true
      this.detailLoading = true
      try {
        for (const m of group.members) {
          await postAPI('deleteVpcPeering', { id: m.id })
        }
        this.$message.success(this.$t('label.action.delete.succeeded'))
        this.$router.push('/vpcpeering')
        this.fetchData()
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.loading = false
        this.detailLoading = false
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
.peering-list-table :deep(tr) {
  cursor: pointer;
}
</style>
