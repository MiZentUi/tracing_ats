<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Clock3, FileText, Plus, Search, Trash2 } from 'lucide-vue-next'
import type { VacancyList, VacancySummary } from '@/api/generated'
import { apiErrorMessage, vacanciesApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { levelLabel } from '@/types'
import { useToast } from '@/composables/useToast'

const { success, error: toastError } = useToast()

const loading = ref(true)
const deleting = ref(false)
const error = ref('')
const page = ref<VacancyList | null>(null)
const search = ref('')
const statusFilter = ref<'ALL' | 'DRAFT' | 'ACTIVE'>('ALL')
const specializationFilter = ref('ALL')
const levelFilter = ref('ALL')
const deleteTarget = ref<VacancySummary | null>(null)

const items = computed(() => page.value?.items ?? [])

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const query = search.value.trim().toLowerCase()
    const matchesSearch =
      query === '' ||
      item.title.toLowerCase().includes(query) ||
      item.specialization.toLowerCase().includes(query)
    const matchesStatus =
      statusFilter.value === 'ALL' ||
      (statusFilter.value === 'ACTIVE' ? item.is_confirmed : !item.is_confirmed)
    const matchesSpecialization =
      specializationFilter.value === 'ALL' || item.specialization === specializationFilter.value
    const matchesLevel = levelFilter.value === 'ALL' || item.level === levelFilter.value
    return matchesSearch && matchesStatus && matchesSpecialization && matchesLevel
  }),
)

const specializations = computed(() =>
  [...new Set(items.value.map((item) => item.specialization))].sort((a, b) => a.localeCompare(b)),
)
const levels = computed(() =>
  [...new Set(items.value.map((item) => item.level))].sort((a, b) =>
    levelLabel(a).localeCompare(levelLabel(b), 'ru'),
  ),
)
const confirmedCount = computed(() => items.value.filter((item) => item.is_confirmed).length)
const draftCount = computed(() => items.value.length - confirmedCount.value)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await vacanciesApi.listVacancies({ limit: 50, offset: 0 })
    page.value = response.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  search.value = ''
  statusFilter.value = 'ALL'
  specializationFilter.value = 'ALL'
  levelFilter.value = 'ALL'
}

function askDelete(item: VacancySummary) {
  deleteTarget.value = item
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) {
    return
  }
  deleting.value = true
  try {
    await vacanciesApi.deleteVacancy({ id: target.id })
    deleteTarget.value = null
    success('Вакансия удалена', `«${target.title}» больше не влияет на историю типов`)
    await load()
  } catch (e) {
    toastError('Не удалось удалить вакансию', apiErrorMessage(e))
  } finally {
    deleting.value = false
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(load)
</script>

<template>
  <section class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="section-label">Рабочее пространство</p>
        <h1 class="mt-1 text-3xl font-semibold tracking-tight text-slate-900">Вакансии</h1>
        <p class="mt-1 text-sm text-slate-500">Шаблоны требований, типы навыков и история расстановки HR.</p>
      </div>
      <RouterLink to="/vacancies/new" class="btn-primary">
        <Plus :size="18" />
        Создать вакансию
      </RouterLink>
    </div>

    <div class="grid gap-3 sm:grid-cols-3">
      <div class="card flex items-center gap-3 p-4">
        <span class="grid h-11 w-11 place-items-center rounded-2xl bg-indigo-50 text-indigo-600">
          <FileText :size="20" />
        </span>
        <div>
          <p class="text-xs font-medium uppercase tracking-wide text-slate-400">Всего</p>
          <p class="text-2xl font-semibold text-slate-900">{{ page?.total ?? '—' }}</p>
        </div>
      </div>
      <div class="card flex items-center gap-3 p-4">
        <span class="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-50 text-emerald-600">
          <CheckCircle2 :size="20" />
        </span>
        <div>
          <p class="text-xs font-medium uppercase tracking-wide text-slate-400">Подтверждено</p>
          <p class="text-2xl font-semibold text-slate-900">{{ confirmedCount }}</p>
        </div>
      </div>
      <div class="card flex items-center gap-3 p-4">
        <span class="grid h-11 w-11 place-items-center rounded-2xl bg-amber-50 text-amber-600">
          <Clock3 :size="20" />
        </span>
        <div>
          <p class="text-xs font-medium uppercase tracking-wide text-slate-400">Черновики</p>
          <p class="text-2xl font-semibold text-slate-900">{{ draftCount }}</p>
        </div>
      </div>
    </div>

    <div class="card p-3">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center">
        <label class="relative flex-1">
          <Search :size="16" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input v-model="search" class="input pl-9" placeholder="Поиск по названию или специализации" />
        </label>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:flex lg:flex-none">
          <select v-model="statusFilter" class="input lg:w-40" aria-label="Фильтр по статусу">
            <option value="ALL">Все статусы</option>
            <option value="DRAFT">Черновики</option>
            <option value="ACTIVE">Подтверждённые</option>
          </select>
          <select v-model="specializationFilter" class="input lg:w-44" aria-label="Фильтр по специализации">
            <option value="ALL">Все специализации</option>
            <option v-for="item in specializations" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="levelFilter" class="input col-span-2 sm:col-span-1 lg:w-40" aria-label="Фильтр по уровню">
            <option value="ALL">Все уровни</option>
            <option v-for="item in levels" :key="item" :value="item">{{ levelLabel(item) }}</option>
          </select>
        </div>
      </div>
    </div>

    <div v-if="error" class="flex items-center justify-between gap-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <span>{{ error }}</span>
      <button class="btn-secondary shrink-0" @click="load">Повторить</button>
    </div>

    <div v-if="loading" class="card overflow-hidden">
      <div class="space-y-4 p-5">
        <div v-for="index in 4" :key="index" class="flex items-center gap-4">
          <div class="skeleton h-4 w-1/3" />
          <div class="skeleton h-4 w-24" />
          <div class="skeleton h-4 w-20" />
          <div class="skeleton ml-auto h-8 w-24" />
        </div>
      </div>
    </div>

    <div v-else-if="items.length === 0" class="card flex flex-col items-center justify-center px-6 py-16 text-center">
      <span class="grid h-14 w-14 place-items-center rounded-2xl bg-indigo-50 text-indigo-600">
        <FileText :size="26" />
      </span>
      <h2 class="mt-4 text-lg font-semibold text-slate-900">Вакансий пока нет</h2>
      <p class="mt-1 max-w-md text-sm text-slate-500">
        Загрузите описание вакансии — модель разложит его на требования, а вы поправите типы навыков.
      </p>
      <RouterLink to="/vacancies/new" class="btn-primary mt-5">
        <Plus :size="18" />
        Создать первую вакансию
      </RouterLink>
    </div>

    <div v-else-if="filteredItems.length === 0" class="card flex flex-col items-center justify-center px-6 py-12 text-center">
      <Search :size="28" class="text-slate-300" />
      <h2 class="mt-3 text-base font-semibold text-slate-900">Ничего не найдено</h2>
      <p class="mt-1 text-sm text-slate-500">Попробуйте изменить поиск или сбросить фильтры.</p>
      <button class="btn-secondary mt-4" @click="resetFilters">Сбросить фильтры</button>
    </div>

    <template v-else>
      <div class="card hidden overflow-hidden md:block">
        <table class="min-w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th class="px-5 py-3 font-medium">Вакансия</th>
              <th class="px-4 py-3 font-medium">Специализация</th>
              <th class="px-4 py-3 font-medium">Уровень</th>
              <th class="px-4 py-3 font-medium">Статус</th>
              <th class="px-4 py-3 text-center font-medium">Требований</th>
              <th class="px-4 py-3 font-medium">Обновлена</th>
              <th class="px-4 py-3 text-right font-medium">Действия</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredItems"
              :key="item.id"
              class="border-b border-slate-100 transition last:border-0 hover:bg-slate-50/70"
            >
              <td class="px-5 py-4">
                <RouterLink :to="`/vacancies/${item.id}`" class="font-semibold text-slate-900 transition hover:text-indigo-600">
                  {{ item.title }}
                </RouterLink>
                <p class="mt-0.5 text-xs text-slate-400">Версия {{ item.version }}</p>
              </td>
              <td class="px-4 py-4 text-slate-600">{{ item.specialization }}</td>
              <td class="px-4 py-4 text-slate-600">{{ levelLabel(item.level) }}</td>
              <td class="px-4 py-4">
                <span :class="item.is_confirmed ? 'badge-success' : 'badge-warning'">
                  {{ item.is_confirmed ? 'Подтверждена' : 'Черновик' }}
                </span>
              </td>
              <td class="px-4 py-4 text-center">
                <span class="badge-neutral">{{ item.requirements_count }}</span>
              </td>
              <td class="px-4 py-4 text-slate-500">{{ formatDate(item.created_at) }}</td>
              <td class="px-4 py-4 text-right">
                <button class="icon-btn text-slate-400 hover:bg-red-50 hover:text-red-600" title="Удалить вакансию" @click="askDelete(item)">
                  <Trash2 :size="16" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="grid gap-3 md:hidden">
        <div v-for="item in filteredItems" :key="item.id" class="card p-4 transition hover:border-indigo-200">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <RouterLink :to="`/vacancies/${item.id}`" class="font-semibold text-slate-900 hover:text-indigo-600">
                {{ item.title }}
              </RouterLink>
              <p class="mt-1 text-xs text-slate-500">
                {{ item.specialization }} · {{ levelLabel(item.level) }} · версия {{ item.version }}
              </p>
            </div>
            <span :class="item.is_confirmed ? 'badge-success' : 'badge-warning'">
              {{ item.is_confirmed ? 'Подтверждена' : 'Черновик' }}
            </span>
          </div>
          <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
            <span>Требований: {{ item.requirements_count }} · {{ formatDate(item.created_at) }}</span>
            <button class="icon-btn text-slate-400 hover:bg-red-50 hover:text-red-600" title="Удалить вакансию" @click="askDelete(item)">
              <Trash2 :size="16" />
            </button>
          </div>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :open="deleteTarget !== null"
      title="Удалить вакансию?"
      :message="`Вакансия «${deleteTarget?.title ?? ''}» будет удалена вместе с версиями требований. Её вклад исчезнет из истории типов. Это действие необратимо.`"
      confirm-label="Удалить"
      danger
      @cancel="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>
