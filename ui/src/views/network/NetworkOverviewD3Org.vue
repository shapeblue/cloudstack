<template>
 <!-- <a-button
        @click='chart.layout(["right","bottom","left","top"][index++%4]).render().fit()'
        class="btn btn-action-button waves-effect waves-light"
      >
        <i class="fas fa-retweet"></i> swap
      </a-button>
      <br /> -->
  <line x1="100" y1="100" x2="500" y2="100" />
  <a-button @click="chart.fit()" class="btn btn-action-button waves-effect waves-light" >
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
    <network-details :show="this.cardShow" :node="this.jsonData.filter(d => d.id === this.nodeId)" />
  </div>
  <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css" rel="stylesheet" />
  <div class="node-modal"></div>
</template>

<script>
import * as d3 from 'd3'
import { OrgChart } from 'd3-org-chart'
import { api } from '@/api'
import router from '../../assets/icons/router-xxl.png'
import network from '../../assets/icons/network.png'
import vm from '../../assets/icons/vm.png'
import more from '../../assets/icons/more.png'
import NetworkDetails from './NetworkDetails'

export default {
  name: 'NetworkOverviewD3Org',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  components: {
    NetworkDetails
  },
  // async mounted () {
  //   this.renderGraph()
  // },
  async created () {
    this.renderGraph()
  },
  data () {
    return {
      chart: {},
      colors: {
        Running: '#b0fa8e',
        Stopped: '#ffa8a8'
      },
      jsonData: [],
      networks: [],
      routers: [],
      vms: [],
      nics: [],
      count: 0,
      cardShow: false,
      currentCount: 0,
      initCount: 1,
      parentId: '',
      nodeId: '',
      columns: ['name', 'imageUrl', 'id', 'role', 'hostid', 'hostname', 'guestipaddress',
        'guestmacaddress', 'guestnetmask', 'guestnetworkname', 'linklocalip', 'state', 'templateid',
        'templatename', 'parentId', 'ostypeid', 'ostypename', 'osdisplayname', 'broadcastdomaintype',
        'broadcasturi', 'cidr', 'gateway', 'dns1', 'dns2', 'networkofferingname', 'networkofferingid', 'domain', 'account']
    }
  },
  provide: function () {
    return {
      updateShowCard: this.toggleDetailsCard
    }
  },
  methods: {
    toggleDetailsCard (d) {
      this.cardShow = !this.cardShow
      this.nodeId = d
      console.log(this.cardShow)
    },
    async renderGraph () {
      await this.collateData()
      // console.log(this.jsonData)

      // console.log(JSON.stringify(this.jsonData))
      var json = JSON.parse(JSON.stringify(JSON.stringify(this.jsonData)))
      console.log('parsed json: ' + json)
      var blob = new Blob([json], { type: 'application/json' })
      var url = URL.createObjectURL(blob)

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
          .childrenMargin((d) => 25)
          .compactMarginBetween((d) => 25)
          .compactMarginPair((d) => 50)
          .neightbourMargin((a, b) => 25)
          .siblingsMargin((d) => 5)
          .onNodeClick((d) => {
            this.toggleDetailsCard(d)
          })
          .buttonContent(({ node, state }) => {
            return `<div style="px;color:#716E7B;border-radius:5px;padding:4px;font-size:10px;margin:auto auto;background-color:white;border: 1px solid #E4E2E9"> <span style="font-size:9px">${
              node.children
                ? `<i class="fas fa-angle-up"></i>`
                : `<i class="fas fa-angle-down"></i>`
            }</span> ${node.data._directSubordinates}  </div>`
          })
          .linkUpdate(function (d, i, arr) {
            // d3.select(this)
            //   .attr('stroke', (d) =>
            //     d.data._upToTheRootHighlighted ? '#152785' : '#E4E2E9'
            //   )
            //   .attr('stroke-width', (d) =>
            //     d.data._upToTheRootHighlighted ? 5 : 1
            //   )
            // if (d.data._upToTheRootHighlighted) {
            //   d3.select(this).raise()
            // }
            d3.select(this)
              .attr('stroke', (d) =>
                d.data._upToTheRootHighlighted ? '#14760D' : '#2CAAE5'
              )
              .attr('stroke-width', (d) =>
                d.data._upToTheRootHighlighted ? 15 : 1
              )

            if (d.data._upToTheRootHighlighted) {
              d3.select(this).raise()
            }
          })
          .nodeUpdate(function (d, i, arr) {
            d3.select(this)
              .select('.node-rect')
              .attr('stroke', d => d.data._highlighted || d.data._upToTheRootHighlighted ? '#152785' : 'none')
              .attr('stroke-width', d.data._highlighted || d.data._upToTheRootHighlighted ? 10 : 1)

            d3.select(this).append('circle')
              .attr('r', 4.5)
              .attr('fill', function (d) {
                console.log(d)
                if (d.data.role === 'VM') {
                  return 'white'
                } else if (d.data.role === 'VIRTUAL_ROUTER') {
                  return 'orange'
                } else {
                  return 'red'
                }
              })
          })
          .expandAll()
          // .nodeUpdate(function (d, i, arr) {
          //   d3.select('node-rect')
          //     .attrr('r', 6.2)
          //     .style('filter', function (d) {
          //       return d._children ? 'url(#image)' : '#fff'
          //     }).on('mouseover', function (d) {
          //       d3.select(this).style('cursor', 'pointer')
          //     })
          // })
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
            return `
              <div style="font-family: 'Inter', sans-serif;background-color:${color}; position:absolute;margin-top:-1px; margin-left:-1px;width:${d.width}px;height:${d.height}px;border-radius:10px;border: 1px solid #E4E2E9">
                <div style="background-color:${color};position:absolute;margin-top:-25px;margin-left:${15}px;border-radius:100px;width:50px;height:50px;" ></div>
                  <img src="${d.data.imageUrl}" style="position:absolute;margin-top:-20px;margin-left:${20}px;border-radius:100px;width:40px;height:40px;" />             
                
                <div style="color:#08011E;position:absolute;right:20px;top:17px;font-size:10px;"><i class="fas fa-ellipsis-h"></i></div>

                <div style="font-size:15px;color:#08011E;margin-left:20px;margin-top:32px"> ${
                  d.data.name
                } </div>
                <div style="color:#716E7B;margin-left:20px;margin-top:3px;font-size:10px;"> ${
                  d.data.role
                } </div>
            </div>
            `
          }).render()
      })

      console.log(this.cardShow)
    },
    getNetworkDetails () {
      return new Promise((resolve, reject) => {
        this.networks.push(this.resource)
        for (var d of this.networks) {
          const data = {}
          for (const key of this.columns) {
            if (key === 'imageUrl') {
              data[key] = network
            } else if (key === 'role') {
              data[key] = 'Network'
            } else {
              data[key] = d[key] || ''
            }
          }
          this.jsonData.push(data)
        }
        resolve(this.networks)
      })
    },
    getRouters () {
      return new Promise((resolve, reject) => {
        api('listRouters', {
          networkid: this.resource.id
        }).then(response => {
          this.routers = response?.listroutersresponse.router || []
          for (var d of this.routers) {
            const data = {}
            for (const key of this.columns) {
              if (key === 'parentId') {
                data[key] = this.resource.id
              } else if (key === 'imageUrl') {
                data[key] = router
              } else {
                data[key] = d[key] || ''
              }
            }
            this.jsonData.push(data)
          }
          resolve(this.routers)
        }).catch(error => {
          reject(error)
        })
      })
    },
    getVirtualMachines () {
      this.parentId = this.routers[0].id
      // const routerId = this.parentId
      // let cnt = 0
      return new Promise((resolve, reject) => {
        api('listVirtualMachines', {
          networkid: this.resource.id
        }).then(response => {
          this.count = response?.listvirtualmachinesresponse?.count
          this.vms = response?.listvirtualmachinesresponse?.virtualmachine || []
          for (var d of this.vms) {
            // this.currentCount += 1
            // cnt += 1
            const data = {}
            for (const key of this.columns) {
              // TODO: Common switch-case construct - based on type
              if (key === 'role') {
                data[key] = 'VM'
              } else if (key === 'parentId') {
                data[key] = this.parentId
              } else if (key === 'imageUrl') {
                data[key] = vm
              } else {
                data[key] = d[key] || ''
              }
            }
            this.jsonData.push(data)
            // if (cnt === this.initCount && this.currentCount < this.count) {
            //   cnt = 0
            //   this.getAdditionalNodes(routerId)
            // }
          }
          resolve(this.vms)
        }).catch(error => {
          reject(error)
        })
      })
    },
    create_UUID () {
      var dt = new Date().getTime()
      var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (dt + Math.random() * 16) % 16 | 0
        dt = Math.floor(dt / 16)
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
      })
      return uuid
    },
    getAdditionalNodes (parentId) {
      // this.jsonData = this.jsonData.filter(d => d.name !== 'More VMs available...')
      const data = {
        name: 'More VMs available...',
        id: this.create_UUID(),
        parentId: parentId,
        imageUrl: more
      }
      this.parentId = data.id
      this.jsonData.push(data)
    },
    getNics () {
      return new Promise((resolve, reject) => {
        for (var vm of this.vms) {
          const nic = vm.nic.map(n => {
            n.parentId = vm.id
            n.role = 'NIC'
            return n
          })
          this.jsonData.push(nic[0])
        }
        console.log(this.nics)
        resolve(this.jsonData)
      })
    },
    async collateData () {
      await this.getNetworkDetails()
      await this.getRouters()
      await this.getVirtualMachines()
      // await this.getNics()
    }
  }
}
</script>
<style scoped>
.node-rect {
  cursor: pointer;
  fill: #fff;
  fill-opacity: .5;
  stroke: #3182bd;
  stroke-width: 1.5px;
}
</style>
