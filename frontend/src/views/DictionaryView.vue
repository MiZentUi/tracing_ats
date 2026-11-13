<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  BookOpen,
  Check,
  ListChecks,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  X,
} from '@lucide/vue'
import {
  addAlias,
  approveGeneration,
  createGenerationJob,
  createSkill,
  listGenerationJobs,
  listReview,
  listSkills,
  rejectGeneration,
  resolveReview,
  setCanonical,
  type GenerationJob,
  type ReviewItem,
  type Skill,
} from '@/api/dictionary'
import { apiErrorMessage } from '@/api'
import AppModal from '@/components/AppModal.vue'
import { useToast } from '@/composables/useToast'
import { formatDate, pluralizeRu } from '@/lib/format'

const { success, error: toastError } = useToast()

type Tab = 'queue' | 'skills' | 'generation'

const tab = ref<Tab>('skills')
const loading = ref(true)
const searching = ref(false)
const error = ref('')
const review = ref<ReviewItem[]>([])
const skills = ref<Skill[]>([])
const jobs = ref<GenerationJob[]>([])
const activeJob = ref<GenerationJob | null>(null)

const query = ref('')
const skillTypeFilter = ref('')
const skillStatusFilter = ref('ACTIVE')
const aliasDrafts = ref<Record<string, string>>({})

const newSkillOpen = ref(false)
const newSkillName = ref('')
const newSkillType = ref('OTHER')
const newSkillError = ref('')
const creatingSkill = ref(false)

const specialization = ref('')
const generationNote = ref('')
const maxSkills = ref(30)
const generating = ref(false)
const selectedSkills = ref<Record<string, boolean>>({})
const selectedAliases = ref<Record<string, boolean>>({})

const pending = computed(() => review.value.filter((item) => item.status === 'PENDING'))
const selectedCount = computed(
  () => Object.values(selectedSkills.value).filter(Boolean).length,
)
const activeJobEditable = computed(() => activeJob.value?.status === 'DONE')

const tabs = computed(() => [
  { id: 'skills' as const, label: 'Навыки', icon: BookOpen, count: skills.value.length },
  {
    id: 'generation' as const,
    label: 'Генерация',
    icon: Sparkles,
    count: jobs.value.length,
  },
  { id: 'queue' as const, label: 'Очередь', icon: ListChecks, count: pending.value.length },
])

const skillTypeLabels: Record<string, string> = {
  TECH: 'Технический',
  TOOL: 'Инструмент',
  DOMAIN: 'Предметная область',
  SOFT: 'Гибкий',
  OTHER: 'Другой',
}

const skillStatusLabels: Record<string, string> = {
  ACTIVE: 'Активный',
  DEPRECATED: 'В архиве',
}

const jobStatusLabels: Record<string, string> = {
  PENDING: 'В очереди',
  RUNNING: 'Генерируется',
  DONE: 'Черновик готов',
  FAILED: 'Ошибка',
  APPROVED: 'Подтверждён',
  REJECTED: 'Отклонён',
}

function skillTypeLabel(value: string): string {
  return skillTypeLabels[value] ?? value
}

function skillStatusLabel(value: string): string {
  return skillStatusLabels[value] ?? value
}

function jobStatusLabel(value: string): string {
  return jobStatusLabels[value] ?? value
}

function jobStatusClass(value: string): string {
  switch (value) {
    case 'APPROVED':
      return 'bg-emerald-50 text-emerald-700'
    case 'REJECTED':
    case 'FAILED':
      return 'bg-rose-50 text-rose-700'
    case 'DONE':
      return 'bg-indigo-50 text-indigo-700'
    default:
      return 'bg-amber-50 text-amber-700'
  }
}

function aliasCount(count: number): string {
  return `${count} ${pluralizeRu(count, 'алиас', 'алиаса', 'алиасов')}`
}

function skillsCount(count: number): string {
  return `${count} ${pluralizeRu(count, 'навык', 'навыка', 'навыков')}`
}

function occurrencesLabel(count: number): string {
  return `${count} ${pluralizeRu(count, 'вхождение', 'вхождения', 'вхождений')}`
}

function skillSelectable(skill: GenerationJob['skills'][number]): boolean {
  return (
    activeJobEditable.value &&
    skill.status === 'DRAFT' &&
    !skill.conflictSkillId
  )
}

function aliasSelectable(
  skill: GenerationJob['skills'][number],
  alias: GenerationJob['skills'][number]['aliases'][number],
): boolean {
  return (
    skillSelectable(skill) &&
    !!selectedSkills.value[skill.id] &&
    alias.status === 'DRAFT' &&
    !alias.conflictSkillId
  )
}

async function loadSkills() {
  skills.value = await listSkills(
    query.value,
    skillTypeFilter.value,
    skillStatusFilter.value,
  )
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [reviewItems, skillItems, jobItems] = await Promise.all([
      listReview(),
      listSkills(query.value, skillTypeFilter.value, skillStatusFilter.value),
      listGenerationJobs(),
    ])
    review.value = reviewItems
    skills.value = skillItems
    jobs.value = jobItems
    syncActiveJob(jobItems)
  } catch (exception) {
    error.value = apiErrorMessage(exception)
    toastError('Словарь недоступен', error.value)
  } finally {
    loading.value = false
  }
}

function syncActiveJob(jobItems: GenerationJob[]) {
  if (activeJob.value) {
    const current = jobItems.find((job) => job.id === activeJob.value?.id)
    if (current) {
      activeJob.value = current
      return
    }
    activeJob.value = null
    selectedSkills.value = {}
    selectedAliases.value = {}
  }
  if (jobItems.length > 0) {
    selectJob(jobItems[0])
  }
}

async function searchSkills() {
  searching.value = true
  error.value = ''
  try {
    await loadSkills()
  } catch (exception) {
    error.value = apiErrorMessage(exception)
    toastError('Не удалось найти навыки', error.value)
  } finally {
    searching.value = false
  }
}

async function bind(item: ReviewItem, skillId: string) {
  try {
    await resolveReview(item.id, 'ALIAS', skillId)
    success('Алиас добавлен')
    await loadAll()
  } catch (exception) {
    toastError('Не удалось привязать', apiErrorMessage(exception))
  }
}

async function createFromReview(item: ReviewItem) {
  try {
    await resolveReview(item.id, 'NEW_SKILL', undefined, item.samples[0] ?? item.key)
    success('Навык создан')
    await loadAll()
  } catch (exception) {
    toastError('Не удалось создать навык', apiErrorMessage(exception))
  }
}

async function reject(item: ReviewItem) {
  try {
    await resolveReview(item.id, 'REJECT')
    success('Запись отклонена')
    await loadAll()
  } catch (exception) {
    toastError('Не удалось отклонить', apiErrorMessage(exception))
  }
}

function openCreateSkill() {
  newSkillName.value = ''
  newSkillType.value = 'OTHER'
  newSkillError.value = ''
  newSkillOpen.value = true
}

async function createNewSkill() {
  const name = newSkillName.value.trim()
  if (!name || creatingSkill.value) {
    return
  }
  creatingSkill.value = true
  newSkillError.value = ''
  try {
    await createSkill(name, newSkillType.value)
    query.value = ''
    skillTypeFilter.value = ''
    skillStatusFilter.value = 'ACTIVE'
    newSkillOpen.value = false
    success('Навык создан')
    await loadSkills()
  } catch (exception) {
    newSkillError.value = apiErrorMessage(exception)
  } finally {
    creatingSkill.value = false
  }
}

async function bindAlias(skill: Skill) {
  const raw = (aliasDrafts.value[skill.id] ?? '').trim()
  if (!raw) {
    return
  }
  try {
    await addAlias(skill.id, raw)
    aliasDrafts.value[skill.id] = ''
    success('Алиас добавлен')
    await loadSkills()
  } catch (exception) {
    toastError('Не удалось добавить алиас', apiErrorMessage(exception))
  }
}

async function makeCanonical(skill: Skill, aliasId: string) {
  try {
    await setCanonical(skill.id, aliasId)
    success('Канонический алиас изменён')
    await loadSkills()
  } catch (exception) {
    toastError('Не удалось изменить канон', apiErrorMessage(exception))
  }
}

function initSelection(job: GenerationJob) {
  const nextSkills: Record<string, boolean> = {}
  const nextAliases: Record<string, boolean> = {}
  for (const skill of job.skills) {
    nextSkills[skill.id] = !skill.conflictSkillId && skill.status === 'DRAFT'
    for (const alias of skill.aliases) {
      nextAliases[alias.id] = !alias.conflictSkillId && alias.status === 'DRAFT'
    }
  }
  selectedSkills.value = nextSkills
  selectedAliases.value = nextAliases
}

function selectJob(job: GenerationJob) {
  activeJob.value = job
  initSelection(job)
}

function selectAll() {
  if (!activeJob.value) {
    return
  }
  for (const skill of activeJob.value.skills) {
    if (!skillSelectable(skill)) {
      continue
    }
    selectedSkills.value[skill.id] = true
    for (const alias of skill.aliases) {
      if (!alias.conflictSkillId && alias.status === 'DRAFT') {
        selectedAliases.value[alias.id] = true
      }
    }
  }
}

function clearAll() {
  if (!activeJob.value) {
    return
  }
  for (const skill of activeJob.value.skills) {
    selectedSkills.value[skill.id] = false
    for (const alias of skill.aliases) {
      selectedAliases.value[alias.id] = false
    }
  }
}

async function startGeneration() {
  if (!specialization.value.trim() || generating.value) {
    return
  }
  generating.value = true
  try {
    const job = await createGenerationJob({
      specialization: specialization.value.trim(),
      note: generationNote.value.trim() || undefined,
      maxSkills: maxSkills.value,
    })
    activeJob.value = job
    initSelection(job)
    jobs.value = await listGenerationJobs()
    success('Черновики готовы', 'Проверьте навыки и алиасы перед подтверждением')
  } catch (exception) {
    toastError('Не удалось сгенерировать', apiErrorMessage(exception))
  } finally {
    generating.value = false
  }
}

async function approve() {
  if (!activeJob.value || !activeJobEditable.value) {
    return
  }
  const accepted = activeJob.value.skills
    .filter((skill) => selectedSkills.value[skill.id] && !skill.conflictSkillId)
    .map((skill) => ({
      draftId: skill.id,
      canonical: skill.canonical,
      skillType: skill.skillType,
      targetSkillId: skill.targetSkillId,
      aliases: skill.aliases
        .filter((alias) => selectedAliases.value[alias.id] && !alias.conflictSkillId)
        .map((alias) => alias.text),
    }))
  if (accepted.length === 0) {
    toastError('Ничего не выбрано', 'Отметьте хотя бы один навык')
    return
  }
  try {
    const job = await approveGeneration(activeJob.value.id, accepted)
    activeJob.value = job
    initSelection(job)
    jobs.value = await listGenerationJobs()
    success('Навыки добавлены в словарь')
  } catch (exception) {
    toastError('Не удалось подтвердить', apiErrorMessage(exception))
  }
}

async function rejectJob() {
  if (!activeJob.value || !activeJobEditable.value) {
    return
  }
  try {
    const job = await rejectGeneration(activeJob.value.id)
    activeJob.value = job
    initSelection(job)
    jobs.value = await listGenerationJobs()
    success('Черновики отклонены')
  } catch (exception) {
    toastError('Не удалось отклонить', apiErrorMessage(exception))
  }
}

onMounted(loadAll)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="section-label">Справочник</p>
        <h1 class="mt-1 text-3xl font-semibold tracking-tight text-slate-900">
          Словарь навыков
        </h1>
        <p class="mt-1 text-sm text-slate-500">
          Новые написания, алиасы, канонические имена и генерация навыков по специальности.
        </p>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="loadAll">
        <Loader2 v-if="loading" :size="16" class="animate-spin" />
        <RefreshCw v-else :size="16" />
        Обновить
      </button>
    </header>

    <div
      v-if="error"
      class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <span>{{ error }}</span>
      <button class="btn-secondary shrink-0" @click="loadAll">Повторить</button>
    </div>

    <div class="inline-flex flex-wrap rounded-xl border border-slate-200 bg-white p-1" role="tablist">
      <button
        v-for="item in tabs"
        :key="item.id"
        type="button"
        class="inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition"
        :class="tab === item.id ? 'bg-slate-900 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'"
        @click="tab = item.id"
      >
        <component :is="item.icon" :size="15" />
        {{ item.label }}
        <span
          class="rounded-full px-1.5 py-0.5 text-[10px]"
          :class="tab === item.id ? 'bg-white/15' : 'bg-slate-100 text-slate-500'"
        >
          {{ item.count }}
        </span>
      </button>
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="index in 6" :key="index" class="card space-y-4 p-5">
        <div class="skeleton h-3 w-1/3" />
        <div class="skeleton h-6 w-2/3" />
        <div class="skeleton h-4 w-1/2" />
        <div class="skeleton h-9 w-full" />
      </div>
    </div>

    <template v-else-if="tab === 'queue'">
      <div
        v-if="pending.length === 0"
        class="card flex flex-col items-center justify-center px-6 py-16 text-center"
      >
        <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
          <ListChecks :size="26" />
        </span>
        <h2 class="mt-4 text-lg font-semibold text-slate-900">Очередь пуста</h2>
        <p class="mt-1 max-w-md text-sm text-slate-500">
          Новые написания навыков появятся здесь после разбора резюме и вакансий.
        </p>
      </div>

      <div v-else class="space-y-3">
        <article v-for="item in pending" :key="item.id" class="card p-5">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-xs font-medium uppercase tracking-wide text-slate-400">
                Новое написание
              </p>
              <p class="mt-1 break-all font-mono text-lg font-semibold text-slate-900">
                {{ item.key }}
              </p>
              <p class="mt-1 text-xs text-slate-500">{{ occurrencesLabel(item.occurrences) }}</p>
            </div>
            <span class="badge-warning">На проверке</span>
          </div>

          <div v-if="item.samples.length" class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="sample in item.samples"
              :key="sample"
              class="rounded-md bg-slate-50 px-2 py-1 text-xs text-slate-600"
            >
              {{ sample }}
            </span>
          </div>

          <div class="mt-4 border-t border-slate-100 pt-4">
            <p class="text-xs font-medium text-slate-500">Куда отнести</p>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="candidate in item.candidates"
                :key="candidate.skillId"
                type="button"
                class="btn-secondary"
                @click="bind(item, candidate.skillId)"
              >
                <Check :size="14" />
                {{ candidate.canonicalName }}
                <span class="text-xs font-normal text-slate-400">
                  {{ Math.round(candidate.confidence * 100) }}%
                </span>
              </button>
              <button type="button" class="btn-secondary" @click="createFromReview(item)">
                <Plus :size="14" />
                Создать навык
              </button>
              <button
                type="button"
                class="btn-ghost text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                @click="reject(item)"
              >
                <X :size="14" />
                Отклонить
              </button>
            </div>
          </div>
        </article>
      </div>
    </template>

    <template v-else-if="tab === 'skills'">
      <form class="card p-3" @submit.prevent="searchSkills">
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
          <label class="relative min-w-0 flex-1">
            <Search
              :size="16"
              class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              v-model="query"
              class="input pl-9"
              placeholder="Поиск по навыку или алиасу"
              aria-label="Поиск по словарю"
            />
          </label>
          <select v-model="skillTypeFilter" class="input sm:w-44" aria-label="Тип навыка">
            <option value="">Все типы</option>
            <option value="TECH">Технический</option>
            <option value="TOOL">Инструмент</option>
            <option value="DOMAIN">Предметная область</option>
            <option value="SOFT">Гибкий</option>
            <option value="OTHER">Другой</option>
          </select>
          <select v-model="skillStatusFilter" class="input sm:w-40" aria-label="Статус навыка">
            <option value="ACTIVE">Активные</option>
            <option value="DEPRECATED">В архиве</option>
          </select>
          <button type="submit" class="btn-primary shrink-0" :disabled="searching">
            <Loader2 v-if="searching" :size="16" class="animate-spin" />
            <Search v-else :size="16" />
            Найти
          </button>
          <button type="button" class="btn-secondary shrink-0" @click="openCreateSkill">
            <Plus :size="16" />
            Добавить навык
          </button>
        </div>
      </form>

      <div
        v-if="skills.length === 0"
        class="card flex flex-col items-center justify-center px-6 py-16 text-center"
      >
        <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
          <BookOpen :size="26" />
        </span>
        <h2 class="mt-4 text-lg font-semibold text-slate-900">Навыки не найдены</h2>
        <p class="mt-1 max-w-md text-sm text-slate-500">
          Попробуйте изменить запрос, тип или статус.
        </p>
        <button class="btn-secondary mt-4" @click="openCreateSkill">
          <Plus :size="16" />
          Добавить навык
        </button>
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article v-for="skill in skills" :key="skill.id" class="card flex flex-col p-5">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <h2 class="truncate text-lg font-semibold text-slate-900">
                {{ skill.canonicalName }}
              </h2>
              <div class="mt-2 flex flex-wrap gap-1.5">
                <span class="badge-neutral">{{ skillTypeLabel(skill.skillType) }}</span>
                <span
                  :class="skill.status === 'DEPRECATED' ? 'badge-warning' : 'badge-success'"
                >
                  {{ skillStatusLabel(skill.status) }}
                </span>
              </div>
            </div>
            <span class="badge-neutral shrink-0">{{ aliasCount(skill.aliases.length) }}</span>
          </div>

          <div class="mt-4 flex flex-wrap gap-1.5">
            <div
              v-for="alias in skill.aliases"
              :key="alias.id"
              class="inline-flex items-center gap-1 rounded-md bg-slate-50 px-2 py-1 text-xs"
            >
              <span :class="alias.canonical ? 'font-medium text-slate-900' : 'text-slate-600'">
                {{ alias.raw }}
              </span>
              <span v-if="alias.canonical" class="text-[10px] uppercase text-emerald-700">
                канон
              </span>
              <button
                v-else
                type="button"
                class="text-indigo-600 hover:underline"
                title="Сделать каноническим алиасом"
                @click="makeCanonical(skill, alias.id)"
              >
                канон
              </button>
            </div>
            <span v-if="skill.aliases.length === 0" class="text-xs text-slate-400">
              Нет алиасов
            </span>
          </div>

          <form
            class="mt-auto flex gap-2 border-t border-slate-100 pt-4"
            @submit.prevent="bindAlias(skill)"
          >
            <input
              v-model="aliasDrafts[skill.id]"
              class="input"
              placeholder="Новый алиас"
              :aria-label="`Новый алиас для ${skill.canonicalName}`"
            />
            <button
              type="submit"
              class="btn-secondary shrink-0"
              :disabled="!(aliasDrafts[skill.id] ?? '').trim()"
            >
              <Plus :size="14" />
              Добавить
            </button>
          </form>
        </article>
      </div>
    </template>

    <template v-else>
      <form class="card p-5" @submit.prevent="startGeneration">
        <div class="flex items-start gap-3">
          <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
            <Sparkles :size="18" />
          </span>
          <div>
            <h2 class="text-base font-semibold text-slate-900">Сгенерировать навыки</h2>
            <p class="mt-0.5 text-sm text-slate-500">
              Укажите специальность — черновики появятся ниже и попадут в словарь только после
              подтверждения.
            </p>
          </div>
        </div>

        <div class="mt-5 grid gap-4 sm:grid-cols-[minmax(0,1fr)_10rem]">
          <div>
            <label class="label" for="generation-specialization">Специальность</label>
            <input
              id="generation-specialization"
              v-model="specialization"
              class="input"
              placeholder="Например, электрик"
              required
            />
          </div>
          <div>
            <label class="label" for="generation-max">Максимум навыков</label>
            <input
              id="generation-max"
              v-model.number="maxSkills"
              class="input"
              type="number"
              min="1"
              max="50"
              step="1"
            />
          </div>
          <div class="sm:col-span-2">
            <label class="label" for="generation-note">
              Примечание <span class="font-normal text-slate-400">(необязательно)</span>
            </label>
            <input
              id="generation-note"
              v-model="generationNote"
              class="input"
              placeholder="Регион, отрасль или что не нужно включать"
            />
          </div>
        </div>

        <div class="mt-4 flex justify-end">
          <button type="submit" class="btn-primary" :disabled="generating || !specialization.trim()">
            <Loader2 v-if="generating" :size="16" class="animate-spin" />
            <Sparkles v-else :size="16" />
            {{ generating ? 'Генерируем…' : 'Сгенерировать' }}
          </button>
        </div>
      </form>

      <div v-if="jobs.length" class="space-y-3">
        <div class="flex items-center justify-between gap-3">
          <h2 class="text-base font-semibold text-slate-900">История генераций</h2>
          <span class="text-xs text-slate-500">{{ jobs.length }}</span>
        </div>
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <button
            v-for="job in jobs"
            :key="job.id"
            type="button"
            class="card p-4 text-left transition hover:border-indigo-200"
            :class="activeJob?.id === job.id ? 'ring-2 ring-indigo-300' : ''"
            @click="selectJob(job)"
          >
            <div class="flex items-start justify-between gap-2">
              <span class="truncate font-semibold text-slate-900">{{ job.specialization }}</span>
              <span
                class="shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium"
                :class="jobStatusClass(job.status)"
              >
                {{ jobStatusLabel(job.status) }}
              </span>
            </div>
            <p class="mt-2 text-xs text-slate-500">
              {{ formatDate(job.createdAt) }} · {{ skillsCount(job.skills.length) }}
            </p>
          </button>
        </div>
      </div>

      <article v-if="activeJob" class="card overflow-hidden">
        <div class="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 class="text-lg font-semibold text-slate-900">
              {{ activeJob.specialization }}
            </h2>
            <p class="mt-0.5 text-sm text-slate-500">
              Черновик от {{ formatDate(activeJob.createdAt) }} ·
              выбрано {{ selectedCount }} из {{ activeJob.skills.length }}
            </p>
          </div>
          <span
            class="rounded-md px-2 py-0.5 text-[11px] font-medium"
            :class="jobStatusClass(activeJob.status)"
          >
            {{ jobStatusLabel(activeJob.status) }}
          </span>
        </div>

        <p v-if="activeJob.error" class="border-b border-rose-100 bg-rose-50 px-5 py-3 text-sm text-rose-700">
          {{ activeJob.error }}
        </p>

        <div v-if="activeJob.skills.length === 0" class="px-5 py-10 text-center text-sm text-slate-500">
          В этом черновике нет навыков.
        </div>

        <div v-else class="divide-y divide-slate-100">
          <div
            v-for="skill in activeJob.skills"
            :key="skill.id"
            class="px-5 py-4"
            :class="!skillSelectable(skill) ? 'opacity-60' : ''"
          >
            <label class="flex items-start gap-3">
              <input
                v-model="selectedSkills[skill.id]"
                type="checkbox"
                class="mt-1 h-4 w-4 accent-indigo-600"
                :disabled="!skillSelectable(skill)"
              />
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="font-medium text-slate-900">{{ skill.canonical }}</span>
                  <span class="badge-neutral">{{ skillTypeLabel(skill.skillType) }}</span>
                  <span v-if="skill.targetSkillName" class="badge-success">
                    Свяжется с «{{ skill.targetSkillName }}»
                  </span>
                  <span v-if="skill.conflictSkillName" class="badge-warning">
                    Конфликт с «{{ skill.conflictSkillName }}»
                  </span>
                </div>

                <div v-if="skill.aliases.length" class="mt-2 flex flex-wrap gap-1.5">
                  <label
                    v-for="alias in skill.aliases"
                    :key="alias.id"
                    class="inline-flex items-center gap-1.5 rounded-md bg-slate-50 px-2 py-1 text-xs"
                    :class="alias.conflictSkillName ? 'text-rose-600' : 'text-slate-700'"
                  >
                    <input
                      v-model="selectedAliases[alias.id]"
                      type="checkbox"
                      class="h-3.5 w-3.5 accent-indigo-600"
                      :disabled="!aliasSelectable(skill, alias)"
                    />
                    {{ alias.text }}
                    <span v-if="alias.conflictSkillName">
                      ↔ {{ alias.conflictSkillName }}
                    </span>
                  </label>
                </div>
                <p v-else class="mt-1 text-xs text-slate-400">Без алиасов</p>
              </div>
            </label>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2 border-t border-slate-100 px-5 py-4">
          <span class="mr-auto text-xs text-slate-500">
            Выбрано {{ selectedCount }} из {{ activeJob.skills.length }}
          </span>
          <template v-if="activeJobEditable">
            <button type="button" class="btn-secondary px-3 py-1.5 text-xs" @click="selectAll">
              Выбрать все
            </button>
            <button type="button" class="btn-ghost px-3 py-1.5 text-xs" @click="clearAll">
              Снять
            </button>
          </template>
          <button
            type="button"
            class="btn-primary"
            :disabled="!activeJobEditable || selectedCount === 0"
            @click="approve"
          >
            <Check :size="16" />
            Подтвердить выбранное
          </button>
          <button
            type="button"
            class="btn-secondary text-rose-600"
            :disabled="!activeJobEditable"
            @click="rejectJob"
          >
            <X :size="16" />
            Отклонить черновик
          </button>
        </div>
      </article>
    </template>

    <AppModal
      :open="newSkillOpen"
      title="Новый навык"
      description="Добавьте каноническое название и тип навыка."
      @close="newSkillOpen = false"
    >
      <form class="space-y-4" @submit.prevent="createNewSkill">
        <div>
          <label class="label" for="new-skill-name">Название</label>
          <input
            id="new-skill-name"
            v-model="newSkillName"
            class="input"
            placeholder="Например, PostgreSQL"
            maxlength="128"
          />
        </div>
        <div>
          <label class="label" for="new-skill-type">Тип</label>
          <select id="new-skill-type" v-model="newSkillType" class="input">
            <option value="TECH">Технический</option>
            <option value="TOOL">Инструмент</option>
            <option value="DOMAIN">Предметная область</option>
            <option value="SOFT">Гибкий</option>
            <option value="OTHER">Другой</option>
          </select>
        </div>
        <p v-if="newSkillError" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {{ newSkillError }}
        </p>
        <div class="flex justify-end gap-2">
          <button type="button" class="btn-secondary" :disabled="creatingSkill" @click="newSkillOpen = false">
            Отмена
          </button>
          <button type="submit" class="btn-primary" :disabled="creatingSkill || !newSkillName.trim()">
            <Loader2 v-if="creatingSkill" :size="16" class="animate-spin" />
            <Plus v-else :size="16" />
            {{ creatingSkill ? 'Создаём…' : 'Создать' }}
          </button>
        </div>
      </form>
    </AppModal>
  </section>
</template>
