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
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @submit.prevent="handleSubmit">
        <a-form-item name="name" ref="name" :label="$t('label.name')">
          <a-input
            v-model:value="form.name"
            :placeholder="$t('label.name')"
            v-focus="true" />
        </a-form-item>
        <a-form-item name="description" ref="description" :label="$t('label.description')">
          <a-input
            v-model:value="form.description"
            :placeholder="$t('label.description')" />
        </a-form-item>
        <a-form-item name="memberkeys" ref="memberkeys" :label="$t('label.mesh.network.members')">
          <a-select
            v-model:value="form.memberKeys"
            mode="multiple"
            showSearch
            optionFilterProp="label"
            :placeholder="$t('label.mesh.network.select.members')"
            :filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())">
            <a-select-option
              v-for="item in availableMembers"
              :key="item.key"
              :value="item.key"
              :label="`[${item.kindLabel}] ${item.name} (${item.cidr}) - ${item.zonename}`"
              :disabled="usedKeys.has(item.key)">
              <a-tag :color="item.kind === 'vpc' ? 'blue' : 'green'" style="margin-right: 6px;">
                {{ item.kindLabel }}
              </a-tag>
              {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              <a-tag v-if="usedKeys.has(item.key)" color="orange" style="margin-left: 8px;">
                {{ $t('label.member.already.in.mesh.network') }}
              </a-tag>
            </a-select-option>
          </a-select>
          <div v-if="form.memberKeys && form.memberKeys.length < 2" style="color: #faad14; margin-top: 4px; font-size: 12px;">
            {{ $t('label.mesh.network.select.min') }}
          </div>
        </a-form-item>
        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button
            ref="submit"
            type="primary"
            :loading="submitting"
            :disabled="!form.name || !form.memberKeys || form.memberKeys.length < 2"
            @click="handleSubmit">
            {{ $t('label.ok') }}
          </a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { getAPI, postAPI } from '@/api'

export default {
  name: 'CreateMeshNetwork',
  data () {
    return {
      loading: false,
      submitting: false,
      allVpcs: [],
      allIsolatedNetworks: [],
      usedKeys: new Set()
    }
  },
  computed: {
    /**
     * Returns the picker rows for every mesh-eligible resource the caller can
     * see, mixing VPCs and Isolated networks behind a single shape. Each
     * entry's {@code key} encodes the kind+id so the submit step can derive
     * the right API param (vpcid or networkid) without a second lookup.
     */
    availableMembers () {
      const items = []
      for (const v of this.allVpcs) {
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
  beforeCreate () {
    this.formRef = ref()
    this.form = reactive({
      name: '',
      description: '',
      memberKeys: []
    })
    this.rules = reactive({
      name: [{ required: true, message: this.$t('label.required') }],
      memberKeys: [{ required: true, type: 'array', min: 2, message: this.$t('label.mesh.network.select.min') }]
    })
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      this.loading = true
      // Pull VPCs + Isolated networks + existing mesh-network memberships in parallel.
      // Isolated networks are filtered to those that are OVN-backed, in the
      // Implemented state, and not VPC tiers — the same eligibility rules the
      // backend enforces on createMeshNetwork. Filtering here just keeps the
      // picker from showing entries that would be rejected on submit.
      Promise.all([
        getAPI('listVPCs', { listAll: true }),
        getAPI('listNetworks', { listAll: true, type: 'Isolated', state: 'Implemented' }),
        getAPI('listMeshNetworks')
      ]).then(([vpcResp, netResp, meshResp]) => {
        this.allVpcs = vpcResp.listvpcsresponse?.vpc || []
        const networks = netResp.listnetworksresponse?.network || []
        this.allIsolatedNetworks = networks.filter(n => {
          if (n.vpcid) return false // tier, not standalone
          if (n.broadcastdomaintype !== 'OVN') return false
          // listNetworks ignores the state param, so re-check here. The
          // backend rejects non-Implemented networks (no LR provisioned
          // yet), so showing them would just produce a confusing error
          // at submit time.
          if (n.state !== 'Implemented') return false
          return true
        })
        const groups = meshResp.listmeshnetworksresponse?.meshnetwork || []
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
      }).finally(() => {
        this.loading = false
      })
    },
    closeAction () {
      this.$emit('close-action')
    },
    /**
     * Splits a picker key (kind:uuid) into the right createMeshNetwork
     * parameters. The backend accepts vpcid OR networkid (and the same on
     * the peer side); this helper produces one half of that pair.
     */
    paramsForKey (key, suffix) {
      const [kind, id] = key.split(':')
      return kind === 'vpc'
        ? { [suffix ? 'peer' + suffix : 'vpcid']: id }
        : { [suffix ? 'peernetworkid' : 'networkid']: id }
    },
    /**
     * Builds the first member half of the create payload. The first member
     * goes in vpcid|networkid; the peer (in the call we use) goes in
     * peervpcid|peernetworkid.
     */
    firstParam (key) {
      const [kind, id] = key.split(':')
      return kind === 'vpc' ? { vpcid: id } : { networkid: id }
    },
    peerParam (key) {
      const [kind, id] = key.split(':')
      return kind === 'vpc' ? { peervpcid: id } : { peernetworkid: id }
    },
    async handleSubmit () {
      if (this.submitting) return
      this.formRef.value.validate().then(async () => {
        if (!this.form.memberKeys || this.form.memberKeys.length < 2) return
        this.submitting = true
        try {
          const keys = this.form.memberKeys
          // First call seeds the mesh with the first pair (and the name/description).
          // Subsequent calls add each remaining member to the same mesh via
          // peer = keys[0]; OvnElement.createMeshNetwork joins them under the
          // existing mesh_uuid because the peer already belongs to it.
          const base = {
            name: this.form.name,
            ...this.firstParam(keys[0]),
            ...this.peerParam(keys[1])
          }
          if (this.form.description) {
            base.description = this.form.description
          }
          await postAPI('createMeshNetwork', base)
          for (let i = 2; i < keys.length; i++) {
            await postAPI('createMeshNetwork', {
              name: this.form.name,
              ...this.firstParam(keys[i]),
              ...this.peerParam(keys[0])
            })
          }
          this.$message.success(this.$t('message.success.add.mesh.network'))
          this.$emit('refresh-data')
          this.closeAction()
        } catch (error) {
          this.$notifyError(error)
        } finally {
          this.submitting = false
        }
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.form-layout {
  width: 80vw;
  max-width: 600px;
}
.action-button {
  text-align: right;
  margin-top: 16px;
}
.action-button button {
  margin-left: 8px;
}
</style>
