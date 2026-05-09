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
        <a-form-item name="vpcids" ref="vpcids" :label="$t('label.vpc.peering.members')">
          <a-select
            v-model:value="form.vpcids"
            mode="multiple"
            showSearch
            optionFilterProp="label"
            :placeholder="$t('label.vpc.peering.select.vpcs')"
            :filterOption="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())">
            <a-select-option
              v-for="item in availableVpcs"
              :key="item.id"
              :value="item.id"
              :label="`${item.name} (${item.cidr}) - ${item.zonename}`"
              :disabled="peeredVpcIds.has(item.id)">
              {{ item.name }} ({{ item.cidr }}) - {{ item.zonename }}
              <a-tag v-if="peeredVpcIds.has(item.id)" color="orange" style="margin-left: 8px;">
                {{ $t('label.vpc.peering.already.peered') }}
              </a-tag>
            </a-select-option>
          </a-select>
          <div v-if="form.vpcids && form.vpcids.length < 2" style="color: #faad14; margin-top: 4px; font-size: 12px;">
            {{ $t('label.vpc.peering.select.min') }}
          </div>
        </a-form-item>
        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button
            ref="submit"
            type="primary"
            :loading="submitting"
            :disabled="!form.name || !form.vpcids || form.vpcids.length < 2"
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
  name: 'CreateVpcPeering',
  data () {
    return {
      loading: false,
      submitting: false,
      allVpcs: [],
      peeredVpcIds: new Set()
    }
  },
  computed: {
    availableVpcs () {
      return this.allVpcs
    }
  },
  beforeCreate () {
    this.formRef = ref()
    this.form = reactive({
      name: '',
      description: '',
      vpcids: []
    })
    this.rules = reactive({
      name: [{ required: true, message: this.$t('label.required') }],
      vpcids: [{ required: true, type: 'array', min: 2, message: this.$t('label.vpc.peering.select.min') }]
    })
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      this.loading = true
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
      }).finally(() => {
        this.loading = false
      })
    },
    closeAction () {
      this.$emit('close-action')
    },
    async handleSubmit () {
      if (this.submitting) return
      this.formRef.value.validate().then(async () => {
        if (!this.form.vpcids || this.form.vpcids.length < 2) return
        this.submitting = true
        try {
          const vpcids = this.form.vpcids
          // First call seeds the group with the first pair (and the name/description).
          // Subsequent calls add each remaining VPC to the same group via peervpcid =
          // first VPC; OvnElement.createVpcPeering joins them under the existing
          // group_uuid because peervpcid already belongs to it.
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
