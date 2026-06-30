<template>
  <div class="candidate-phasor-mini" aria-label="现场值与候选向量三相对比图">
    <svg class="candidate-phasor-svg" viewBox="0 0 132 112" role="img">
      <g class="mini-grid">
        <circle v-for="r in [22, 34, 46]" :key="r" :cx="center" :cy="centerY" :r="r" />
        <line class="axis" x1="18" :y1="centerY" x2="114" :y2="centerY" />
        <line class="axis" :x1="center" y1="12" :x2="center" y2="104" />
        <line
          v-for="angle in [30, 60, 120, 150]"
          :key="angle"
          class="spoke"
          :x1="axisPoint(angle, 48).x"
          :y1="axisPoint(angle, 48).y"
          :x2="axisPoint(angle, -48).x"
          :y2="axisPoint(angle, -48).y"
        />
      </g>

      <g v-for="vector in actualVectors" :key="`actual-${vector.key}`">
        <line
          class="mini-vector actual"
          :class="vector.phase"
          :x1="center"
          :y1="centerY"
          :x2="vector.x"
          :y2="vector.y"
        />
      </g>

      <g v-for="vector in candidateVectors" :key="`candidate-${vector.key}`">
        <line
          class="mini-vector candidate"
          :class="vector.phase"
          :x1="center"
          :y1="centerY"
          :x2="vector.x"
          :y2="vector.y"
        />
        <circle class="mini-vector-tip" :class="vector.phase" :cx="vector.x" :cy="vector.y" r="2.8" />
        <text class="mini-vector-label" :class="vector.phase" :x="vector.labelX" :y="vector.labelY">
          {{ vector.label }}
        </text>
      </g>
    </svg>
    <div class="candidate-phasor-legend">
      <span><i class="actual"></i>y</span>
      <span><i class="candidate"></i>ŷ</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  actual: { type: Array, default: () => [] },
  candidate: { type: Array, default: () => [] },
  prefix: { type: String, default: 'U' }
})

const center = 66
const centerY = 58
const maxRadius = 42

const maxAmplitude = computed(() => {
  const amplitudes = [...props.actual, ...props.candidate].map((entry) => Number(entry?.amplitude || 0))
  return Math.max(...amplitudes, 1)
})

const actualVectors = computed(() => buildVectors(props.actual, false))
const candidateVectors = computed(() => buildVectors(props.candidate, true))

function buildVectors(entries, withLabel) {
  return entries.slice(0, 3).map((entry, index) => {
    const amplitude = Number(entry?.amplitude || 0)
    const angle = Number(entry?.angle || 0)
    const phase = phaseClass(entry, index)
    const radius = amplitude / maxAmplitude.value * maxRadius
    const radians = angle * Math.PI / 180
    const unitX = Math.cos(radians)
    const unitY = -Math.sin(radians)
    const x = center + unitX * radius
    const y = centerY + unitY * radius
    const zero = amplitude < 0.01
    return {
      key: `${phase}-${index}`,
      phase,
      x,
      y,
      label: withLabel ? `${props.prefix}${index + 1}` : '',
      labelX: zero ? center + 5 + index * 7 : x + unitX * 8,
      labelY: zero ? centerY - 5 - index * 6 : y + unitY * 8
    }
  })
}

function phaseClass(entry, index) {
  const raw = String(entry?.source || entry?.phase || ['A', 'B', 'C'][index]).toUpperCase()
  if (raw.includes('A') || raw.includes('1')) return 'a'
  if (raw.includes('B') || raw.includes('2')) return 'b'
  if (raw.includes('C') || raw.includes('3')) return 'c'
  return ['a', 'b', 'c'][index] || 'a'
}

function axisPoint(angle, radius) {
  const radians = angle * Math.PI / 180
  return {
    x: center + Math.cos(radians) * radius,
    y: centerY - Math.sin(radians) * radius
  }
}
</script>
