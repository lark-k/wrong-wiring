<template>
  <section class="visual-stage">
    <div class="stage-header">
      <div>
        <h2>端子排错接线对比图</h2>
        <p>上排为正确端子顺序，下排直接显示当前接入后的端子位置变化</p>
      </div>
      <div class="legend">
        <span><i class="voltage"></i>电压</span>
        <span><i class="current"></i>电流</span>
        <span><i class="red"></i>故障</span>
        <span><i class="dash"></i>断线</span>
      </div>
    </div>

    <svg class="wiring-svg terminal-svg" viewBox="0 0 860 300" role="img" aria-label="端子排错接线对比图">
      <g class="terminal-row correct-row">
        <text class="row-title" x="34" y="40">正确情况</text>
        <rect x="20" y="56" width="820" height="86" rx="10" />
        <g v-for="terminal in terminalNodes" :key="`correct-${terminal.key}`">
          <circle class="terminal-dot" :class="terminal.kind" :cx="terminal.x" cy="92" r="18" />
          <text class="terminal-label" :x="terminal.x" y="132">{{ terminal.label }}</text>
        </g>
      </g>

      <g class="terminal-row fault-row">
        <text class="row-title" x="34" y="180">错误情况</text>
        <rect x="20" y="196" width="820" height="86" rx="10" />
        <g v-for="terminal in faultTerminalNodes" :key="`fault-${terminal.slot}`">
          <circle
            class="terminal-dot"
            :class="[terminal.kind, { fault: terminal.fault, broken: terminal.broken }]"
            :cx="terminal.x"
            cy="232"
            r="18"
            @mouseenter="hovered = terminal"
            @mouseleave="hovered = null"
          />
          <text class="terminal-label" :class="{ fault: terminal.fault }" :x="terminal.x" y="272">{{ terminal.label }}</text>
          <text v-if="terminal.badge" class="terminal-badge" :x="terminal.x" y="192">{{ terminal.badge }}</text>
        </g>
      </g>

      <foreignObject v-if="hovered" :x="hovered.midX - 92" :y="hovered.midY - 34" width="204" height="66">
        <div class="svg-tooltip">
          <strong>{{ hovered.tooltipTitle }}</strong>
          <span>{{ hovered.status }}</span>
        </div>
      </foreignObject>
    </svg>
  </section>

  <section class="phasor-stage">
    <div class="stage-header compact">
      <div>
        <h2>三相相量坐标图</h2>
        <p>左侧为正确三相图，右侧为错接线后的测量三相图</p>
      </div>
      <div class="legend">
        <span><i class="voltage"></i>电压</span>
        <span><i class="current"></i>电流</span>
      </div>
    </div>

    <svg class="phasor-svg dual-phasor-svg" viewBox="0 0 860 380" role="img" aria-label="正确与错误三相相量对比图">
      <defs>
        <marker id="arrow-voltage" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="#3f6fc7" />
        </marker>
        <marker id="arrow-current" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="#f59e0b" />
        </marker>
      </defs>

      <g v-for="chart in phasorCharts" :key="chart.key" class="phasor-chart">
        <text class="chart-title" :x="chart.cx" y="34">{{ chart.title }}</text>
        <circle v-for="r in [44, 74, 104]" :key="`${chart.key}-${r}`" class="grid-ring" :cx="chart.cx" :cy="chart.cy" :r="r" />
        <line class="grid-axis" :x1="chart.cx - 132" :y1="chart.cy" :x2="chart.cx + 132" :y2="chart.cy" />
        <line class="grid-axis" :x1="chart.cx" :y1="chart.cy - 122" :x2="chart.cx" :y2="chart.cy + 122" />
        <line
          v-for="angle in angleTicks"
          :key="`${chart.key}-axis-${angle}`"
          class="grid-spoke"
          :x1="axisPoint(chart, angle, 122).x"
          :y1="axisPoint(chart, angle, 122).y"
          :x2="axisPoint(chart, angle, -122).x"
          :y2="axisPoint(chart, angle, -122).y"
        />
        <text class="angle-label" :x="chart.cx + 142" :y="chart.cy + 5">0°</text>
        <text class="angle-label" :x="chart.cx - 118" :y="chart.cy - 92">120°</text>
        <text class="angle-label" :x="chart.cx - 125" :y="chart.cy + 108">-120°</text>

        <g v-for="vector in chart.vectors" :key="vector.key">
          <line
            class="phasor-line"
            :class="[vector.kind, { zero: vector.zero, broken: vector.broken, reversed: vector.reversed }]"
            :x1="chart.cx"
            :y1="chart.cy"
            :x2="vector.x"
            :y2="vector.y"
            :marker-end="vector.kind === 'current' ? 'url(#arrow-current)' : 'url(#arrow-voltage)'"
          />
          <circle v-if="vector.zero" class="origin-dot" :cx="chart.cx" :cy="chart.cy" r="5" />
          <text class="phasor-label" :class="vector.kind" :x="vector.labelX" :y="vector.labelY">{{ vector.name }}</text>
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
  },
  currentResult: {
    type: Object,
    default: null
  }
})

const hovered = ref(null)
const phases = ['A', 'B', 'C']
const angleTicks = [30, 60, 120, 150]
const terminalNodes = [
  { key: 'Ia', label: 'Ia', phase: 'A', slotType: 'current', kind: 'current', x: 58 },
  { key: 'Ua', label: 'Ua', phase: 'A', slotType: 'voltage', kind: 'voltage', x: 140 },
  { key: '-Ia', label: '-Ia', phase: 'A', slotType: 'reverse-current', kind: 'reverse-current', x: 222 },
  { key: 'Ib', label: 'Ib', phase: 'B', slotType: 'current', kind: 'current', x: 304 },
  { key: 'Ub', label: 'Ub', phase: 'B', slotType: 'voltage', kind: 'voltage', x: 386 },
  { key: '-Ib', label: '-Ib', phase: 'B', slotType: 'reverse-current', kind: 'reverse-current', x: 468 },
  { key: 'Ic', label: 'Ic', phase: 'C', slotType: 'current', kind: 'current', x: 550 },
  { key: 'Uc', label: 'Uc', phase: 'C', slotType: 'voltage', kind: 'voltage', x: 632 },
  { key: '-Ic', label: '-Ic', phase: 'C', slotType: 'reverse-current', kind: 'reverse-current', x: 714 },
  { key: 'N', label: 'N', phase: 'N', slotType: 'neutral', kind: 'neutral', x: 800 }
]
const defaultConnections = [
  { channel: '通道1', source: 'A', broken: false, reversed: false, swapped: false, status: '正常' },
  { channel: '通道2', source: 'B', broken: false, reversed: false, swapped: false, status: '正常' },
  { channel: '通道3', source: 'C', broken: false, reversed: false, swapped: false, status: '正常' }
]

const connections = computed(() => normalizeConnections(props.result?.connections))
const faultTerminalNodes = computed(() => terminalNodes.map((terminal) => {
  if (terminal.phase === 'N') {
    return terminalViewModel(terminal, 'N', false, false, '中性线', '', 'N 端子')
  }

  const slotIndex = phases.indexOf(terminal.phase)
  const connection = connections.value[slotIndex] || defaultConnections[slotIndex]
  const source = connection.source
  const label = labelForSlot(terminal.slotType, source, connection.reversed)
  const broken = connection.broken
  const reversed = connection.reversed && terminal.slotType === 'current'
  const swapped = connection.swapped
  const fault = broken || reversed || swapped
  const badge = broken ? '断线' : reversed ? '反接' : swapped ? '换相' : ''
  const statusParts = []
  if (swapped) statusParts.push(`${terminal.phase} 位接入 ${source} 相`)
  if (reversed) statusParts.push(`${source} 相电流反接`)
  if (broken) statusParts.push(`${source} 相断线`)

  return terminalViewModel(
    terminal,
    label,
    fault,
    broken,
    statusParts.length ? statusParts.join('，') : '正常',
    badge,
    `${terminal.label} 位置当前为 ${label}`
  )
}))

const phasorCharts = computed(() => [
  {
    key: 'correct',
    title: '正确三相图',
    cx: 220,
    cy: 205,
    vectors: [
      ...buildPhasors(props.result?.x || [], 'U', 'voltage', 220, 205, true),
      ...buildPhasors(props.currentResult?.x || [], 'I', 'current', 220, 205, true)
    ]
  },
  {
    key: 'fault',
    title: '错接线测量图',
    cx: 640,
    cy: 205,
    vectors: [
      ...buildPhasors(props.result?.y || [], 'U', 'voltage', 640, 205, false),
      ...buildPhasors(props.currentResult?.y || [], 'I', 'current', 640, 205, false)
    ]
  }
])

function normalizeConnections(rawConnections) {
  if (!Array.isArray(rawConnections) || rawConnections.length !== 3) {
    return defaultConnections
  }
  return rawConnections.map((connection, index) => ({
    channel: connection.channel || `通道${index + 1}`,
    source: connection.source || phases[index],
    broken: Boolean(connection.broken),
    reversed: Boolean(connection.reversed),
    swapped: Boolean(connection.swapped),
    status: connection.status || '正常'
  }))
}

function labelForSlot(slotType, source, reversed) {
  const phase = source.toLowerCase()
  if (slotType === 'voltage') {
    return `U${phase}`
  }
  if (slotType === 'current') {
    return reversed ? `-I${phase}` : `I${phase}`
  }
  if (slotType === 'reverse-current') {
    return reversed ? `I${phase}` : `-I${phase}`
  }
  return 'N'
}

function terminalViewModel(terminal, label, fault, broken, status, badge = '', tooltipTitle = label) {
  return {
    ...terminal,
    slot: terminal.key,
    label,
    fault,
    broken,
    status,
    badge,
    tooltipTitle,
    midX: terminal.x,
    midY: 216
  }
}

function buildPhasors(entries, prefix, kind, cx, cy, theoretical) {
  const maxAmp = Math.max(...entries.map((entry) => entry.amplitude || 0), 1)
  const maxRadius = kind === 'voltage' ? 104 : 72
  return entries.map((entry, index) => {
    const name = theoretical
      ? `${prefix}${entry.phase.toLowerCase()}`
      : `${prefix}${index + 1}`
    const length = (entry.amplitude || 0) / maxAmp * maxRadius
    const radians = (entry.angle || 0) * Math.PI / 180
    const x = cx + Math.cos(radians) * length
    const y = cy - Math.sin(radians) * length
    return {
      key: `${kind}-${name}`,
      name,
      kind,
      x,
      y,
      labelX: x + 10,
      labelY: y - 8,
      zero: (entry.amplitude || 0) < 0.01,
      broken: entry.broken,
      reversed: entry.reversed
    }
  })
}

function axisPoint(chart, angle, radius) {
  const radians = angle * Math.PI / 180
  return {
    x: chart.cx + Math.cos(radians) * radius,
    y: chart.cy - Math.sin(radians) * radius
  }
}
</script>
