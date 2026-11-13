<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, FileText } from '@lucide/vue'
import { useRouter } from 'vue-router'
import type { ResumeBatchUploadResponse } from '@/api/generated'
import ResumeUploader from '@/components/ResumeUploader.vue'
import { formatBytes } from '@/lib/format'

const router = useRouter()

const selection = ref<{ files: File[]; textLength: number }>({ files: [], textLength: 0 })

const previewTitle = computed(() => {
  if (selection.value.files.length === 1) {
    return selection.value.files[0].name.replace(/\.[^.]+$/, '')
  }
  if (selection.value.files.length > 1) {
    return `${selection.value.files.length} файла`
  }
  if (selection.value.textLength > 0) {
    return 'Резюме из вставленного текста'
  }
  return 'Новое резюме'
})

const previewMeta = computed(() => {
  if (selection.value.files.length > 0) {
    const total = selection.value.files.reduce((sum, file) => sum + file.size, 0)
    return `${selection.value.files.length} файл(а) · ${formatBytes(total)}`
  }
  if (selection.value.textLength > 0) {
    return `${selection.value.textLength.toLocaleString('ru-RU')} символов текста`
  }
  return ''
})

async function onUploaded(response: ResumeBatchUploadResponse) {
  const uploaded = response.resumes.filter((item) => item.resumeId)
  if (uploaded.length === 1 && uploaded[0].resumeId) {
    await router.push(`/resumes/${uploaded[0].resumeId}`)
    return
  }
  await router.push('/resumes')
}
</script>

<template>
  <section class="space-y-5">
    <div>
      <RouterLink
        to="/resumes"
        class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900"
      >
        <ArrowLeft :size="16" />
        К списку резюме
      </RouterLink>
    </div>

    <header>
      <p class="section-label">Кандидаты</p>
      <h1 class="mt-1 text-3xl font-semibold tracking-tight text-slate-900">Добавить резюме</h1>
      <p class="mt-1 text-sm text-slate-500">
        Можно добавить один файл и текст. Если заполнены оба источника, появятся два резюме.
      </p>
    </header>

    <article class="card p-5">
      <div class="flex items-start gap-4">
        <span class="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-indigo-50 text-indigo-600">
          <FileText :size="21" />
        </span>
        <div class="min-w-0">
          <h2 class="truncate text-lg font-semibold text-slate-900">{{ previewTitle }}</h2>
          <p v-if="previewMeta" class="mt-0.5 text-sm text-slate-500">{{ previewMeta }}</p>
          <div class="mt-3 flex flex-wrap gap-1.5">
            <span class="badge-neutral">PDF, DOCX, TXT, MD</span>
            <span class="badge-neutral">до 5 МБ, один файл</span>
          </div>
        </div>
      </div>
    </article>

    <div class="card p-5">
      <h2 class="text-base font-semibold text-slate-900">Источник резюме</h2>
      <p class="mt-0.5 text-sm text-slate-500">
        Файл и текст можно добавить вместе. Файл — только один.
      </p>
      <div class="mt-4">
        <ResumeUploader @uploaded="onUploaded" @selection="selection = $event" />
      </div>
    </div>
  </section>
</template>
