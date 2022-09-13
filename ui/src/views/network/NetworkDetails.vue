<template>
  <a-modal :visible="show" :footer="null" :closable="true" @cancel="handleClose">
    <a-card>
          <a-list itemLayout="horizontal" size="small" :dataSource="node">
            <template #renderItem="{ item }">
              <!-- <span><img avatar :src="item.imageUrl" class="element"/> </span> -->
              <span title style="marginLeft: 20px"> <strong> {{ item.role }}: {{ item.name }} </strong></span>
              <a-divider />
              <div v-for="(idx, key) in item" :key="idx">
                <div v-if="item[key] && key !== 'imageUrl'">
                  <span> <strong> {{ capitalizeFirstLetter(key) }}: </strong> </span>
                  <span> {{ item[key] }} </span>
                  <br/>
                </div>
              </div>
            </template>
      </a-list>
    </a-card>
  </a-modal>
</template>
<script>
export default {
  name: 'NetworkDetails',
  inject: ['updateShowCard'],
  props: {
    show: {
      type: Boolean,
      value: false
    },
    node: {
      type: Object,
      default () {
        return {}
      }
    }
  },
  mounted () {
    console.log('here')
  },
  created () {
    console.log('c-here')
  },
  methods: {
    handleClose () {
      this.updateShowCard()
    },
    capitalizeFirstLetter (string) {
      return string.charAt(0).toUpperCase() + string.slice(1)
    }
  }
}
</script>
<style scoped>
.element {
  width:40px;
  height:40px;
}
</style>
