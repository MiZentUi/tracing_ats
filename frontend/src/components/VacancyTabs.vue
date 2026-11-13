<script setup lang="ts">
import { ListChecks, Users } from '@lucide/vue'

defineProps<{
  vacancyId: string
  active: 'requirements' | 'candidates'
}>()
</script>

<template>
  <nav class="inline-flex rounded-2xl border border-slate-200 bg-white p-1" aria-label="Разделы вакансии">
    <RouterLink
      :to="`/vacancies/${vacancyId}`"
      class="inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition"
      :class="active === 'requirements' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'"
    >
      <ListChecks :size="16" />
      Требования
    </RouterLink>
    <RouterLink
      :to="`/vacancies/${vacancyId}/candidates`"
      class="inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition"
      :class="active === 'candidates' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'"
    >
      <Users :size="16" />
      Кандидаты
    </RouterLink>
  </nav>
</template>
