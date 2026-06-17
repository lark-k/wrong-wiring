<template>
  <section class="visual-stage">
    <div class="stage-header">
      <div>
        <h2>接线图可视化</h2>
        <p>源端到测量通道的实时映射</p>
      </div>
      <div class="legend">
        <span><i class="green"></i>正常</span>
        <span><i class="red"></i>故障</span>
        <span><i class="dash"></i>断线</span>
      </div>
    </div>

    <svg class="wiring-svg" viewBox="0 0 760 330" role="img" aria-label="三相接线图">
      <g v-for="line in lineModels" :key="line.channel">
        <path
          class="wire-path"
          :class="{ fault: line.fault, broken: line.broken }"
          :d="line.path"
          @mouseenter="hovered = line"
          @mouseleave="hovered = null"
        />
        <text
          v-if="line.reversed"
          class="reverse-mark"
          :x="line.midX"
          :y="line.midY - 10"
        >−</text>
        <circle v-if="line.broken" class="break-dot" :cx="line.midX" :cy="line.midY" r="7" />
      </g>

      <g v-for="node in sourceNodes" :key="node.phase">
        <circle class="node source" :cx="node.x" :cy="node.y" r="18" />
        <text class="node-label" :x="node.x" :y="node.y + 5">{{ node.phase }}</text>
      </g>

      <g v-for="node in channelNodes" :key="node.channel">
        <circle class="node channel" :cx="node.x" :cy="node.y" r="18" />
        <text class="node-label" :x="node.x" :y="node.y + 5">{{ node.index }}</text>
        <text class="channel-label" :x="node.x + 30" :y="node.y + 5">{{ node.channel }}</text>
      </g>

      <foreignObject v-if="hovered" :x="hovered.midX - 80" :y="hovered.midY + 16" width="190" height="62">
        <div class="svg-tooltip">
          <strong>{{ hovered.source }} → {{ hovered.channel }}</strong>
          <span>{{ hovered.status }}</span>
        </div>
      </foreignObject>
    </svg>
  </section>

  <section class="phasor-stage">
    <div class="stage-header compact">
      <div>
        <h2>三相相量坐标图</h2>
        <p>理论向量 x 与测量向量 y 叠加对比</p>
      </div>
    </div>

    <svg class="phasor-svg" viewBox="0 0 760 360" role="img" aria-label="三相相量坐标图">
      <defs>
        <marker id="arrow-x" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="#5ce1e6" />
        </marker>
        <marker id="arrow-y" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="#ff6b6b" />
        </marker>
      </defs>

      <g class="grid">
        <circle v-for="r in [55, 95, 135]" :key="r" cx="280" cy="180" :r="r" />
        <line x1="105" y1="180" x2="455" y2="180" />
        <line x1="280" y1="20" x2="280" y2="340" />
        <line v-for="angle in angleTicks" :key="angle" :x1="axisPoint(angle, 160).x" :y1="axisPoint(angle, 160).y" :x2="axisPoint(angle, -160).x" :y2="axisPoint(angle, -160).y" />
      </g>

      <text class="angle-label" x="462" y="184">0°</text>
      <text class="angle-label" x="145" y="57">120°</text>
      <text class="angle-label" x="127" y="312">-120°</text>
      <text class="axis-label" x="464" y="164">Re</text>
      <text class="axis-label" x="292" y="30">Im</text>

      <g v-for="vector in theoreticalVectors" :key="`x-${vector.name}`">
        <line class="phasor-line theoretical" x1="280" y1="180" :x2="vector.x" :y2="vector.y" marker-end="url(#arrow-x)" />
        <text class="phasor-label theoretical" :x="vector.labelX" :y="vector.labelY">{{ vector.name }}</text>
      </g>

      <g v-for="vector in measuredVectors" :key="`y-${vector.name}`">
        <line
          class="phasor-line measured"
          :class="{ zero: vector.zero, reversed: vector.reversed, broken: vector.broken }"
          x1="280"
          y1="180"
          :x2="vector.x"
          :y2="vector.y"
          marker-end="url(#arrow-y)"
        />
        <circle v-if="vector.zero" class="origin-dot" cx="280" cy="180" r="6" />
        <text class="phasor-label measured" :x="vector.labelX" :y="vector.labelY">{{ vector.name }}</text>
      </g>

      <g class="phasor-readout">
        <text x="520" y="64">相量读数</text>
        <g v-for="(entry, index) in result?.y || []" :key="entry.channel" :transform="`translate(520 ${98 + index * 58})`">
          <rect width="190" height="42" rx="8" />
          <text x="12" y="15">{{ entry.channel }} · 源 {{ entry.source }}</text>
          <text x="12" y="31">{{ entry.polar }}</text>
          <text v-if="entry.broken || entry.reversed" x="112" y="31">{{ vectorStatus(entry) }}</text>
        </g>
      </g>
    </svg>

  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  result: {
    type: Object,
    default: null
  }
})

const hovered = ref(null)
const sourceNodes = [
  { phase: 'A', x: 70, y: 75 },
  { phase: 'B', x: 70, y: 165 },
  { phase: 'C', x: 70, y: 255 }
]
const channelNodes = [
  { channel: '通道1', index: '1', x: 610, y: 75 },
  { channel: '通道2', index: '2', x: 610, y: 165 },
  { channel: '通道3', index: '3', x: 610, y: 255 }
]
const defaultConnections = [
  { channel: '通道1', source: 'A', broken: false, reversed: false, swapped: false, status: '正常' },
  { channel: '通道2', source: 'B', broken: false, reversed: false, swapped: false, status: '正常' },
  { channel: '通道3', source: 'C', broken: false, reversed: false, swapped: false, status: '正常' }
]
const angleTicks = [30, 60, 120, 150]

const lineModels = computed(() => {
  const connections = normalizeConnections(props.result?.connections)

  return connections.map((connection, index) => {
    const source = sourceNodes.find((node) => node.phase === connection.source) || sourceNodes[index]
    const target = channelNodes[index]
    const midX = 340
    const midY = (source.y + target.y) / 2
    const curve = connection.swapped ? 145 : 80

    return {
      ...connection,
      fault: connection.broken || connection.reversed || connection.swapped,
      midX,
      midY,
      path: `M ${source.x + 22} ${source.y} C ${source.x + curve} ${source.y}, ${target.x - curve} ${target.y}, ${target.x - 22} ${target.y}`
    }
  })
})

const theoreticalVectors = computed(() => (props.result?.x || []).map((entry) => vectorModel(entry, entry.phase, 'x')))
const measuredVectors = computed(() => (props.result?.y || []).map((entry) => vectorModel(entry, entry.channel.replace('通道', 'y'), 'y')))

function normalizeConnections(connections) {
  if (!Array.isArray(connections) || connections.length !== 3) {
    return defaultConnections
  }
  return connections.map((connection, index) => ({
    channel: connection.channel || `通道${index + 1}`,
    source: connection.source || ['A', 'B', 'C'][index],
    broken: Boolean(connection.broken),
    reversed: Boolean(connection.reversed),
    swapped: Boolean(connection.swapped),
    status: connection.status || '正常'
  }))
}

function vectorModel(entry, name, kind) {
  const maxAmp = Math.max(...[...(props.result?.x || []), ...(props.result?.y || [])].map((item) => item.amplitude || 0), 1)
  const scale = 135 / maxAmp
  const length = (entry.amplitude || 0) * scale
  const radians = (entry.angle || 0) * Math.PI / 180
  const x = 280 + Math.cos(radians) * length
  const y = 180 - Math.sin(radians) * length
  const labelOffset = kind === 'x' ? 12 : 24

  return {
    name,
    x,
    y,
    labelX: x + labelOffset,
    labelY: y - labelOffset,
    zero: (entry.amplitude || 0) < 0.01,
    reversed: entry.reversed,
    broken: entry.broken
  }
}

function axisPoint(angle, radius) {
  const radians = angle * Math.PI / 180
  return {
    x: 280 + Math.cos(radians) * radius,
    y: 180 - Math.sin(radians) * radius
  }
}

function vectorStatus(entry) {
  if (entry.broken && entry.reversed) {
    return '断线优先'
  }
  if (entry.broken) {
    return '断线'
  }
  if (entry.reversed) {
    return '反接'
  }
  return ''
}
</script>
