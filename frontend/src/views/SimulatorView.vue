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

        <section class="identification-stage">
          <div class="stage-header compact">
            <div>
              <h2>识别验证 Top3</h2>
            </div>
          </div>

          <div class="scan-board" :class="{ active: scanState.running, complete: scanState.done }">
            <div class="scan-main">
              <div class="scan-title-row">
                <div>
                  <span class="mini-pill">候选扫描</span>
                  <strong>{{ scanState.done ? '排序完成' : '正在枚举矩阵组合' }}</strong>
                </div>
                <b>{{ scanState.index }} / {{ scanState.total }}</b>
              </div>
              <div class="scan-progress" role="progressbar" :aria-valuenow="scanPercent" aria-valuemin="0" aria-valuemax="100">
                <span :style="{ width: `${scanPercent}%` }"></span>
              </div>
              <div class="scan-metrics">
                <div>
                  <small>D 断相</small>
                  <code>{{ scanState.dMask }}</code>
                </div>
                <div>
                  <small>S 极性</small>
                  <code>{{ scanState.sMask }}</code>
                </div>
                <div>
                  <small>P 换相</small>
                  <code>{{ scanState.pOrder }}</code>
                </div>
                <div>
                  <small>当前距离</small>
                  <strong>{{ formatNumber(scanState.distance) }}</strong>
                </div>
              </div>
            </div>

            <div class="scan-pipeline">
              <div v-for="step in scanSteps" :key="step.key" class="scan-step" :class="{ active: step.active, done: step.done }">
                <span></span>
                <small>{{ step.label }}</small>
              </div>
            </div>
          </div>

          <div class="match-grid compare-grid">
            <article v-for="section in matchSections" :key="section.key" class="match-panel">
              <div class="match-panel-header">
                <div>
                  <h3>{{ section.title }}</h3>
                  <p>候选数量 {{ section.candidateCount || 0 }}</p>
                </div>
                <span class="match-status" :class="section.statusClass">{{ section.status }}</span>
              </div>

              <div v-if="section.topMatch" class="match-compare-body diff-mode">
                <div class="field-vector-strip">
                  <div>
                    <span class="mini-pill">现场值 y</span>
                    <strong v-for="entry in section.measuredVector" :key="`${section.key}-field-${entry.channel}`">
                      {{ phaseLabel(entry, section.key) }} {{ entry.polar }}
                    </strong>
                  </div>
                  <p v-if="section.tiedCount > 1">
                    有 {{ section.tiedCount }} 个候选生成了相同或近似相同的 ŷ，属于物理不可区分结果。
                  </p>
                </div>

                <div class="diff-list">
                  <article
                    v-for="match in section.displayMatches"
                    :key="`${section.key}-diff-${match.rank}`"
                    class="diff-card"
                    :class="{ winner: match.rank === 1, hit: match.trueFaultMatch }"
                  >
                    <div class="diff-rank">#{{ match.rank }}</div>
                    <div class="diff-main">
                      <div class="diff-title-row">
                        <strong :title="match.fault?.description || match.explanation">
                          {{ shortFaultDescription(match.fault?.description || match.explanation) }}
                        </strong>
                        <span class="tag compact" :class="match.trueFaultMatch ? 'hit' : 'muted'">
                          {{ match.trueFaultMatch ? '真实故障' : '候选' }}
                        </span>
                      </div>
                      <div class="phase-diff-grid">
                        <div v-for="phase in match.phaseDiffs" :key="`${section.key}-${match.rank}-${phase.phase}`" class="phase-diff" :class="phase.level">
                          <b>{{ phase.phase }} 相</b>
                          <strong>{{ phase.status }}</strong>
                          <small>{{ phase.detail }}</small>
                        </div>
                      </div>
                    </div>
                    <CandidatePhasorMini
                      :actual="section.measuredVector"
                      :candidate="match.predictedVector || []"
                      :prefix="section.key === 'voltage' ? 'U' : 'I'"
                    />
                    <div class="diff-score">
                      <b>{{ percent(match.similarity) }}</b>
                      <small>{{ formatNumber(match.distance) }}</small>
                    </div>
                  </article>
                </div>
              </div>
              <div v-else class="candidate-empty">等待仿真结果</div>
            </article>
          </div>
        </section>
      </section>

      <aside class="results-column">
        <DiagnosisPanel :result="simulationResult" />
        <MatrixCard title="电压断相矩阵 D" :matrix="voltageResult?.D || identity" :highlight="hasVoltageBroken" />
        <MatrixCard title="电压极性矩阵 S" :matrix="voltageResult?.S || identity" :highlight="hasVoltageReversed" />
        <MatrixCard title="电压置换矩阵 P" :matrix="voltageResult?.P || identity" :highlight="hasVoltageSwapped" />
        <MatrixCard title="电压总矩阵 M" :matrix="voltageResult?.M || identity" :highlight="hasVoltageFault" />
        <MatrixCard title="电流总矩阵 M" :matrix="currentResult?.M || identity" :highlight="hasCurrentFault" />
        <VectorCard title="理论正确向量 x" :entries="theoreticalEntries" />
        <VectorCard title="理论错误向量 y0" :entries="y0Entries" />
        <VectorCard title="仿真现场值 y" :entries="measuredEntries" />
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { simulateWiring } from '../api/simulation'
import DiagnosisPanel from '../components/DiagnosisPanel.vue'
import CandidatePhasorMini from '../components/CandidatePhasorMini.vue'
import FaultPanel from '../components/FaultPanel.vue'
import HeaderBar from '../components/HeaderBar.vue'
import MatrixCard from '../components/MatrixCard.vue'
import VectorCard from '../components/VectorCard.vue'
import WiringDiagram from '../components/WiringDiagram.vue'

const identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
const voltageResult = ref(null)
const currentResult = ref(null)
const error = ref('')
const scanTimer = ref(null)
const requestSerial = ref(0)

const form = reactive(defaultForm())
const scanState = reactive({
  running: false,
  done: false,
  index: 0,
  total: 384,
  dMask: '000',
  sMask: '000',
  pOrder: 'ABC',
  distance: 0
})

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
      : '当前存在错接线，请查看电压/电流独立故障配置与 Top3 识别结果。'
  }
})
const hasVoltageBroken = computed(() => Object.values(form.voltage.broken).some(Boolean))
const hasVoltageReversed = computed(() => Object.values(form.voltage.reversed).some(Boolean))
const hasVoltageSwapped = computed(() => form.voltage.phaseOrder.join('') !== 'ABC')
const hasVoltageFault = computed(() => hasVoltageBroken.value || hasVoltageReversed.value || hasVoltageSwapped.value)
const hasCurrentFault = computed(() => Object.values(form.current.broken).some(Boolean) || Object.values(form.current.reversed).some(Boolean) || form.current.phaseOrder.join('') !== 'ABC')
const theoreticalEntries = computed(() => mergeVectorEntries(voltageResult.value?.x, currentResult.value?.x, false))
const y0Entries = computed(() => mergeVectorEntries(voltageResult.value?.y0, currentResult.value?.y0, true))
const measuredEntries = computed(() => mergeVectorEntries(voltageResult.value?.y, currentResult.value?.y, true))
const matchSections = computed(() => [
  buildMatchSection('voltage', '电压识别', voltageResult.value),
  buildMatchSection('current', '电流识别', currentResult.value)
])
const scanPercent = computed(() => {
  if (!scanState.total) return 0
  return Math.min(100, Math.round(scanState.index / scanState.total * 100))
})
const scanSteps = computed(() => {
  const percent = scanPercent.value
  return [
    { key: 'enum', label: '枚举 384', active: percent < 35, done: percent >= 35 },
    { key: 'vector', label: '生成 ŷ_i', active: percent >= 35 && percent < 62, done: percent >= 62 },
    { key: 'compare', label: '比较 y', active: percent >= 62 && percent < 86, done: percent >= 86 },
    { key: 'rank', label: '排序 Top3', active: percent >= 86, done: scanState.done }
  ]
})

onMounted(runSimulation)
onUnmounted(clearScanTimer)

async function runSimulation() {
  const serial = requestSerial.value + 1
  requestSerial.value = serial
  error.value = ''
  startScan()
  try {
    const [voltage, current] = await Promise.all([
      simulateWiring(toPayload('voltage')),
      simulateWiring(toPayload('current'))
    ])
    if (serial !== requestSerial.value) return
    await finishScan(voltage, current)
    if (serial !== requestSerial.value) return
    voltageResult.value = voltage
    currentResult.value = current
  } catch (err) {
    if (serial !== requestSerial.value) return
    stopScan()
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
    channel: measured ? `${entry.channel} 路 U` : entry.channel
  }))
  const current = currentEntries.map((entry) => ({
    ...entry,
    phase: measured ? entry.phase : `I${entry.phase.toLowerCase()}`,
    channel: measured ? `${entry.channel} 路 I` : entry.channel
  }))
  return [...voltage, ...current]
}

function buildMatchSection(key, title, result) {
  const matches = result?.topMatches || []
  const topMatch = matches[0] || null
  const top1Hit = Boolean(matches[0]?.trueFaultMatch)
  const top3Hit = matches.some((match) => match.trueFaultMatch)
  const measuredVector = result?.y || []
  const topDistance = Number(topMatch?.distance ?? Number.NaN)
  const tiedCount = Number.isFinite(topDistance)
    ? matches.filter((match) => Math.abs(Number(match.distance || 0) - topDistance) < 0.000001).length
    : 0
  const displayMatches = matches.slice(0, 3).map((match) => ({
    ...match,
    phaseDiffs: buildPhaseDiffs(measuredVector, match.predictedVector || [])
  }))

  return {
    key,
    title,
    matches,
    topMatch,
    displayMatches,
    tiedCount,
    measuredVector,
    candidateCount: result?.candidateCount,
    status: top1Hit ? '识别成功' : top3Hit ? 'Top3 命中' : result ? '未命中' : '等待',
    statusClass: top1Hit ? 'success' : top3Hit ? 'warning' : result ? 'miss' : 'pending'
  }
}

function buildPhaseDiffs(measured = [], predicted = []) {
  return [0, 1, 2].map((index) => {
    const actual = measured[index] || {}
    const candidate = predicted[index] || {}
    const amplitudeDiff = Math.abs(Number(actual.amplitude || 0) - Number(candidate.amplitude || 0))
    const bothZero = isZeroVector(actual) && isZeroVector(candidate)
    const oneZero = isZeroVector(actual) !== isZeroVector(candidate)
    const angleDiffValue = bothZero || oneZero
      ? 0
      : Math.abs(angleDiff(Number(actual.angle || 0), Number(candidate.angle || 0)))
    const phase = ['A', 'B', 'C'][index] || `${index + 1}`

    if (bothZero) {
      return {
        phase,
        level: 'ok',
        status: '同为断相',
        detail: '现场 0 / 候选 0'
      }
    }

    if (oneZero) {
      return {
        phase,
        level: 'bad',
        status: '断相不符',
        detail: `${actual.polar || '0'} vs ${candidate.polar || '0'}`
      }
    }

    if (amplitudeDiff < 0.01 && angleDiffValue < 0.5) {
      return {
        phase,
        level: 'ok',
        status: '完全一致',
        detail: actual.polar || candidate.polar || '-'
      }
    }

    if (angleDiffValue >= 60) {
      return {
        phase,
        level: 'bad',
        status: '相角不符',
        detail: `偏差 ${formatNumber(angleDiffValue)}°`
      }
    }

    return {
      phase,
      level: amplitudeDiff >= 1 || angleDiffValue >= 5 ? 'warn' : 'ok',
      status: amplitudeDiff >= 1 || angleDiffValue >= 5 ? '轻微偏差' : '近似一致',
      detail: `幅差 ${formatNumber(amplitudeDiff)} / 角差 ${formatNumber(angleDiffValue)}°`
    }
  })
}

function phaseLabel(entry, key) {
  const prefix = key === 'voltage' ? 'U' : 'I'
  const source = String(entry?.source || '').toLowerCase()
  return `${prefix}${source || '?'}`
}

function isZeroVector(entry) {
  return Number(entry?.amplitude || 0) < 0.01
}

function angleDiff(a, b) {
  let diff = (a - b) % 360
  if (diff > 180) diff -= 360
  if (diff <= -180) diff += 360
  return diff
}

function startScan() {
  clearScanTimer()
  Object.assign(scanState, {
    running: true,
    done: false,
    index: 0,
    total: 384,
    dMask: '000',
    sMask: '000',
    pOrder: 'ABC',
    distance: 1.2
  })
}

function finishScan(voltage, current) {
  clearScanTimer()
  const total = Math.max(voltage?.candidateCount || 384, current?.candidateCount || 384)
  const finalDistance = Math.min(
    voltage?.topMatches?.[0]?.distance ?? 0,
    current?.topMatches?.[0]?.distance ?? 0
  )
  const startedAt = performance.now()
  const duration = 1050

  return new Promise((resolve) => {
    scanTimer.value = window.setInterval(() => {
      const elapsed = performance.now() - startedAt
      const progress = Math.min(1, elapsed / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      const index = Math.max(1, Math.round(total * eased))
      const wobble = Math.sin(index * 0.31) * 0.035
      const distance = Math.max(finalDistance, (1 - eased) * 1.15 + finalDistance + wobble)
      updateScanFrame(index, total, distance)

      if (progress >= 1) {
        clearScanTimer()
        updateScanFrame(total, total, finalDistance)
        scanState.running = false
        scanState.done = true
        resolve()
      }
    }, 24)
  })
}

function stopScan() {
  clearScanTimer()
  scanState.running = false
}

function clearScanTimer() {
  if (scanTimer.value) {
    window.clearInterval(scanTimer.value)
    scanTimer.value = null
  }
}

function updateScanFrame(index, total, distance) {
  const orders = ['ABC', 'ACB', 'BAC', 'BCA', 'CAB', 'CBA']
  scanState.index = index
  scanState.total = total
  scanState.dMask = maskLabel(index % 8)
  scanState.sMask = maskLabel(Math.floor(index / 8) % 8)
  scanState.pOrder = orders[Math.floor(index / 64) % orders.length]
  scanState.distance = distance
}

function maskLabel(value) {
  return value.toString(2).padStart(3, '0')
}

function vectorSummary(entries = []) {
  return entries.map((entry, index) => `${index + 1}: ${entry.polar}`).join('；')
}

function shortFaultDescription(description = '') {
  return description
    .replace(/通道1读取([ABC])相，通道2读取([ABC])相，通道3读取([ABC])相/g, '换相：1←$1，2←$2，3←$3')
    .replace(/，不换相/g, '，不换相')
}

function formatNumber(value) {
  return Number(value || 0).toFixed(3)
}

function percent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function similarityPercent(value) {
  return Math.max(0, Math.min(100, Math.round(Number(value || 0) * 100)))
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
    matrixCsv('Voltage M', voltageResult.value?.M || identity),
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
