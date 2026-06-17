<template>
  <header class="header-bar">
    <div>
      <div class="eyebrow">Three-phase Matrix Lab</div>
      <h1>错接线矩阵可视化仿真系统</h1>
      <p>基于 D / S / P 矩阵的三相电压电流错接线模拟</p>
    </div>
    <div class="header-actions">
      <button class="ghost-button" type="button" @click="$emit('export-json')">
        <FileJson2 :size="17" />
        JSON
      </button>
      <button class="ghost-button" type="button" @click="$emit('export-csv')">
        <Table2 :size="17" />
        CSV
      </button>
      <StatusBadge :correct="correct" />
    </div>
  </header>
</template>

<script setup>
import { FileJson2, Table2 } from 'lucide-vue-next'
import StatusBadge from './StatusBadge.vue'

defineProps({
  correct: {
    type: Boolean,
    default: true
  }
})

defineEmits(['export-json', 'export-csv'])
</script>
