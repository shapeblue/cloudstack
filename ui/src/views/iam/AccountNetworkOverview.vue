<template>
  <!-- <a-button
        @click='chart.layout(["right","bottom","left","top"][index++%4]).render().fit()'
        class="btn btn-action-button waves-effect waves-light"
      >
        <i class="fas fa-retweet"></i> swap
      </a-button>
      <br /> -->
  <line x1="100" y1="100" x2="500" y2="100" />
  <a-button @click="chart.fit()" class="btn btn-action-button waves-effect waves-light">
    <i class="fas fa-sync"></i> fit
  </a-button>
  <br />
  <!-- <a-button @click="chart.compact(!!(compact++%2)).render().fit()" class="btn btn-action-button waves-effect waves-light">
    <i class="fas fa-sitemap"></i> compact
  </a-button> -->
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
    console.log(this.resource)
    return {
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
      count: 0,
      cardShow: false,
      currentCount: 0,
      initCount: 1,
      root: {},
      parentId: '',
      zoneId: this.resource.zoneId,
      nodeId: '',
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
      console.log(this.cardShow)
    },
    async getPublicNetworks () {
      return new Promise((resolve, reject) => {
        api('listNetworks', {
          zoneid: this.zoneId,
          issystem: true,
          traffictype: 'Public'
        }).then(async response => {
          this.publicNetworks = response?.listnetworksresponse?.network || []
          this.publicNetworks[0].role = 'Public Network'
          this.publicNetworks[0].imageUrl = internet
          this.publicNetworks[0]._upToTheRootHighlighted = true
          this.jsonData.push(this.publicNetworks[0])
          this.root = this.publicNetworks[0]
          await this.getNetworks()
          resolve(this.publicNetworks)
        })
      })
    },
    async getNetworks () {
      console.log('here')
      return new Promise((resolve, reject) => {
        console.log(this.resource)
        api('listNetworks', {
          account: this.resource.name,
          zoneid: this.resource.datacenterid
        }).then(async response => {
          console.log(response)
          const networks = this.networks.concat(response?.listnetworksresponse?.network || [])
          networks.forEach(async (net) => {
            console.log('hhh: ', net)
            await this.getRouters(net.id)
            console.log(this.routers)
            // net.parentId = routers.length > 1 ? routers.filter(r => r.redundantstate === 'PRIMARY')[0]?.id : routers[0].id
            net.parentId = this.routers[this.routers.length - 1].id
            net.imageUrl = network
            net.role = 'Network'
            // net._upToTheRootHighlighted = true
            net.service = ''
            this.jsonData.push(net)
            await this.getVirtualMachines(net.id)
          })
          this.networks = this.networks.concat(networks)
          console.log(this.networks)
          resolve(this.networks)
          // resolve(this.routers)
        })
      })
    },
    getRouters (netId) {
      return new Promise((resolve, reject) => {
        api('listRouters', {
          networkid: netId
        }).then(response => {
          const routers = response?.listroutersresponse.router || []
          console.log(routers)
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
            console.log('here1')
            this.jsonData.push(data)
          }
          this.routers = this.routers.concat(routers)
          resolve(routers)
          return routers
        }).catch(error => {
          reject(error)
        })
      })
    },
    getVirtualMachines (netId) {
      console.log(netId)
      return new Promise((resolve, reject) => {
        api('listVirtualMachines', {
          networkid: netId
        }).then(response => {
          this.count = response?.listvirtualmachinesresponse?.count
          const vms = response?.listvirtualmachinesresponse?.virtualmachine || []
          for (var d of vms) {
            // this.currentCount += 1
            // cnt += 1
            const data = {}
            for (const key of this.columns) {
              // TODO: Common switch-case construct - based on type
              if (key === 'role') {
                data[key] = 'VM'
              } else if (key === 'parentId') {
                data[key] = netId
              } else if (key === 'imageUrl') {
                data[key] = vm
              } else {
                data[key] = d[key] || ''
              }
            }
            this.jsonData.push(data)
          }
          this.vms = this.vms.concat(vms)
          console.log(this.vms)
          resolve(this.vms)
        }).catch(error => {
          reject(error)
        })
      })
    },
    async renderGraph () {
      await this.collateData()
      console.log(this.jsonData)

      console.log(JSON.stringify(this.jsonData))
      var json = JSON.parse(JSON.stringify(JSON.stringify(this.jsonData)))
      console.log('parsed json: ' + json)
      var blob = new Blob([json], { type: 'application/json' })
      var url = URL.createObjectURL(blob)

      var svg = d3.selectAll('g.node')
      console.log(svg)
      // var node = svg.selectAll('.node')
      // node.enter().append('image')
      //   .attr('class', function (d) {
      //     return 'node ' + d.id
      //   })
      //   .attr('xlink:href', 'https://github.com/favicon.ico')
      //   .attr('width', '16px')
      //   .attr('height', '16px')

      // node.exit().remove()

      // console.log(svg)

      d3.json(url).then((dataFlattened) => {
        dataFlattened.forEach((d) => {
          // const val = Math.round(d.name.length / 2)
          // d.progress = [...new Array(val)].map((d) => Math.random() * 25 + 5)
        })
        this.chart = new OrgChart()
          .container('.chart-container')
          .data(dataFlattened)
          .svgHeight(window.innerHeight - 10)
          .rootMargin(100)
          .nodeWidth((d) => 210)
          .childrenMargin((d) => 150)
          .compactMarginBetween((d) => 25)
          .compactMarginPair((d) => 150)
          .neightbourMargin((a, b) => 25)
          .siblingsMargin((d) => 50)
          .onNodeClick((d) => {
            this.toggleDetailsCard(d)
          })
          .buttonContent(({ node, state }) => {
            return `<div style="px;color:#716E7B;border-radius:5px;padding:4px;font-size:10px;margin:2px;padding-top:5px;background-color:white;border: 1px solid #E4E2E9"> <span style="font-size:9px">${
              node.children
                ? `<i class="fas fa-angle-up"></i>`
                : `<i class="fas fa-angle-down"></i>`
            }</span> ${node.data._directSubordinates}  </div>`
          })
          .linkUpdate(function (d, i, arr) {
            d3.select(this)
              .attr('stroke', (d) => {
                var randomColor = Math.floor(Math.random() * 16777215).toString(16)
                return d.data._upToTheRootHighlighted ? '#' + randomColor : '#2CAAE5'
                // d.data._upToTheRootHighlighted ? '#14760D' : '#2CAAE5'
              })
              .attr('stroke-width', (d) =>
                d.data._upToTheRootHighlighted ? 15 : 3
              ).attr('label', 'hi')

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
            // const states = Object.keys(this.colors)
            // console.log(`
            //   <div style="font-family: 'Inter', sans-serif;background-color:${color}; position:absolute;margin-top:-1px; margin-left:-1px;width:${d.width}px;height:${d.height}px;border-radius:10px;border: 1px solid #E4E2E9">
            //     <div :style="background-color: ${states}.contains(${d.data?.state}) ? ${this.colors[d.data.state]} : ${color};position:absolute;margin-top:-25px;margin-left:${15}px;border-radius:100px;width:50px;height:50px;" ></div>
            //       <img src="${d.data.imageUrl}" style="position:absolute;margin-top:-20px;margin-left:${20}px;border-radius:100px;width:40px;height:40px;" />

            //     <div style="color:#08011E;position:absolute;right:20px;top:17px;font-size:10px;"><i class="fas fa-ellipsis-h"></i></div>

            //     <div style="font-size:15px;color:#08011E;margin-left:20px;margin-top:32px"> ${
            //       d.data.name
            //     } </div>
            //     <div style="color:#716E7B;margin-left:20px;margin-top:3px;font-size:10px;"> ${
            //       d.data.role
            //     } </div>
            // </div>`)
            d.width = 10
            d.height = 110
            console.log(d.width + '   ' + d.height)
            return `
              <div style="font-family: 'Inter', sans-serif;background-color:${color}; position:absolute;margin-top:-1px; margin-left:-1px;width:1px;height:1px;border-radius:0px;border: 1px solid #FFFFFF">
                <div style="background-color:${color};position:absolute;margin-top:-25px;margin-left:${15}px;border-radius:5px;width:50px;height:50px;" ></div>
                  <img src="${d.data.imageUrl}" style="position:absolute;margin-top:-5px; margin-bottom: 10px; margin-left:-30px;border-radius:10px;width:70px;height:60px;" />             
              
                    <div style="font-size:15px;color:#08011E;margin-left:-10px;margin-top:62px"> ${
                      d.data.name || d.data.role} </div>
                    <div v-if="'${d.data.role}' !== 'Public Network'" style="color:#716E7B;margin-left:-10px;margin-top:3px;font-size:10px;"> ${
                      d.data.role
                    } </div>
            </div>
            `
          })
          .render().expandAll()
      })

      console.log(this.cardShow)
    },
    async collateData () {
      await this.getPublicNetworks()
      await this.getNetworks()
    }
  }
}
</script>
<style scoped>
</style>
