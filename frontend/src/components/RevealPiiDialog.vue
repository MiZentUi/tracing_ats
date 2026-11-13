<script setup lang="ts">
import { ref, watch } from 'vue'
import { Loader2, LockKeyholeOpen } from '@lucide/vue'
import AppModal from './AppModal.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    submitting?: boolean
    title?: string
    description?: string
  }>(),
  {
    submitting: false,
    title: 'Показать персональные данные?',
    description: 'Укажите причину раскрытия.',
  },
)

const emit = defineEmits<{
  close: []
  confirm: [reason: string]
}>()

const reason = ref('')
const error = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      reason.value = ''
      error.value = ''
    }
  },
)

function close() {
  if (!props.submitting) {
    emit('close')
  }
}

function confirm() {
  const value = reason.value.trim()
  if (value.length < 3) {
    error.value = 'Укажите причину: минимум 3 символа'
    return
  }
  if (value.length > 512) {
    error.value = 'Причина должна быть не длиннее 512 символов'
    return
  }
  error.value = ''
  emit('confirm', value)
}
</script>

<template>
  <AppModal :open="open" :title="title" :description="description" @close="close">
    <form class="space-y-4" @submit.prevent="confirm">
      <div>
        <label class="label" for="reveal-reason">Причина раскрытия</label>
        <textarea
          id="reveal-reason"
          v-model="reason"
          class="textarea min-h-28 w-full"
          maxlength="512"
          placeholder="Например: проверка контактов перед интервью"
          :disabled="submitting"
          autofocus
        />
        <div class="mt-1 flex items-center justify-between gap-3">
          <p v-if="error" class="text-xs font-medium text-rose-700">{{ error }}</p>
          <span v-else />
          <span class="shrink-0 text-xs text-slate-400">{{ reason.length }} / 512</span>
        </div>
      </div>

      <div class="flex justify-end gap-2">
        <button type="button" class="btn-secondary" :disabled="submitting" @click="close">
          Отмена
        </button>
        <button type="submit" class="btn-primary" :disabled="submitting">
          <Loader2 v-if="submitting" :size="16" class="animate-spin" />
          <LockKeyholeOpen v-else :size="16" />
          {{ submitting ? 'Открываем…' : 'Показать данные' }}
        </button>
      </div>
    </form>
  </AppModal>
</template>
