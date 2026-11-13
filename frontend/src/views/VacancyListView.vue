<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  ChevronDown,
  FileText,
  Filter,
  Layers,
  Plus,
  Search,
  Trash2,
  X,
} from '@lucide/vue'
import type {
  ReferenceItem,
  Specialization,
  VacancyList,
  VacancySummary,
} from '@/api/generated'
import { apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useToast } from '@/composables/useToast'
import { formatDate } from '@/lib/format'
import { levelLabel } from '@/types'

const { success, error: toastError } = useToast()

const loading = ref(true)
const deleting = ref(false)
const error = ref('')
const page = ref<VacancyList | null>(null)
const specializations = ref<Specialization[]>([])
const levels = ref<ReferenceItem[]>([])

const searchInput = ref('')
const appliedSearch = ref('')
const filtersOpen = ref(false)
const specializationFilter = ref('ALL')
const levelFilter = ref('ALL')
const appliedSpecialization = ref('ALL')
const appliedLevel = ref('ALL')
const showTemplates = ref(false)
const appliedShowTemplates = ref(false)
const deleteTarget = ref<VacancySummary | null>(null)

const items = computed(() => page.value?.items ?? [])

const activeFilterCount = computed(() => {
  let count = 0
  if (appliedSpecialization.value !== 'ALL') {
    count += 1
  }
  if (appliedLevel.value !== 'ALL') {
    count += 1
  }
  if (appliedShowTemplates.value) {
    count += 1
  }
  return count
})

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const query = appliedSearch.value.toLowerCase()
    const matchesSearch =
      query === '' ||
      item.title.toLowerCase().includes(query) ||
      item.specialization.toLowerCase().includes(query) ||
      specializationName(item.specialization).toLowerCase().includes(query)
    const matchesSpecialization =
      appliedSpecialization.value === 'ALL' || item.specialization === appliedSpecialization.value
    const matchesLevel = appliedLevel.value === 'ALL' || item.level === appliedLevel.value
    const matchesTemplate = appliedShowTemplates.value || !item.is_confirmed
    return matchesSearch && matchesSpecialization && matchesLevel && matchesTemplate
  }),
)

const hasOnlyTemplates = computed(
  () => items.value.length > 0 && items.value.every((item) => item.is_confirmed),
)

function specializationName(code: string): string {
  return specializations.value.find((item) => item.code === code)?.name ?? code
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const vacancies = await vacanciesApi.listVacancies({ limit: 200, offset: 0 })
    page.value = vacancies.data
    try {
      const [specs, levelList] = await Promise.all([
        referenceApi.listSpecializations(),
        referenceApi.listLevels(),
      ])
      specializations.value = specs.data
      levels.value = levelList.data
    } catch {
      // Справочники нужны только для человекочитаемых названий — список важнее.
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function applySearch() {
  appliedSearch.value = searchInput.value.trim()
  appliedSpecialization.value = specializationFilter.value
  appliedLevel.value = levelFilter.value
  appliedShowTemplates.value = showTemplates.value
  filtersOpen.value = false
}

function resetFilters() {
  searchInput.value = ''
  specializationFilter.value = 'ALL'
  levelFilter.value = 'ALL'
  showTemplates.value = false
  appliedSearch.value = ''
  appliedSpecialization.value = 'ALL'
  appliedLevel.value = 'ALL'
  appliedShowTemplates.value = false
  filtersOpen.value = false
}

function showOnlyTemplates() {
  showTemplates.value = true
  appliedShowTemplates.value = true
  filtersOpen.value = false
}

function askDelete(item: VacancySummary) {
  deleteTarget.value = item
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target || deleting.value) {
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

onMounted(load)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="section-label">Рабочее пространство</p>
        <h1 class="mt-1 text-3xl font-semibold tracking-tight text-slate-900">Вакансии</h1>
        <p class="mt-1 text-sm text-slate-500">
          Открывайте вакансии, смотрите текст и подбирайте кандидатов.
        </p>
      </div>
      <RouterLink to="/vacancies/new" class="btn-primary">
        <Plus :size="18" />
        Добавить вакансию
      </RouterLink>
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
            placeholder="Поиск по названию или специализации"
            aria-label="Поиск вакансий"
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
          <ChevronDown
            :size="14"
            class="transition"
            :class="filtersOpen ? 'rotate-180' : ''"
          />
        </button>
        <button type="submit" class="btn-primary shrink-0">
          <Search :size="16" />
          Найти
        </button>
      </div>

      <div v-if="filtersOpen" class="mt-3 grid gap-3 border-t border-slate-100 pt-3 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <label class="label" for="filter-specialization">Специализация</label>
          <select id="filter-specialization" v-model="specializationFilter" class="input">
            <option value="ALL">Все специализации</option>
            <option v-for="item in specializations" :key="item.code" :value="item.code">
              {{ item.name }}
            </option>
          </select>
        </div>
        <div>
          <label class="label" for="filter-level">Уровень</label>
          <select id="filter-level" v-model="levelFilter" class="input">
            <option value="ALL">Все уровни</option>
            <option v-for="item in levels" :key="item.code" :value="item.code">
              {{ item.name }}
            </option>
          </select>
        </div>
        <label class="flex cursor-pointer items-center gap-2 self-end rounded-lg border border-slate-200 px-3 py-2.5 text-sm text-slate-600">
          <input v-model="showTemplates" type="checkbox" class="h-4 w-4 accent-indigo-600" />
          Показывать шаблоны
        </label>
        <div class="flex items-end">
          <button type="button" class="btn-ghost px-0" @click="resetFilters">
            <X :size="15" />
            Сбросить фильтры
          </button>
        </div>
      </div>
    </form>

    <div
      v-if="error"
      class="flex items-center justify-between gap-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <span>{{ error }}</span>
      <button class="btn-secondary shrink-0" @click="load">Повторить</button>
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
      v-else-if="items.length === 0"
      class="card flex flex-col items-center justify-center px-6 py-16 text-center"
    >
      <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
        <FileText :size="26" />
      </span>
      <h2 class="mt-4 text-lg font-semibold text-slate-900">Вакансий пока нет</h2>
      <p class="mt-1 max-w-md text-sm text-slate-500">
        Добавьте описание вакансии и назначьте приоритеты требованиям.
      </p>
      <RouterLink to="/vacancies/new" class="btn-primary mt-5">
        <Plus :size="18" />
        Добавить первую вакансию
      </RouterLink>
    </div>

    <div
      v-else-if="filteredItems.length === 0"
      class="card flex flex-col items-center justify-center px-6 py-12 text-center"
    >
      <template v-if="hasOnlyTemplates">
        <Layers :size="28" class="text-slate-300" />
        <h2 class="mt-3 text-base font-semibold text-slate-900">Остались только шаблоны</h2>
        <p class="mt-1 max-w-md text-sm text-slate-500">
          Вакансии, сохранённые как шаблоны, не показываются в общем списке. Их можно открыть
          отдельно или использовать при создании новой вакансии.
        </p>
        <button class="btn-secondary mt-4" @click="showOnlyTemplates">Показать шаблоны</button>
      </template>
      <template v-else>
        <Search :size="28" class="text-slate-300" />
        <h2 class="mt-3 text-base font-semibold text-slate-900">Ничего не найдено</h2>
        <p class="mt-1 text-sm text-slate-500">Попробуйте изменить запрос или сбросить фильтры.</p>
        <button class="btn-secondary mt-4" @click="resetFilters">Сбросить</button>
      </template>
    </div>

    <template v-else>
      <p class="text-xs text-slate-500">Найдено: {{ filteredItems.length }}</p>
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="item in filteredItems"
          :key="item.id"
          class="card group flex flex-col p-5 transition hover:border-indigo-200 hover:shadow-md"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-xs font-medium uppercase tracking-wide text-slate-400">
                {{ specializationName(item.specialization) }}
              </p>
              <RouterLink
                :to="`/vacancies/${item.id}`"
                class="mt-1 block truncate text-lg font-semibold text-slate-900 transition group-hover:text-indigo-600"
              >
                {{ item.title || 'Без названия' }}
              </RouterLink>
            </div>
            <button
              class="icon-btn shrink-0 text-slate-400 hover:bg-red-50 hover:text-red-600"
              title="Удалить вакансию"
              @click="askDelete(item)"
            >
              <Trash2 :size="16" />
            </button>
          </div>

          <div class="mt-4 flex flex-wrap gap-1.5">
            <span v-if="item.is_confirmed" class="badge-info">Шаблон</span>
            <span class="badge-neutral">{{ levelLabel(item.level) }}</span>
            <span class="badge-neutral">{{ item.requirements_count }} требований</span>
          </div>

          <div class="mt-auto flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <span class="text-xs text-slate-400">{{ formatDate(item.created_at) }}</span>
            <RouterLink
              :to="`/vacancies/${item.id}`"
              class="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 hover:text-indigo-700"
            >
              Открыть
              <ArrowRight :size="15" />
            </RouterLink>
          </div>
        </article>
      </div>
    </template>

    <ConfirmDialog
      :open="deleteTarget !== null"
      title="Удалить вакансию?"
      :message="`Вакансия «${deleteTarget?.title ?? ''}» будет удалена без возможности восстановления.`"
      confirm-label="Удалить"
      danger
      @cancel="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </section>
</template>
