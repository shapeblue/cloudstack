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
  <a-tag
    v-if="!isTag"
    closable
    @close="() => $emit('close', searchFilter.key)"
  >
    {{ retrieveFieldLabel(searchFilter.key) }}: {{ searchFilter.value }}
  </a-tag>
  <a-tag
    v-else
    closable
    @close="() => $emit('close', searchFilter.key)"
  >
    {{ $t('label.tag') }}: {{ searchFilter.key }}={{ searchFilter.value }}
  </a-tag>
</template>

<script>
import { api } from '@/api'

export default {
  name: 'SearchFilter',
  props: {
    apiName: {
      type: String,
      default: () => ''
    },
    filterKey: {
      type: String,
      default: () => ''
    },
    filterValue: {
      type: String,
      default: () => ''
    },
    isTag: {
      type: Boolean,
      default: () => false
    }
  },
  emits: ['close'],
  data () {
    return {
      alertTypes: {},
      searchFilter: {
        key: this.filterKey,
        value: this.filterKey
      }
    }
  },
  created () {
    this.getSearchFilters()
  },
  methods: {
    retrieveFieldLabel (fieldName) {
      if (fieldName === 'groupid') {
        fieldName = 'group'
      }
      if (fieldName === 'keyword') {
        if ('listAnnotations' in this.$store.getters.apis) {
          return this.$t('label.annotation')
        } else {
          return this.$t('label.name')
        }
      }
      return this.$t('label.' + fieldName)
    },
    getSearchFilters () {
      if (this.filterKey === 'domainid' && !('listDomains' in this.$store.getters.apis)) {
        return true
      }
      if (this.filterKey === 'account' && !('listAccounts' in this.$store.getters.apis)) {
        return true
      }
      if (this.filterKey === 'account' && !('addAccountToProject' in this.$store.getters.apis || 'createAccount' in this.$store.getters.apis)) {
        return true
      }
      if (this.filterKey === 'podid' && !('listPods' in this.$store.getters.apis)) {
        return true
      }
      if (this.filterKey === 'clusterid' && !('listClusters' in this.$store.getters.apis)) {
        return true
      }
      if (this.filterKey === 'groupid' && !('listInstanceGroups' in this.$store.getters.apis)) {
        return true
      }
      this.searchFilter = {
        key: this.filterKey,
        value: this.filterValue
      }
      let value = this.getStaticFieldValue(this.filterKey, this.filterValue)
      if (value !== '') {
        this.searchFilter = {
          key: this.filterKey,
          value: value
        }
      } else {
        value = this.getDynamicFieldValue(this.filterKey, this.filterValue)
        value.then((result) => {
          if (result) {
            this.searchFilter = {
              key: this.filterKey,
              value: result
            }
          } else {
            this.searchFilter = {
              key: this.filterKey,
              value: this.filterValue
            }
          }
        })
      }
    },
    getStaticFieldValue (key, value) {
      let formattedValue = ''
      if (key.includes('type')) {
        if (this.$route.path === '/guestnetwork' || this.$route.path.includes('/guestnetwork/')) {
          formattedValue = this.getGuestNetworkType(value)
        } else if (this.$route.path === '/role' || this.$route.path.includes('/role/')) {
          formattedValue = this.getRoleType(value)
        }
      }

      if (key.includes('scope')) {
        formattedValue = this.getScope(value)
      }

      if (key.includes('state')) {
        formattedValue = this.getState(value)
      }

      if (key.includes('level')) {
        formattedValue = this.getLevel(value)
      }

      if (key.includes('entitytype')) {
        formattedValue = this.getEntityType(value)
      }

      if (key.includes('accounttype')) {
        formattedValue = this.getAccountType(value)
      }

      if (key.includes('systemvmtype')) {
        formattedValue = this.getSystemVmType(value)
      }

      if (key.includes('scope')) {
        formattedValue = this.getStoragePoolScope(value)
      }

      if (key.includes('provider')) {
        formattedValue = this.getImageStoreProvider(value)
      }

      if (key.includes('resourcetype')) {
        formattedValue = value
      }

      this.searchFilter = {
        key: this.filterKey,
        value: formattedValue
      }
      return formattedValue
    },
    async getDynamicFieldValue (key, value) {
      let formattedValue = ''

      if (key.includes('type')) {
        if (this.$route.path === '/alert') {
          formattedValue = await this.getAlertType(value)
        } else if (this.$route.path === '/affinitygroup') {
          formattedValue = await this.getAffinityGroupType(value)
        }
      }

      if (key.includes('zoneid')) {
        formattedValue = await this.getZone(value)
      }

      if (key.includes('domainid')) {
        formattedValue = await this.getDomain(value)
      }

      if (key.includes('account')) {
        formattedValue = await this.getAccount(value)
      }

      if (key.includes('hypervisor')) {
        formattedValue = await this.getHypervisor(value)
      }

      if (key.includes('imagestoreid')) {
        formattedValue = await this.getImageStore(value)
      }

      if (key.includes('storageid')) {
        formattedValue = await this.getStoragePool(value)
      }

      if (key.includes('podid')) {
        formattedValue = await this.getPod(value)
      }

      if (key.includes('clusterid')) {
        formattedValue = await this.getCluster(value)
      }

      if (key.includes('groupid')) {
        formattedValue = await this.getInstanceGroup(value)
      }

      if (key.includes('managementserverid')) {
        formattedValue = await this.getManagementServer(value)
      }

      if (key.includes('serviceofferingid')) {
        formattedValue = await this.getServiceOffering(value)
      }

      if (key.includes('diskofferingid')) {
        formattedValue = await this.getDiskOffering(value)
      }

      return formattedValue
    },
    getZone (zoneId) {
      return new Promise((resolve) => {
        api('listZones', { showicon: true, id: zoneId }).then(json => {
          if (json?.listzonesresponse?.zone) {
            resolve(json.listzonesresponse.zone[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getDomain (domainId) {
      return new Promise((resolve) => {
        api('listDomains', { listAll: true, showicon: true, id: domainId }).then(json => {
          if (json?.listdomainsresponse?.domain) {
            resolve(json.listdomainsresponse.domain[0].path)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getAccount (accountId) {
      return new Promise((resolve) => {
        if (!this.$isValidUuid(accountId)) {
          resolve(accountId)
        }
        const params = { listAll: true, isrecursive: false, showicon: true, id: accountId }
        api('listAccounts', params).then(json => {
          if (json?.listaccountsresponse?.account) {
            resolve(json.listaccountsresponse.account[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getHypervisor (value) {
      return new Promise((resolve) => {
        api('listHypervisors').then(json => {
          if (json?.listhypervisorsresponse?.hypervisor) {
            for (const key in json.listhypervisorsresponse.hypervisor) {
              const hypervisor = json.listhypervisorsresponse.hypervisor[key]
              if (hypervisor.name === value) {
                resolve(hypervisor.name)
              }
            }
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getImageStore (storeId) {
      return new Promise((resolve) => {
        api('listImageStores', { listAll: true, showicon: true, id: storeId }).then(json => {
          if (json?.listimagestoresresponse?.imagestore) {
            resolve(json.listimagestoresresponse.imagestore[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getStoragePool (poolId) {
      return new Promise((resolve) => {
        api('listStoragePools', { listAll: true, showicon: true, id: poolId }).then(json => {
          if (json?.liststoragepoolsresponse?.storagepool) {
            resolve(json.liststoragepoolsresponse.storagepool[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getPod (podId) {
      return new Promise((resolve) => {
        api('listPods', { id: podId }).then(json => {
          if (json?.listpodsresponse?.pod) {
            resolve(json.listpodsresponse.pod[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getCluster (clusterId) {
      return new Promise((resolve) => {
        api('listClusters', { id: clusterId }).then(json => {
          if (json?.listclustersresponse?.cluster) {
            resolve(json.listclustersresponse.cluster[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getInstanceGroup (groupId) {
      return new Promise((resolve) => {
        api('listInstanceGroups', { listAll: true, id: groupId }).then(json => {
          if (json?.listinstancegroupsresponse?.instancegroup) {
            resolve(json.listinstancegroupsresponse.instancegroup[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getServiceOffering (offeringId) {
      return new Promise((resolve) => {
        api('listServiceOfferings', { listAll: true, id: offeringId }).then(json => {
          if (json?.listserviceofferingsresponse?.serviceoffering) {
            resolve(json.listserviceofferingsresponse.serviceoffering[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getDiskOffering (offeringId) {
      return new Promise((resolve) => {
        api('listDiskOfferings', { listAll: true, id: offeringId }).then(json => {
          if (json?.listdiskofferingsresponse?.diskoffering) {
            resolve(json.listdiskofferingsresponse.diskoffering[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getAlertType (type) {
      if (this.alertTypes.length > 0) {
        return new Promise((resolve) => {
          resolve(this.alertTypes[type])
        })
      } else {
        return new Promise((resolve) => {
          api('listAlertTypes').then(json => {
            const alertTypes = {}
            for (const key in json.listalerttypesresponse.alerttype) {
              const alerttype = json.listalerttypesresponse.alerttype[key]
              alertTypes[alerttype.id] = alerttype.name
            }
            this.alertTypes = alertTypes
            resolve(alertTypes[type])
          }).catch(() => {
            resolve(null)
          })
        })
      }
    },
    getAffinityGroupType (type) {
      if (this.alertTypes.length > 0) {
        return new Promise((resolve) => {
          resolve(this.alertTypes[type])
        })
      } else {
        return new Promise((resolve) => {
          api('listAffinityGroupTypes').then(json => {
            const alertTypes = {}
            for (const key in json.listaffinitygrouptypesresponse.affinityGroupType) {
              const affinityGroupType = json.listaffinitygrouptypesresponse.affinityGroupType[key]
              if (affinityGroupType.type === 'host anti-affinity') {
                alertTypes[affinityGroupType.type] = 'host anti-affinity (Strict)'
              } else if (affinityGroupType.type === 'host affinity') {
                alertTypes[affinityGroupType.type] = 'host affinity (Strict)'
              } else if (affinityGroupType.type === 'non-strict host anti-affinity') {
                alertTypes[affinityGroupType.type] = 'host anti-affinity (Non-Strict)'
              } else if (affinityGroupType.type === 'non-strict host affinity') {
                alertTypes[affinityGroupType.type] = 'host affinity (Non-Strict)'
              }
            }
            this.alertTypes = alertTypes
            resolve(alertTypes[type])
          }).catch(() => {
            resolve(null)
          })
        })
      }
    },
    getManagementServer (msId) {
      return new Promise((resolve) => {
        api('listManagementServers', { listAll: true, id: msId }).then(json => {
          if (json?.listmanagementserversresponse?.managementserver) {
            resolve(json.listmanagementserversresponse.managementserver[0].name)
          }
        }).catch(() => {
          resolve(null)
        })
      })
    },
    getGuestNetworkType (value) {
      switch (value.toLowerCase()) {
        case 'isolated':
          return this.$t('label.isolated')
        case 'shared':
          return this.$t('label.shared')
        case 'l2':
          return this.$t('label.l2')
      }
    },
    getAccountType (type) {
      const types = []
      if (this.apiName.indexOf('listAccounts') > -1) {
        switch (type) {
          case '1':
            return 'Admin'
          case '2':
            return 'DomainAdmin'
          case '0':
            return 'User'
        }
      }
      return types
    },
    getSystemVmType (type) {
      if (this.apiName.indexOf('listSystemVms') > -1) {
        switch (type.toLowerCase()) {
          case 'consoleproxy':
            return this.$t('label.console.proxy.vm')
          case 'secondarystoragevm':
            return this.$t('label.secondary.storage.vm')
        }
      }
    },
    getStoragePoolScope (scope) {
      if (this.apiName.indexOf('listStoragePools') > -1) {
        switch (scope.toUpperCase()) {
          case 'CLUSTER':
            return this.$t('label.cluster')
          case 'ZONE':
            return this.$t('label.zone')
          case 'REGION':
            return this.$t('label.region')
          case 'GLOBAL':
            return this.$t('label.global')
        }
      }
    },
    getImageStoreProvider (provider) {
      if (this.apiName.indexOf('listImageStores') > -1) {
        switch (provider.toUpperCase()) {
          case 'NFS':
            return 'NFS'
          case 'SMB':
            return 'SMB/CIFS'
          case 'S3':
            return 'S3'
          case 'SWIFT':
            return 'Swift'
        }
      }
    },
    getRoleType (role) {
      switch (role.toLowerCase()) {
        case 'Admin'.toLowerCase():
          return 'Admin'
        case 'ResourceAdmin'.toLowerCase():
          return 'ResourceAdmin'
        case 'DomainAdmin'.toLowerCase():
          return 'DomainAdmin'
        case 'User'.toLowerCase():
          return 'User'
      }
    },
    getScope (scope) {
      switch (scope.toLowerCase()) {
        case 'local':
          return this.$t('label.local')
        case 'domain':
          return this.$t('label.domain')
        case 'global':
          return this.$t('label.global')
      }
    },
    getState (state) {
      if (this.apiName.includes('listVolumes')) {
        switch (state.toLowerCase()) {
          case 'allocated':
            return this.$t('label.allocated')
          case 'ready':
            return this.$t('label.isready')
          case 'destroy':
            return this.$t('label.destroy')
          case 'expunging':
            return this.$t('label.expunging')
          case 'expunged':
            return this.$t('label.expunged')
          case 'migrating':
            return this.$t('label.migrating')
        }
      } else if (this.apiName.includes('listKubernetesClusters')) {
        switch (state.toLowerCase()) {
          case 'created':
            return this.$t('label.created')
          case 'starting':
            return this.$t('label.starting')
          case 'running':
            return this.$t('label.running')
          case 'stopping':
            return this.$t('label.stopping')
          case 'stopped':
            return this.$t('label.stopped')
          case 'scaling':
            return this.$t('label.scaling')
          case 'upgrading':
            return this.$t('label.upgrading')
          case 'alert':
            return this.$t('label.alert')
          case 'recovering':
            return this.$t('label.recovering')
          case 'destroyed':
            return this.$t('label.destroyed')
          case 'destroying':
            return this.$t('label.destroying')
          case 'error':
            return this.$t('label.error')
        }
      } else if (this.apiName.indexOf('listWebhooks') > -1) {
        switch (state.toLowerCase()) {
          case 'enabled':
            return this.$t('label.enabled')
          case 'disabled':
            return this.$t('label.disabled')
        }
      }
    },
    getEntityType (type) {
      let entityType = ''
      if (this.apiName.indexOf('listAnnotations') > -1) {
        const allowedTypes = {
          VM: 'Virtual Machine',
          HOST: 'Host',
          VOLUME: 'Volume',
          SNAPSHOT: 'Snapshot',
          VM_SNAPSHOT: 'VM Snapshot',
          INSTANCE_GROUP: 'Instance Group',
          NETWORK: 'Network',
          VPC: 'VPC',
          PUBLIC_IP_ADDRESS: 'Public IP Address',
          VPN_CUSTOMER_GATEWAY: 'VPC Customer Gateway',
          TEMPLATE: 'Template',
          ISO: 'ISO',
          SSH_KEYPAIR: 'SSH Key Pair',
          DOMAIN: 'Domain',
          SERVICE_OFFERING: 'Service Offfering',
          DISK_OFFERING: 'Disk Offering',
          NETWORK_OFFERING: 'Network Offering',
          POD: 'Pod',
          ZONE: 'Zone',
          CLUSTER: 'Cluster',
          PRIMARY_STORAGE: 'Primary Storage',
          SECONDARY_STORAGE: 'Secondary Storage',
          VR: 'Virtual Router',
          SYSTEM_VM: 'System VM',
          KUBERNETES_CLUSTER: 'Kubernetes Cluster'
        }
        entityType = allowedTypes[type.toUpperCase()]
      }
      return entityType
    },
    getLevel (level) {
      switch (level.toUpperCase()) {
        case 'INFO':
          return this.$t('label.info.upper')

        case 'WARN':
          return this.$t('label.warn.upper')

        case 'ERROR':
          return this.$t('label.error.upper')
      }
    }
  }
}
</script>
