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
<div class="vm-info-card">
  <v-network-graph
    class="vm-info-card"
    :nodes="nodes"
    :edges="edges"
    :layouts="layouts"
    :configs="configs"
     >
    <!-- Cannot use <style> directly due to restrictions of Vue. -->
      <defs>
      <!-- Cannot use <style> directly due to restrictions of Vue. -->
      <!-- eslint-disable-next-line vue/require-component-is -->
      <component is="style">
        <!-- eslint-disable prettier/prettier -->
        @font-face { font-family: 'Material Icons'; font-style: normal; font-weight: 400; src:
        url(https://fonts.gstatic.com/s/materialicons/v97/flUhRq6tzZclQEJ-Vdg-IuiaDsNcIhQ8tQ.woff2)
        format('woff2'); }
        .edge-icon { pointer-events: none; }
        <!-- eslint-enable -->
      </component>
    </defs>
    <template #override-node="{ nodeId, scale, config, ...slotProps }">
      <circle :r="config.radius * scale" :fill="config.color" v-bind="slotProps" />
      <!-- Use v-html to interpret escape sequences for icon characters. -->
      <text
        font-family="Material Icons"
        :font-size="22 * scale"
        fill="#ffffff"
        text-anchor="middle"
        dominant-baseline="central"
        style="pointer-events: none"
        v-html="nodes[nodeId].icon"
      />
    </template>
  </v-network-graph>
</div>
</template>
<script>
import { reactive } from 'vue'
import * as vNG from 'v-network-graph'
import { ForceLayout } from 'v-network-graph/lib/force-layout'

// const nodeSize = 40

export default {
  name: 'NetworkOverview',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  created () {
    this.configs = reactive(
      vNG.defineConfigs({
        view: {
          layoutHandler: new ForceLayout({
            positionFixedByDrag: true,
            scalingObjects: false,
            fit: false,
            zoomEnabled: true,
            mouseWheelZoomEnabled: true,
            autoPanAndZoomOnLoad: true
          })
        },
        node: {
          draggable: false
        }
      })
    )
  },
  data () {
    return {
      nodes: {
        node1: { name: 'N1', icon: '&#xe320' /* Laptop Mac */ },
        node2: { name: 'N2', icon: '&#xe328' /* Router */ },
        node3: { name: 'N3', icon: '&#xe331' /* Tablet Mac */ },
        node4: { name: 'N4', icon: '&#xe2bd' /* Cloud */ },
        node5: { name: 'N5', icon: '&#xf0e2' /* Support Agent */ },
        node6: { name: 'N6', icon: '&#xea75' /* Video Settings */ }
      },
      edges: {
        edge1: { source: 'node1', target: 'node2' },
        edge2: { source: 'node2', target: 'node3' },
        edge3: { source: 'node2', target: 'node4' },
        edge4: { source: 'node4', target: 'node5' },
        edge5: { source: 'node4', target: 'node6' }
      },
      layouts: reactive({
        nodes: {}
      })
    }
  }
}
</script>
<style>
.v-network-graph {
  padding: 0;
  user-select: none;
  height: 50vw;
}
</style>
