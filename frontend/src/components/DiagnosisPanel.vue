<template>
  <article class="diagnosis-panel" :class="{ ok: result?.correctWiring }">
    <div class="card-title-row">
      <h3>故障解释</h3>
      <span class="mini-pill">{{ result?.correctWiring ? 'Normal' : 'Fault' }}</span>
    </div>
    <p class="summary" :title="summaryText">{{ summaryText }}</p>
    <div class="diagnosis-text" :title="diagnosisText">{{ diagnosisText }}</div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: {
    type: Object,
    default: null
  }
})

const summaryText = computed(() => props.result?.summary || '等待仿真结果')
const diagnosisText = computed(() => props.result?.diagnosis || '暂无诊断')
</script>
