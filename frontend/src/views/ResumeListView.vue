<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { FolderSearch, Loader2, Plus, RefreshCcw, X } from '@lucide/vue'
import type { ResumeSummary } from '@/api/generated'
import { apiErrorMessage, resumesApi } from '@/api'
import ResumeUploader from '@/components/ResumeUploader.vue'
import { resumeStatusClass, resumeStatusLabel } from '@/lib/labels'

const route = useRoute()

const items = ref<ResumeSummary[]>([])
const loading = ref(true)
const refreshing = ref(false)
const showUploader = ref(route.query.upload === '1')
const error = ref('')
const highlighted = computed(() => String(route.query.resumeId ?? ''))
let timer: number | null = null

const inProgress = computed(() =>
  items.value.some((item) =>
    ['UPLOADED', 'MASKED', 'EXTRACTED', 'MATCHING'].includes(item.status),
  ),
)

function formatDate(value: string): string {
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function load(manual = false) {
  if (manual) {
    refreshing.value = true
  }
  try {
    const page = await resumesApi.listResumes({ limit: 50, offset: 0 })
    items.value = page.data.items ?? []
    error.value = ''
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function startPolling() {
  stopPolling()
  timer = window.setInterval(async () => {
    await load()
    if (!inProgress.value) {
      stopPolling()
    }
  }, 3000)
}

async function onUploaded() {
  showUploader.value = false
  await load(true)
  if (inProgress.value) {
    startPolling()
  }
}

onMounted(async () => {
  await load()
  if (inProgress.value) {
    startPolling()
  }
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Резюме</h1>
        <p class="mt-1 text-sm text-slate-500">
          Загруженные резюме, статус обработки и вакансии, которые им подходят.
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-secondary" :disabled="refreshing" @click="load(true)">
          <Loader2 v-if="refreshing" :size="16" class="animate-spin" />
          <RefreshCcw v-else :size="16" />
          Обновить
        </button>
        <button class="btn-primary" @click="showUploader = !showUploader">
          <X v-if="showUploader" :size="16" />
          <Plus v-else :size="16" />
          {{ showUploader ? 'Свернуть загрузку' : 'Добавить резюме' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="showUploader" class="card p-5">
      <div class="mb-4">
        <h2 class="text-base font-semibold text-slate-900">Добавить резюме</h2>
        <p class="mt-0.5 text-sm text-slate-500">
          Загрузите файл или вставьте текст. Сразу после обработки резюме появится в списке,
          а вакансии для него можно будет подобрать в один клик.
        </p>
      </div>
      <ResumeUploader @uploaded="onUploaded" />
    </div>

    <div
      v-if="inProgress"
      class="flex items-center gap-3 rounded-2xl border border-indigo-200 bg-indigo-50/60 px-4 py-3 text-sm text-indigo-800"
    >
      <Loader2 :size="16" class="animate-spin" />
      Идёт обработка резюме. Список обновится автоматически.
    </div>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>
    <div
      v-else-if="!items.length"
      class="rounded-3xl border border-dashed border-slate-300 px-6 py-16 text-center"
    >
      <FolderSearch :size="32" class="mx-auto text-slate-300" />
      <p class="mt-3 text-sm text-slate-500">Пока нет резюме. Добавьте первое.</p>
      <button class="btn-primary mt-4 inline-flex" @click="showUploader = true">
        <Plus :size="16" />
        Добавить резюме
      </button>
    </div>
    <div v-else class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <table class="w-full text-left text-sm">
        <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3">Резюме</th>
            <th class="px-4 py-3">Статус</th>
            <th class="px-4 py-3">Загружено</th>
            <th class="px-4 py-3 text-right">Действия</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr
            v-for="item in items"
            :key="item.resumeId"
            :class="item.resumeId === highlighted ? 'bg-indigo-50/60' : ''"
          >
            <td class="px-4 py-3 font-mono text-slate-700">{{ item.resumeId.slice(0, 8) }}…</td>
            <td class="px-4 py-3">
              <span class="rounded-full px-2 py-1 text-xs font-medium" :class="resumeStatusClass(item.status)">
                {{ resumeStatusLabel(item.status) }}
              </span>
            </td>
            <td class="px-4 py-3 text-slate-500">{{ formatDate(item.createdAt) }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap items-center justify-end gap-3">
                <RouterLink
                  :to="`/resumes/${item.resumeId}/text`"
                  class="text-sm font-medium text-slate-500 hover:text-slate-800"
                >
                  Текст
                </RouterLink>
                <RouterLink
                  :to="`/resumes/${item.resumeId}`"
                  class="text-sm font-medium text-indigo-600 hover:text-indigo-700"
                >
                  Подобрать вакансии
                </RouterLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
