<template>
  <line x1="100" y1="100" x2="500" y2="100" />
  <a-button @click="chart.fit()" class="btn btn-action-button waves-effect waves-light">
    <i class="fas fa-sync"></i>  fit
  </a-button>
  <br />
  <a-button
    @click='chart.layout(["right","bottom","left","top"][index++ % 4]).render().fit()'
    class="btn btn-action-button waves-effect waves-light">
    <i class="fas fa-retweet"></i>  swap
  </a-button>
  <br />
  <br />
  <br />
  <div class="chart-container" style="height: 1200px; background-color: #fffeff"></div>
  <div>
    <network-details :show="this.cardShow" :node="this.jsonData.filter((d) => d.id === this.nodeId)" />
  </div>
  <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css" rel="stylesheet" />
  <div class="node-modal"></div>
</template>
<script>
import * as d3 from 'd3'
import { OrgChart } from 'd3-org-chart'
import router from '../../assets/icons/router-xxl.png'
import network from '../../assets/icons/network.png'
import vm from '../../assets/icons/vm.png'
import internet from '../../assets/icons/internet.png'
import vpcImage from '../../assets/icons/vpc.png'
import { api } from '@/api'
import NetworkDetails from '@views/network/NetworkDetails'

export default {
  name: 'AccountNetworkOverview',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  components: {
    NetworkDetails
  },
  async created () {
    this.renderGraph()
  },
  provide: function () {
    return {
      updateShowCard: this.toggleDetailsCard
    }
  },
  data () {
    return {
      index: 0,
      chart: {},
      colors: {
        Running: '#b0fa8e',
        Stopped: '#ffa8a8'
      },
      jsonData: [],
      publicNetworks: [],
      networks: [],
      routers: [],
      vms: [],
      nics: [],
      vpcs: [],
      count: 0,
      cardShow: false,
      currentCount: 0,
      initCount: 1,
      root: {},
      parentId: '',
      zoneId: this.resource.zoneId,
      nodeId: '',
      connections: [],
      columns: ['name', 'imageUrl', 'id', 'role', 'hostid', 'hostname', 'guestipaddress',
        'guestmacaddress', 'guestnetmask', 'guestnetworkname', 'linklocalip', 'state', 'templateid',
        'templatename', 'parentId', 'ostypeid', 'ostypename', 'osdisplayname', 'broadcastdomaintype',
        'broadcasturi', 'cidr', 'gateway', 'dns1', 'dns2', 'networkofferingname', 'networkofferingid', 'domain', 'account']
    }
  },
  methods: {
    toggleDetailsCard (d) {
      this.cardShow = !this.cardShow
      this.nodeId = d
    },
    getPublicNetworks () {
      return new Promise((resolve, reject) => {
        api('listNetworks', {
          zoneid: this.zoneId,
          issystem: true,
          traffictype: 'Public'
        }).then(response => {
          this.publicNetworks = response?.listnetworksresponse?.network || []
          this.publicNetworks[0].role = 'Public Network'
          this.publicNetworks[0].imageUrl = internet
          this.publicNetworks[0]._upToTheRootHighlighted = true
          this.jsonData.push(this.publicNetworks[0])
          this.root = this.publicNetworks[0]
          resolve(this.publicNetworks)
        })
      })
    },
    getNetworks (id) {
      if (!id) {
        return new Promise((resolve, reject) => {
          api('listNetworks', {
            account: this.resource.name,
            zoneid: this.resource.datacenterid,
            forvpc: false,
            listall: true
          }).then(async response => {
            const networks = this.networks.concat(response?.listnetworksresponse?.network || [])
            resolve(networks)
          })
        })
      } else {
        return new Promise((resolve, reject) => {
          api('listNetworks', {
            account: this.resource.name,
            zoneid: this.resource.datacenterid,
            vpcid: id,
            listall: true
          }).then(async response => {
            const networks = this.networks.concat(response?.listnetworksresponse?.network || [])
            resolve(networks)
          })
        })
      }
    },
    getVpcs () {
      return new Promise((resolve, reject) => {
        api('listVPCs', {
          account: this.resource.name,
          zoneid: this.resource.datacenterid,
          listall: true
        }).then(response => {
          const vpcs = this.vpcs.concat(response?.listvpcsresponse?.vpc || [])
          resolve(vpcs)
        })
      })
    },
    getRouters (netId, isVpc) {
      const params = {}
      if (!isVpc) {
        params.networkid = netId
      } else {
        params.vpcid = netId
      }

      return new Promise((resolve, reject) => {
        api('listRouters', params).then(response => {
          const routers = response?.listroutersresponse.router || []
          for (var d of routers) {
            const data = {}
            for (const key of this.columns) {
              if (key === 'parentId') {
                data[key] = this.root.id
              } else if (key === 'imageUrl') {
                data[key] = router
              } else {
                data[key] = d[key] || ''
              }
            }
            this.jsonData.push(data)
          }
          // this.routers = this.routers.concat(routers)
          resolve(routers)
          return routers
        }).catch(error => {
          reject(error)
        })
      })
    },
    getVirtualMachines (net) {
      return new Promise((resolve, reject) => {
        api('listVirtualMachines', {
          networkid: net.id
        }).then(response => {
          this.count = response?.listvirtualmachinesresponse?.count
          const vms = response?.listvirtualmachinesresponse?.virtualmachine || []
          for (var d of vms) {
            const data = {}
            for (const key of this.columns) {
              // TODO: Common switch-case construct - based on type
              if (key === 'role') {
                data[key] = 'VM'
              } else if (key === 'imageUrl') {
                data[key] = vm
              } else {
                data[key] = d[key] || ''
              }
            }
            // else if (key === 'parentId') {
            //   data[key] = net.id
            // }

            const indexOfObject = this.jsonData.findIndex(item => item.id === data.id)
            if (indexOfObject === -1) {
              data.parentId = net.id
              data.color = net.color
              //   this.jsonData.push(data)
              this.jsonData = [...this.jsonData, data]
            } else {
              data.parentId = net.id
              data.color = net.color
              this.connections.push({
                from: data.id,
                to: net.id,
                label: 'Secondary NIC',
                color: net.color
              })
            }
            // if (!this.jsonData.includes(data)) {
            //   data.parentId = net.id
            //   data.color = net.color
            //   this.jsonData.push(data)
            // }
            // data._upToTheRootHighlighted = true
            // this.jsonData.push(data)
          }
          resolve(vms)
        }).catch(error => {
          reject(error)
        })
      })
    },
    async renderGraph () {
      await this.collateData()
      var json = JSON.parse(JSON.stringify(JSON.stringify(this.jsonData)))
      var blob = new Blob([json], { type: 'application/json' })
      var url = URL.createObjectURL(blob)

      d3.json(url).then((dataFlattened) => {
        dataFlattened.forEach((d) => {
        })
        this.chart = new OrgChart()
          .container('.chart-container')
          .data(dataFlattened)
          .svgHeight(window.innerHeight - 10)
          .rootMargin(100)
          .nodeWidth((d) => 210)
          .childrenMargin((d) => 150)
          .compactMarginBetween((d) => 15)
          .compactMarginPair((d) => 150)
          .neightbourMargin((a, b) => 25)
          .siblingsMargin((d) => 50)
          .onNodeClick((d) => {
            this.toggleDetailsCard(d)
          })
          .buttonContent(({ node, state }) => {
            return `<div style="margin-top: 25px"> <div style="color:#716E7B;border-radius:5px;padding:4px;font-size:10px;margin-top:3px; margin-bottom:1px; margin-left:10px;padding-top:5px;background-color:white;border: 1px solid #E4E2E9"> <span style="font-size:9px">${
              node.children
                ? `<i class="fas fa-angle-up"></i>`
                : `<i class="fas fa-angle-down"></i>`
            }</span> ${node.data._directSubordinates}  </div> </div>`
          })
          .linkUpdate(function (d, i, arr) {
            d3.select(this)
              .attr('stroke', (d) => {
                // var randomColor = Math.floor(Math.random() * 16777215).toString(16)
                // return d.data._upToTheRootHighlighted ? '#' + randomColor : '#2CAAE5'
                // d.data._upToTheRootHighlighted ? '#14760D' : '#2CAAE5'
                return d.data.color || '#2CAAE5'
              })
              .attr('stroke-width', (d) => {
                d.data.nodeValue = 'hi there'
                return d.data._upToTheRootHighlighted ? 15 : 15
              }).attr('label', (d) => {
                return d.data.name
              }).attr('text-anchor', 'middle')
              .attr('font-size', '12px')
              .text('text-path', 'foo')

            if (d.data._upToTheRootHighlighted) {
              d3.select(this).raise()
            }
          })
          .nodeUpdate(function (d, i, arr) {
            d3.select(this)
              .select('.node-rect')
              .select('width').append('10px')
              // .attr('stroke', d => d.data._highlighted || d.data._upToTheRootHighlighted ? '#152785' : 'none')
              .attr('stroke', d => d.data._highlighted || d.data._upToTheRootHighlighted ? 'none' : 'none')
              // .attr('stroke-width', d.data._highlighted || d.data._upToTheRootHighlighted ? 10 : 1)
              .attr('stroke-width', d.data._highlighted || d.data._upToTheRootHighlighted ? 1 : 1)

              .attr('height', function (d) { return d.radius })
          })
          .nodeContent(function (d, i, arr, state) {
            const color = '#FFFFFF'
            d.width = 10
            d.height = 100
            return `
              <div style="font-family: 'Inter', sans-serif;background-color:${color}; position:absolute;margin-top:-10px; margin-left:-1px;width:1px;height:1px;border-radius:5px;border: 1px solid #FFFFFF">
                <div style="background-color:${color};position:absolute;margin-top:-25px;margin-left:${15}px;border-radius:5px;width:50px;height:50px;" ></div>
                  <img src="${d.data.imageUrl}" style="position:absolute;margin-top:-5px; margin-bottom: 10px; margin-left:-30px;border-radius:10px;width:70px;height:60px;" />             
              
                    <div style="font-size:15px;color:#08011E;margin-left:-20px;margin-top:62px"> ${
                      d.data.name || d.data.role} </div>
                    <div v-if="'${d.data.role}' !== 'Public Network'" style="color:#716E7B;margin-left:-20px;margin-top:3px;font-size:10px;"> ${
                      d.data.role
                    } </div>
            </div>
            `
          }).connections(
            // [{ from: '24207803-472b-45d8-aace-9093a4fe6c98', to: '157acd98-a519-4772-a11f-c3ad00ecaf35', label: 'Secondary' }]
            this.connections
          ).connectionsUpdate(function (d, i, arr) {
            console.log(arr)
            console.log(d.color)
            d3.select(this)
              .attr('stroke', (d) => d.color)
              .attr('stroke-linecap', 'butt')
              .attr('stroke-width', (d) => '5')
              .attr('pointer-events', 'none')
              .attr('stroke-linejoin', (d) => 'miter-clip')
              .attr('d', (d) => `M54.691055,158.060564q0,0,66.164888.525118l-1.050237-46.735516l45.160161-.525118`)

              //  .attr("marker-start", d => `url(#${d.from + "_" + d.to})`)
              //  .attr("marker-end", d => `url(#arrow-${d.from + "_" + d.to})`)
              .attr('stroke-dasharray', '20, 20')
          })
          .render().expandAll()
      })
    },
    async collateData () {
      var result = await this.getPublicNetworks()
      if (result) {
        result = await this.getNetworks()
      }
      if (result) {
        for (var net of result) {
          var randomColor = Math.floor(Math.random() * 16777215).toString(16)
          const routers = await this.getRouters(net.id, false)
          if (routers) {
            net.color = '#' + randomColor
            net.parentId = routers[0].id
            net.imageUrl = network
            net.role = 'Network'
            this.jsonData.push(net)
            await this.getVirtualMachines(net)
          }
        }
      }
      result = await this.getVpcs()
      if (result) {
        for (var vpc of result) {
          randomColor = Math.floor(Math.random() * 16777215).toString(16)
          const routers = await this.getRouters(vpc.id, true)
          if (routers) {
            vpc.parentId = routers[0].id
            vpc.color = '#' + randomColor
            vpc.role = 'VPC'
            vpc.imageUrl = vpcImage
            var nets = await this.getNetworks(vpc.id)
            this.jsonData.push(vpc)
            if (nets) {
              for (net of nets) {
                net.color = '#' + randomColor
                net.parentId = vpc.id
                net.imageUrl = network
                net.role = 'Network'
                this.jsonData.push(net)
                await this.getVirtualMachines(net)
              }
            }
          }
        }
      }
      console.log(JSON.stringify(this.jsonData))
    }
  }
}
</script>
<style scoped>
</style>
