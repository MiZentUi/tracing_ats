<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowLeft, ShieldCheck } from '@lucide/vue'
import type { ResumeText } from '@/api/generated'
import { apiErrorMessage, resumesApi } from '@/api'

const props = defineProps<{ id: string }>()

const resume = ref<ResumeText | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const response = await resumesApi.getResumeText({ resumeId: props.id })
    resume.value = response.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Резюме</p>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">
          Обезличенный текст резюме
        </h1>
        <p class="mt-1 text-sm text-slate-500">
          {{ props.id.slice(0, 8) }}… · персональные данные заменены плейсхолдерами, оригинал не показывается
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <RouterLink :to="`/resumes/${props.id}`" class="btn-secondary">
          <ArrowLeft :size="16" />
          К подходящим вакансиям
        </RouterLink>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>

    <template v-else-if="resume">
      <div
        class="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600"
      >
        <ShieldCheck :size="14" />
        Слепой режим: персональные данные скрыты
      </div>

      <div
        v-if="resume.warnings?.length"
        class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
      >
        {{ resume.warnings.join(', ') }}
      </div>

      <pre
        class="whitespace-pre-wrap rounded-3xl border border-slate-200 bg-white p-6 font-mono text-sm leading-6 text-slate-800 shadow-sm"
      >{{ resume.maskedText }}</pre>
    </template>
  </section>
</template>
