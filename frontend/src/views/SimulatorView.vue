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
        <WiringDiagram :result="voltageResult" :current-result="currentResult" />
      </section>

      <aside class="results-column">
        <DiagnosisPanel :result="simulationResult" />
        <MatrixCard title="电压断相矩阵 D" :matrix="voltageResult?.D || identity" description="电压断线相被强制为 0" :highlight="hasVoltageBroken" />
        <MatrixCard title="电压符号矩阵 S" :matrix="voltageResult?.S || identity" description="电压反接相乘以 -1" :highlight="hasVoltageReversed" />
        <MatrixCard title="电压置换矩阵 P" :matrix="voltageResult?.P || identity" description="电压换相后通道读取顺序" :highlight="hasVoltageSwapped" />
        <MatrixCard title="电流总矩阵 M" :matrix="currentResult?.M || identity" description="电流独立故障矩阵" :highlight="hasCurrentFault" />
        <VectorCard title="理论向量 x" :entries="theoreticalEntries" />
        <VectorCard title="测量向量 y" :entries="measuredEntries" />
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
const voltageResult = ref(null)
const currentResult = ref(null)
const error = ref('')

const form = reactive(defaultForm())

const simulationResult = computed(() => {
  const voltage = voltageResult.value
  const current = currentResult.value
  if (!voltage && !current) return null
  const correctWiring = Boolean(voltage?.correctWiring && current?.correctWiring)
  return {
    ...voltage,
    correctWiring,
    diagnosis: [
      `电压：${voltage?.diagnosis || '暂无诊断'}`,
      `电流：${current?.diagnosis || '暂无诊断'}`
    ].join('；'),
    summary: correctWiring
      ? '当前电压、电流均为正确接线。'
      : '当前存在错接线，请查看电压/电流独立故障配置。'
  }
})
const hasVoltageBroken = computed(() => Object.values(form.voltage.broken).some(Boolean))
const hasVoltageReversed = computed(() => Object.values(form.voltage.reversed).some(Boolean))
const hasVoltageSwapped = computed(() => form.voltage.phaseOrder.join('') !== 'ABC')
const hasCurrentFault = computed(() => Object.values(form.current.broken).some(Boolean) || Object.values(form.current.reversed).some(Boolean) || form.current.phaseOrder.join('') !== 'ABC')
const theoreticalEntries = computed(() => mergeVectorEntries(voltageResult.value?.x, currentResult.value?.x, false))
const measuredEntries = computed(() => mergeVectorEntries(voltageResult.value?.y, currentResult.value?.y, true))

onMounted(runSimulation)

async function runSimulation() {
  error.value = ''
  try {
    const [voltage, current] = await Promise.all([
      simulateWiring(toPayload('voltage')),
      simulateWiring(toPayload('current'))
    ])
    voltageResult.value = voltage
    currentResult.value = current
  } catch (err) {
    error.value = err.message || '仿真失败，请检查后端服务是否启动。'
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
  runSimulation()
}

function randomizeFault() {
  resetFaultOnly()
  const target = Math.random() > 0.5 ? form.voltage : form.current
  const phases = ['A', 'B', 'C']
  const faultCount = 1 + Math.floor(Math.random() * 3)
  for (let i = 0; i < faultCount; i++) {
    const phase = phases[Math.floor(Math.random() * phases.length)]
    if (Math.random() > 0.5) {
      target.broken[phase] = true
    } else {
      target.reversed[phase] = true
    }
  }
  const orders = ['ABC', 'BAC', 'CBA', 'ACB', 'BCA', 'CAB']
  if (Math.random() > 0.45) {
    target.phaseOrder = orders[1 + Math.floor(Math.random() * (orders.length - 1))].split('')
  }
  runSimulation()
}

function resetFaultOnly() {
  form.voltage = faultDefaults()
  form.current = faultDefaults()
}

function defaultForm() {
  return {
    voltageAmplitude: 220,
    currentAmplitude: 5,
    voltage: faultDefaults(),
    current: faultDefaults(),
    noiseEnabled: false,
    amplitudeNoisePercent: 1,
    angleNoiseDegree: 0.5
  }
}

function faultDefaults() {
  return {
    broken: { A: false, B: false, C: false },
    reversed: { A: false, B: false, C: false },
    phaseOrder: ['A', 'B', 'C']
  }
}

function toPayload(type) {
  const fault = form[type]
  return {
    type,
    amplitude: type === 'voltage' ? form.voltageAmplitude : form.currentAmplitude,
    phaseAngles: type === 'voltage'
      ? { A: 0, B: -120, C: 120 }
      : { A: -30, B: -150, C: 90 },
    broken: { ...fault.broken },
    reversed: { ...fault.reversed },
    phaseOrder: [...fault.phaseOrder],
    noiseEnabled: form.noiseEnabled,
    amplitudeNoisePercent: form.amplitudeNoisePercent,
    angleNoiseDegree: form.angleNoiseDegree
  }
}

function mergeVectorEntries(voltageEntries = [], currentEntries = [], measured) {
  const voltage = voltageEntries.map((entry) => ({
    ...entry,
    phase: measured ? entry.phase : `U${entry.phase.toLowerCase()}`,
    channel: measured ? `${entry.channel}·U` : entry.channel
  }))
  const current = currentEntries.map((entry) => ({
    ...entry,
    phase: measured ? entry.phase : `I${entry.phase.toLowerCase()}`,
    channel: measured ? `${entry.channel}·I` : entry.channel
  }))
  return [...voltage, ...current]
}

function exportJson() {
  downloadFile('wrong-wiring-result.json', JSON.stringify({
    voltage: voltageResult.value || {},
    current: currentResult.value || {}
  }, null, 2), 'application/json')
}

function exportCsv() {
  const result = simulationResult.value
  if (!result) return
  const sections = [
    matrixCsv('Voltage D', voltageResult.value?.D || identity),
    matrixCsv('Voltage S', voltageResult.value?.S || identity),
    matrixCsv('Voltage P', voltageResult.value?.P || identity),
    matrixCsv('Current M', currentResult.value?.M || identity)
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
