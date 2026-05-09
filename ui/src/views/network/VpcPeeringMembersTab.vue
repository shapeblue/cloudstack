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
      :disabled="!('createVpcPeering' in $store.getters.apis)"
      @click="openAddModal">
      <template #icon><plus-outlined /></template>
      {{ $t('label.add.vpc.to.peering') }}
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
          <router-link :to="{ path: '/vpc/' + record.vpcid }">{{ record.vpcname }}</router-link>
        </template>
        <template v-if="column.key === 'state'">
          <status :text="record.state" displayText />
        </template>
        <template v-if="column.key === 'actions'">
          <a-popconfirm
            :title="$t('message.confirm.remove.vpc.from.peering')"
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
      :title="$t('label.add.vpc.to.peering')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="showAddModal = false"
      width="480px">
      <a-spin :spinning="adding">
        <a-form layout="vertical">
          <a-form-item :label="$t('label.vpc')">
            <a-select
              v-model:value="newVpcId"
              showSearch
              optionFilterProp="label"
              :placeholder="$t('label.select')"
              :filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())">
              <a-select-option
                v-for="item in vpcsAvailable"
                :key="item.id"
                :value="item.id"
                :label="`${item.name} (${item.cidr}) - ${item.zonename}`">
                {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div class="action-button">
            <a-button @click="showAddModal = false">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" @click="addMember" :disabled="!newVpcId">{{ $t('label.ok') }}</a-button>
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
  name: 'VpcPeeringMembersTab',
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
      peeredVpcIds: new Set(),
      showAddModal: false,
      newVpcId: undefined,
      adding: false
    }
  },
  computed: {
    members () {
      return this.resource?.members || []
    },
    columns () {
      return [
        { key: 'vpcname', title: this.$t('label.vpc'), dataIndex: 'vpcname' },
        { key: 'vpccidr', title: this.$t('label.cidr'), dataIndex: 'vpccidr', width: 160 },
        { key: 'linklocalip', title: this.$t('label.link.local.ip'), dataIndex: 'linklocalip', width: 160 },
        { key: 'zonename', title: this.$t('label.zone'), dataIndex: 'zonename', width: 160 },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state', width: 100 },
        { key: 'actions', title: '', dataIndex: 'actions', width: 60, align: 'center' }
      ]
    },
    vpcsAvailable () {
      return this.allVpcs.filter(v => !this.peeredVpcIds.has(v.id))
    }
  },
  methods: {
    openAddModal () {
      this.newVpcId = undefined
      this.showAddModal = true
      this.fetchAllVpcs()
    },
    fetchAllVpcs () {
      Promise.all([
        getAPI('listVPCs', { listAll: true }),
        getAPI('listVpcPeerings')
      ]).then(([vpcResp, peerResp]) => {
        this.allVpcs = vpcResp.listvpcsresponse?.vpc || []
        const groups = peerResp.listvpcpeeringsresponse?.vpcpeering || []
        const used = new Set()
        for (const g of groups) {
          for (const m of (g.members || [])) {
            if (m.vpcid) used.add(m.vpcid)
          }
        }
        this.peeredVpcIds = used
      }).catch(error => {
        this.$notifyError(error)
      })
    },
    async addMember () {
      if (!this.newVpcId || this.members.length === 0) return
      this.adding = true
      try {
        const existing = this.members[0]
        await postAPI('createVpcPeering', {
          name: this.resource.name,
          vpcid: this.newVpcId,
          peervpcid: existing.vpcid
        })
        this.$message.success(this.$t('message.success.add.vpc.peering'))
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
        await postAPI('deleteVpcPeering', { id: record.id })
        this.$message.success(this.$t('message.success.remove.vpc.from.peering'))
        this.$emit('refresh-data')
      } catch (error) {
        this.$notifyError(error)
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
