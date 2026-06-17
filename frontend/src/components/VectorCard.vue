<template>
  <article class="vector-card">
    <div class="card-title-row">
      <h3>{{ title }}</h3>
      <span class="mini-pill">{{ entries.length }} 项</span>
    </div>
    <div class="vector-list">
      <div v-for="entry in entries" :key="entry.phase || entry.channel" class="vector-row">
        <div class="vector-name">
          <span>{{ entry.phase || entry.channel }}</span>
          <small v-if="entry.source">源 {{ entry.source }}</small>
        </div>
        <div class="vector-value">
          <div class="polar-line">
            <strong>{{ entry.polar }}</strong>
            <span v-if="statusText(entry)" class="tag compact" :class="statusClass(entry)">
              {{ statusText(entry) }}
            </span>
          </div>
          <small>{{ entry.complex }}</small>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  entries: { type: Array, default: () => [] }
})

function statusText(entry) {
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

function statusClass(entry) {
  if (entry.broken && entry.reversed) {
    return 'muted'
  }
  if (entry.broken) {
    return 'broken'
  }
  if (entry.reversed) {
    return 'reversed'
  }
  return ''
}
</script>
