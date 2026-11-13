<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowRight,
  ChevronDown,
  FileText,
  Filter,
  Loader2,
  Plus,
  RefreshCcw,
  Search,
  X,
} from '@lucide/vue'
import type { ResumeSummary } from '@/api/generated'
import { apiErrorMessage, resumesApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { resumeDisplayTitle } from '@/lib/format'
import { resumeStatusClass, resumeStatusLabel } from '@/lib/labels'

const { error: toastError } = useToast()

const items = ref<ResumeSummary[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const searchInput = ref('')
const appliedSearch = ref('')
const filtersOpen = ref(false)
const statusFilter = ref<'ALL' | 'PROCESSING' | 'READY' | 'PROBLEM'>('ALL')
const appliedStatus = ref<'ALL' | 'PROCESSING' | 'READY' | 'PROBLEM'>('ALL')
let timer: number | null = null

const inProgress = computed(() =>
  items.value.some((item) =>
    ['UPLOADED', 'MASKED', 'EXTRACTED', 'MATCHING'].includes(item.status),
  ),
)

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const query = appliedSearch.value.toLowerCase()
    const matchesSearch =
      query === '' ||
      resumeDisplayTitle(item.title, item.createdAt).toLowerCase().includes(query) ||
      resumeStatusLabel(item.status).toLowerCase().includes(query)
    let matchesStatus = true
    if (appliedStatus.value === 'PROCESSING') {
      matchesStatus = ['UPLOADED', 'MASKED', 'EXTRACTED', 'MATCHING'].includes(item.status)
    } else if (appliedStatus.value === 'READY') {
      matchesStatus = item.status === 'MATCHED'
    } else if (appliedStatus.value === 'PROBLEM') {
      matchesStatus = ['MATCH_FAILED', 'MASKING_FAILED', 'TEXT_EMPTY', 'FAILED'].includes(
        item.status,
      )
    }
    return matchesSearch && matchesStatus
  }),
)

const activeFilterCount = computed(() => (appliedStatus.value === 'ALL' ? 0 : 1))

async function load(manual = false) {
  if (manual) {
    refreshing.value = true
  }
  try {
    const page = await resumesApi.listResumes({ limit: 100, offset: 0 })
    items.value = page.data.items ?? []
    error.value = ''
  } catch (e) {
    error.value = apiErrorMessage(e)
    if (manual) {
      toastError('Не удалось обновить список', error.value)
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function applySearch() {
  appliedSearch.value = searchInput.value.trim()
  appliedStatus.value = statusFilter.value
  filtersOpen.value = false
}

function resetFilters() {
  searchInput.value = ''
  statusFilter.value = 'ALL'
  appliedSearch.value = ''
  appliedStatus.value = 'ALL'
  filtersOpen.value = false
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
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="section-label">Кандидаты</p>
        <h1 class="mt-1 text-3xl font-semibold tracking-tight text-slate-900">Резюме</h1>
        <p class="mt-1 text-sm text-slate-500">
          Открывайте резюме, смотрите обезличенный текст и подходящие вакансии.
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-secondary" :disabled="refreshing" @click="load(true)">
          <Loader2 v-if="refreshing" :size="16" class="animate-spin" />
          <RefreshCcw v-else :size="16" />
          Обновить
        </button>
        <RouterLink to="/resumes/new" class="btn-primary">
          <Plus :size="18" />
          Добавить резюме
        </RouterLink>
      </div>
    </header>

    <form class="card p-3" @submit.prevent="applySearch">
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <label class="relative min-w-0 flex-1">
          <Search
            :size="16"
            class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            v-model="searchInput"
            class="input pl-9"
            placeholder="Поиск по названию или статусу"
            aria-label="Поиск резюме"
          />
        </label>
        <button
          type="button"
          class="btn-secondary shrink-0"
          :class="filtersOpen || activeFilterCount > 0 ? 'border-slate-400 text-slate-900' : ''"
          :aria-expanded="filtersOpen"
          @click="filtersOpen = !filtersOpen"
        >
          <Filter :size="16" />
          Фильтры
          <span v-if="activeFilterCount > 0" class="badge-info">{{ activeFilterCount }}</span>
          <ChevronDown :size="14" class="transition" :class="filtersOpen ? 'rotate-180' : ''" />
        </button>
        <button type="submit" class="btn-primary shrink-0">
          <Search :size="16" />
          Найти
        </button>
      </div>

      <div v-if="filtersOpen" class="mt-3 flex flex-wrap items-end gap-3 border-t border-slate-100 pt-3">
        <div class="w-full sm:w-56">
          <label class="label" for="resume-status-filter">Статус</label>
          <select id="resume-status-filter" v-model="statusFilter" class="input">
            <option value="ALL">Все статусы</option>
            <option value="PROCESSING">В обработке</option>
            <option value="READY">Готово</option>
            <option value="PROBLEM">Ошибки</option>
          </select>
        </div>
        <button type="button" class="btn-ghost px-0" @click="resetFilters">
          <X :size="15" />
          Сбросить фильтры
        </button>
      </div>
    </form>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div
      v-if="inProgress"
      class="flex items-center gap-3 rounded-xl border border-indigo-200 bg-indigo-50/60 px-4 py-3 text-sm text-indigo-800"
    >
      <Loader2 :size="16" class="animate-spin" />
      Идёт обработка резюме. Список обновится автоматически.
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="index in 6" :key="index" class="card space-y-4 p-5">
        <div class="skeleton h-3 w-1/3" />
        <div class="skeleton h-6 w-2/3" />
        <div class="skeleton h-4 w-1/2" />
        <div class="skeleton h-8 w-full" />
      </div>
    </div>

    <div
      v-else-if="!items.length"
      class="card flex flex-col items-center justify-center px-6 py-16 text-center"
    >
      <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
        <FileText :size="26" />
      </span>
      <h2 class="mt-4 text-lg font-semibold text-slate-900">Резюме пока нет</h2>
      <p class="mt-1 max-w-md text-sm text-slate-500">
        Добавьте файл или вставьте текст — система обезличит данные и подберёт вакансии.
      </p>
      <RouterLink to="/resumes/new" class="btn-primary mt-5">
        <Plus :size="18" />
        Добавить первое резюме
      </RouterLink>
    </div>

    <div
      v-else-if="filteredItems.length === 0"
      class="card flex flex-col items-center justify-center px-6 py-12 text-center"
    >
      <Search :size="28" class="text-slate-300" />
      <h2 class="mt-3 text-base font-semibold text-slate-900">Ничего не найдено</h2>
      <p class="mt-1 text-sm text-slate-500">Попробуйте изменить запрос или сбросить фильтры.</p>
      <button class="btn-secondary mt-4" @click="resetFilters">Сбросить</button>
    </div>

    <template v-else>
      <p class="text-xs text-slate-500">Найдено: {{ filteredItems.length }}</p>
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="item in filteredItems"
          :key="item.resumeId"
          class="card group flex flex-col p-5 transition hover:border-indigo-200 hover:shadow-md"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <RouterLink
                :to="`/resumes/${item.resumeId}`"
                class="block text-lg font-semibold text-slate-900 transition group-hover:text-indigo-600"
              >
                {{ resumeDisplayTitle(item.title, item.createdAt) }}
              </RouterLink>
            </div>
            <span
              class="shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium"
              :class="resumeStatusClass(item.status)"
            >
              {{ resumeStatusLabel(item.status) }}
            </span>
          </div>

          <div class="mt-auto flex items-center justify-end border-t border-slate-100 pt-4">
            <RouterLink
              :to="`/resumes/${item.resumeId}`"
              class="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 hover:text-indigo-700"
            >
              Открыть
              <ArrowRight :size="15" />
            </RouterLink>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>
