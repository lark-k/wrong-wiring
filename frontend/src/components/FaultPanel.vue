<template>
  <aside class="fault-panel">
    <div class="panel-heading">
      <SlidersHorizontal :size="18" />
      <span>故障配置</span>
    </div>

    <section class="field-group">
      <label>幅值设置</label>
      <div class="dual-amplitude">
        <div class="numeric-field">
          <input v-model.number="model.voltageAmplitude" type="number" min="0.1" step="1" />
          <span>V</span>
        </div>
        <div class="numeric-field">
          <input v-model.number="model.currentAmplitude" type="number" min="0.1" step="0.1" />
          <span>A</span>
        </div>
      </div>
    </section>

    <section class="field-group">
      <label>断相选择</label>
      <div class="phase-grid">
        <button
          v-for="phase in phases"
          :key="`b-${phase}`"
          class="toggle-tile"
          :class="{ active: model.broken[phase] }"
          type="button"
          @click="model.broken[phase] = !model.broken[phase]"
        >
          {{ phase }}相断线
        </button>
      </div>
    </section>

    <section class="field-group">
      <label>反接选择</label>
      <div class="phase-grid">
        <button
          v-for="phase in phases"
          :key="`r-${phase}`"
          class="toggle-tile danger"
          :class="{ active: model.reversed[phase] }"
          type="button"
          @click="model.reversed[phase] = !model.reversed[phase]"
        >
          {{ phase }}相反接
        </button>
      </div>
    </section>

    <section class="field-group">
      <label for="phaseOrder">换相选择</label>
      <select id="phaseOrder" v-model="phaseOrderText">
        <option v-for="option in orderOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
    </section>

    <section class="field-group">
      <div class="row-between">
        <label>噪声设置</label>
        <label class="switch">
          <input v-model="model.noiseEnabled" type="checkbox" />
          <span></span>
        </label>
      </div>
      <div class="slider-row">
        <span>幅值扰动</span>
        <input v-model.number="model.amplitudeNoisePercent" type="range" min="0" max="5" step="0.1" />
        <b>{{ model.amplitudeNoisePercent }}%</b>
      </div>
      <div class="slider-row">
        <span>相角扰动</span>
        <input v-model.number="model.angleNoiseDegree" type="range" min="0" max="5" step="0.1" />
        <b>{{ model.angleNoiseDegree }}°</b>
      </div>
    </section>

    <div class="button-stack">
      <button class="primary-button" type="button" @click="$emit('simulate')">
        <Play :size="18" />
        开始仿真
      </button>
      <button class="secondary-button" type="button" @click="$emit('reset')">
        <RotateCcw :size="18" />
        重置为正确接线
      </button>
      <button class="secondary-button amber" type="button" @click="$emit('randomize')">
        <Shuffle :size="18" />
        随机生成故障
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Play, RotateCcw, Shuffle, SlidersHorizontal } from 'lucide-vue-next'

const model = defineModel({ type: Object, required: true })
defineEmits(['simulate', 'reset', 'randomize'])

const phases = ['A', 'B', 'C']
const orderOptions = [
  { value: 'ABC', label: 'ABC 正确顺序' },
  { value: 'BAC', label: 'BAC，A/B 互换' },
  { value: 'CBA', label: 'CBA，A/C 互换' },
  { value: 'ACB', label: 'ACB，B/C 互换' },
  { value: 'BCA', label: 'BCA，循环错位' },
  { value: 'CAB', label: 'CAB，循环错位' }
]

const phaseOrderText = computed({
  get: () => model.value.phaseOrder.join(''),
  set: (value) => {
    model.value.phaseOrder = value.split('')
  }
})
</script>
