<script setup lang="ts">
import { AlertTriangle } from 'lucide-vue-next'
import AppModal from './AppModal.vue'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <AppModal :open="open" :title="title" @close="emit('cancel')">
    <div class="flex gap-3">
      <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full" :class="danger ? 'bg-red-50 text-red-600' : 'bg-amber-50 text-amber-600'">
        <AlertTriangle :size="20" />
      </div>
      <p class="pt-1.5 text-sm leading-6 text-slate-600">{{ message }}</p>
    </div>
    <div class="mt-5 flex justify-end gap-2">
      <button type="button" class="btn-secondary" @click="emit('cancel')">Отмена</button>
      <button
        type="button"
        :class="danger ? 'btn-danger' : 'btn-primary'"
        @click="emit('confirm')"
      >
        {{ confirmLabel ?? 'Подтвердить' }}
      </button>
    </div>
  </AppModal>
</template>
