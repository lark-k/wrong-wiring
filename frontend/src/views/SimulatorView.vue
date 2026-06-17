<template>
  <main class="app-shell">
    <HeaderBar
      :correct="simulationResult?.correctWiring"
      @export-json="exportJson"
      @export-csv="exportCsv"
    />

    <div class="dashboard-grid">
      <aside class="fault-column">
        <FaultPanel
          v-model="form"
          @simulate="runSimulation"
          @reset="resetForm"
          @randomize="randomizeFault"
        />
      </aside>

      <section class="center-column">
        <div v-if="error" class="error-banner">{{ error }}</div>
        <WiringDiagram :result="simulationResult" />
      </section>

      <aside class="results-column">
        <DiagnosisPanel :result="simulationResult" />
        <MatrixCard title="断相矩阵 D" :matrix="simulationResult?.D || identity" description="断线相被强制为 0" :highlight="hasBroken" />
        <MatrixCard title="符号矩阵 S" :matrix="simulationResult?.S || identity" description="反接相乘以 -1" :highlight="hasReversed" />
        <MatrixCard title="置换矩阵 P" :matrix="simulationResult?.P || identity" description="换相后通道读取顺序" :highlight="hasSwapped" />
        <MatrixCard title="总矩阵 M" :matrix="simulationResult?.M || identity" description="M = P · S · D" highlight />
        <VectorCard title="理论向量 x" :entries="simulationResult?.x || []" />
        <VectorCard title="测量向量 y" :entries="simulationResult?.y || []" />
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { simulateWiring } from '../api/simulation'
import DiagnosisPanel from '../components/DiagnosisPanel.vue'
import FaultPanel from '../components/FaultPanel.vue'
import HeaderBar from '../components/HeaderBar.vue'
import MatrixCard from '../components/MatrixCard.vue'
import VectorCard from '../components/VectorCard.vue'
import WiringDiagram from '../components/WiringDiagram.vue'

const identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
const simulationResult = ref(null)
const error = ref('')

const form = reactive(defaultForm())

const hasBroken = computed(() => Object.values(form.broken).some(Boolean))
const hasReversed = computed(() => Object.values(form.reversed).some(Boolean))
const hasSwapped = computed(() => form.phaseOrder.join('') !== 'ABC')

onMounted(runSimulation)

async function runSimulation() {
  error.value = ''
  try {
    simulationResult.value = await simulateWiring(toPayload())
  } catch (err) {
    error.value = err.message || '仿真失败，请检查后端服务是否启动。'
  }
}

function resetForm() {
  Object.assign(form, defaultForm(form.type))
  runSimulation()
}

function randomizeFault() {
  resetFaultOnly()
  const phases = ['A', 'B', 'C']
  const faultCount = 1 + Math.floor(Math.random() * 3)
  for (let i = 0; i < faultCount; i++) {
    const phase = phases[Math.floor(Math.random() * phases.length)]
    if (Math.random() > 0.5) {
      form.broken[phase] = true
    } else {
      form.reversed[phase] = true
    }
  }
  const orders = ['ABC', 'BAC', 'CBA', 'ACB', 'BCA', 'CAB']
  if (Math.random() > 0.45) {
    form.phaseOrder = orders[1 + Math.floor(Math.random() * (orders.length - 1))].split('')
  }
  runSimulation()
}

function resetFaultOnly() {
  form.broken = { A: false, B: false, C: false }
  form.reversed = { A: false, B: false, C: false }
  form.phaseOrder = ['A', 'B', 'C']
}

function defaultForm(type = 'voltage') {
  return {
    type,
    amplitude: type === 'voltage' ? 220 : 5,
    phaseAngles: type === 'voltage'
      ? { A: 0, B: -120, C: 120 }
      : { A: -30, B: -150, C: 90 },
    broken: { A: false, B: false, C: false },
    reversed: { A: false, B: false, C: false },
    phaseOrder: ['A', 'B', 'C'],
    noiseEnabled: false,
    amplitudeNoisePercent: 1,
    angleNoiseDegree: 0.5
  }
}

function toPayload() {
  return JSON.parse(JSON.stringify(form))
}

function exportJson() {
  downloadFile('wrong-wiring-result.json', JSON.stringify(simulationResult.value || {}, null, 2), 'application/json')
}

function exportCsv() {
  const result = simulationResult.value
  if (!result) return
  const sections = [
    matrixCsv('D', result.D),
    matrixCsv('S', result.S),
    matrixCsv('P', result.P),
    matrixCsv('M', result.M)
  ]
  downloadFile('wrong-wiring-matrix.csv', sections.join('\n\n'), 'text/csv;charset=utf-8')
}

function matrixCsv(name, matrix) {
  return [`${name}`, ...matrix.map((row) => row.join(','))].join('\n')
}

function downloadFile(filename, content, type) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
</script>
