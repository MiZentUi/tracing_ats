<script setup lang="ts">
import { Quote } from '@lucide/vue'
import type { EditableRequirement } from '@/types'

defineProps<{ requirement: EditableRequirement }>()
</script>

<template>
  <div v-if="requirement.quote || requirement.clarityWarning" class="space-y-2">
    <blockquote
      v-if="requirement.quote"
      class="flex gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm leading-6 text-slate-600"
    >
      <Quote :size="14" class="mt-1 shrink-0 text-slate-400" />
      <span>{{ requirement.quote }}</span>
    </blockquote>

    <p
      v-if="requirement.clarityWarning"
      class="rounded-lg bg-amber-50 px-3 py-2 text-sm leading-6 text-amber-800"
    >
      {{ requirement.clarityWarning }}
    </p>
  </div>
</template>
