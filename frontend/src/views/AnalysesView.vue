<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { FolderSearch, Loader2, RefreshCcw } from '@lucide/vue'
import type { ResumeSummary } from '@/api/generated'
import { apiErrorMessage, resumesApi } from '@/api'

const route = useRoute()

const items = ref<ResumeSummary[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const highlighted = computed(() => String(route.query.resumeId ?? ''))
let timer: number | null = null

const inProgress = computed(() =>
  items.value.some((item) =>
    ['UPLOADED', 'MASKED', 'EXTRACTED', 'MATCHING'].includes(item.status),
  ),
)

function statusClass(status: string): string {
  switch (status) {
    case 'MATCHED':
      return 'bg-emerald-50 text-emerald-700'
    case 'MATCH_FAILED':
    case 'MASKING_FAILED':
    case 'FAILED':
      return 'bg-rose-50 text-rose-700'
    case 'TEXT_EMPTY':
      return 'bg-amber-50 text-amber-700'
    default:
      return 'bg-slate-100 text-slate-600'
  }
}

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
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Разборы</h1>
        <p class="mt-1 text-sm text-slate-500">
          Все загруженные резюме и статус их сравнения с вакансиями.
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-secondary" :disabled="refreshing" @click="load(true)">
          <Loader2 v-if="refreshing" :size="16" class="animate-spin" />
          <RefreshCcw v-else :size="16" />
          Обновить
        </button>
        <RouterLink to="/match" class="btn-primary">Загрузить резюме</RouterLink>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="inProgress" class="flex items-center gap-3 rounded-2xl border border-indigo-200 bg-indigo-50/60 px-4 py-3 text-sm text-indigo-800">
      <Loader2 :size="16" class="animate-spin" />
      Идёт разбор и сравнение. Список обновится автоматически.
    </div>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>
    <div
      v-else-if="!items.length"
      class="rounded-3xl border border-dashed border-slate-300 px-6 py-16 text-center"
    >
      <FolderSearch :size="32" class="mx-auto text-slate-300" />
      <p class="mt-3 text-sm text-slate-500">Пока нет разборов. Загрузите первое резюме.</p>
      <RouterLink to="/match" class="btn-primary mt-4 inline-flex">Загрузить резюме</RouterLink>
    </div>
    <div v-else class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <table class="w-full text-left text-sm">
        <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3">Резюме</th>
            <th class="px-4 py-3">Статус</th>
            <th class="px-4 py-3">Загружено</th>
            <th class="px-4 py-3"></th>
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
              <span class="rounded-full px-2 py-1 text-xs font-medium" :class="statusClass(item.status)">
                {{ item.status }}
              </span>
            </td>
            <td class="px-4 py-3 text-slate-500">{{ formatDate(item.createdAt) }}</td>
            <td class="px-4 py-3 text-right">
              <RouterLink
                :to="`/resumes/${item.resumeId}`"
                class="text-sm font-medium text-indigo-600 hover:text-indigo-700"
              >
                Открыть
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
