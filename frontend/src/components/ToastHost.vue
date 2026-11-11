<script setup lang="ts">
import { AlertCircle, CheckCircle2, Info, X } from '@lucide/vue'
import { useToast } from '@/composables/useToast'

const { toasts, remove } = useToast()

const styles = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  error: 'border-red-200 bg-red-50 text-red-900',
  info: 'border-indigo-200 bg-indigo-50 text-indigo-900',
}

const icons = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
}
</script>

<template>
  <div class="pointer-events-none fixed right-4 top-4 z-[100] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-start gap-3 rounded-2xl border p-3.5 shadow-lg backdrop-blur"
        :class="styles[toast.type]"
        role="status"
      >
        <component :is="icons[toast.type]" :size="18" class="mt-0.5 shrink-0" />
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold">{{ toast.title }}</p>
          <p v-if="toast.message" class="mt-0.5 text-xs opacity-80">{{ toast.message }}</p>
        </div>
        <button class="rounded-lg p-1 opacity-60 transition hover:bg-black/5 hover:opacity-100" @click="remove(toast.id)">
          <X :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
