<template>
  <div class="status-badge" :class="{ ok: correct, fault: !correct }">
    <span class="status-dot"></span>
    <span>{{ correct ? '正确接线' : '存在错接线' }}</span>
  </div>
</template>

<script setup>
defineProps({
  correct: {
    type: Boolean,
    default: true
  }
})
</script>
