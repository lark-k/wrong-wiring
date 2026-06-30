<template>
  <article class="matrix-card" :class="{ highlight }">
    <div class="card-title-row">
      <h3>{{ title }}</h3>
      <span v-if="identity" class="mini-pill">正确接线</span>
    </div>
    <p v-if="description">{{ description }}</p>
    <div class="matrix-grid">
      <span
        v-for="(value, index) in flattened"
        :key="index"
        :class="valueClass(value)"
      >
        {{ formatValue(value) }}
      </span>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  matrix: { type: Array, default: () => [] },
  description: { type: String, default: '' },
  highlight: { type: Boolean, default: false }
})

const flattened = computed(() => props.matrix.flat())
const identity = computed(() => JSON.stringify(props.matrix) === JSON.stringify([[1, 0, 0], [0, 1, 0], [0, 0, 1]]))

function valueClass(value) {
  return {
    zero: Number(value) === 0,
    one: Number(value) === 1,
    negative: Number(value) === -1
  }
}

function formatValue(value) {
  return Number.isInteger(value) ? value : Number(value).toFixed(2)
}
</script>
