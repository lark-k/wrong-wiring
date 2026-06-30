<template>
  <header class="header-bar">
    <div>
      <div class="eyebrow">Three-phase Matrix Lab</div>
      <h1>基于矩阵计算的电能表错接线仿真识别系统</h1>
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
