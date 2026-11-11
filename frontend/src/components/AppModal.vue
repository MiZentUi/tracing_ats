<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { X } from '@lucide/vue'

const props = defineProps<{
  open: boolean
  title: string
  description?: string
  width?: 'md' | 'lg'
}>()

const emit = defineEmits<{ close: [] }>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) {
    emit('close')
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-slate-950/35 backdrop-blur-sm" @click="emit('close')" />
        <div
          class="relative w-full overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
          :class="width === 'lg' ? 'max-w-2xl' : 'max-w-lg'"
          role="dialog"
          aria-modal="true"
        >
          <div class="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
            <div>
              <h2 class="text-lg font-semibold text-slate-900">{{ title }}</h2>
              <p v-if="description" class="mt-1 text-sm text-slate-500">{{ description }}</p>
            </div>
            <button class="icon-btn -mr-1 -mt-1" aria-label="Закрыть" @click="emit('close')">
              <X :size="18" />
            </button>
          </div>
          <div class="px-5 py-4">
            <slot />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
