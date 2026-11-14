<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Copy,
  LayoutGrid,
  List,
  LoaderCircle,
  Plus,
  Save,
  Search,
  Sparkles,
  Trash2,
} from '@lucide/vue'
import type { ReferenceItem, SkillKind, Specialization, Vacancy } from '@/api/generated'
import { apiErrorCode, apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import RequirementBoard from '@/components/RequirementBoard.vue'
import RequirementRow from '@/components/RequirementRow.vue'
import { useToast } from '@/composables/useToast'
import { kindLabels } from '@/lib/labels'
import { levelLabel, toEditable, type EditableRequirement } from '@/types'

const props = defineProps<{ id: string }>()
const router = useRouter()
const { success, info, error: toastError } = useToast()

const vacancy = ref<Vacancy | null>(null)
const requirements = ref<EditableRequirement[]>([])
const title = ref('')
const sourceTextDraft = ref('')
const sourceTextarea = ref<HTMLTextAreaElement | null>(null)
const specialization = ref('')
const level = ref('none')
const specializations = ref<Specialization[]>([])
const levels = ref<ReferenceItem[]>([])
const snapshot = ref('')
const loading = ref(true)
const savingMode = ref<'save' | 'template' | null>(null)
const deleting = ref(false)
const deleteOpen = ref(false)
const useTemplateOpen = ref(false)
const viewMode = ref<'list' | 'board'>('board')
const requirementSearch = ref('')
const error = ref('')
const conflict = ref(false)
const savedMessage = ref('')
const highlightedKey = ref<string | null>(null)

const saving = computed(() => savingMode.value !== null)

// Грейд запрашивается только у IT-специализаций; у остальных уровень фиксирован как «none».
const needsLevel = computed(
  () => specializations.value.find((item) => item.code === specialization.value)?.it === true,
)

const isConfirmed = computed(() => vacancy.value?.template.is_confirmed === true)

function resizeSource() {
  const element = sourceTextarea.value
  if (!element) {
    return
  }
  element.style.height = 'auto'
  element.style.height = `${element.scrollHeight}px`
}

watch(sourceTextDraft, () => {
  void nextTick(resizeSource)
})

function serialize() {
  return JSON.stringify({
    title: title.value,
    sourceText: sourceTextDraft.value,
    specialization: specialization.value,
    level: level.value,
    requirements: requirements.value.map((item) => ({
      id: item.id,
      skill: item.skill,
      kind: item.kind,
      yearsRequired: item.yearsRequired,
      quote: item.quote,
      clarityWarning: item.clarityWarning,
    })),
  })
}

const dirty = computed(() => snapshot.value !== '' && serialize() !== snapshot.value)

const canSave = computed(
  () =>
    !!vacancy.value &&
    !saving.value &&
    !deleting.value &&
    duplicateKeys.value.size === 0 &&
    (dirty.value || !isConfirmed.value),
)

function apply(value: Vacancy) {
  vacancy.value = value
  title.value = value.title ?? ''
  sourceTextDraft.value = value.source_text ?? ''
  specialization.value = value.specialization
  level.value = value.level
  requirements.value = value.template.requirements.map(toEditable)
  snapshot.value = serialize()
  void nextTick(resizeSource)
}

async function load() {
  loading.value = true
  error.value = ''
  conflict.value = false
  try {
    const [vacancyResponse, specs, levelList] = await Promise.all([
      vacanciesApi.getVacancy({ id: props.id }),
      referenceApi.listSpecializations(),
      referenceApi.listLevels(),
    ])
    apply(vacancyResponse.data)
    specializations.value = specs.data
    levels.value = levelList.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function requirementsFor(kind: keyof typeof kindLabels) {
  return requirements.value.filter((item) => !item.qualification && item.kind === kind)
}

const qualificationCount = computed(
  () => requirements.value.filter((item) => item.qualification).length,
)

const kindCounters = computed(() =>
  (Object.keys(kindLabels) as Array<keyof typeof kindLabels>).map((kind) => ({
    kind,
    label: kindLabels[kind],
    count: requirementsFor(kind).length,
  })),
)

function normalizedSkill(skill: string | null) {
  return (skill ?? '').trim().toLowerCase()
}

const duplicateKeys = computed(() => {
  const counts = new Map<string, number>()
  for (const item of requirements.value) {
    const key = normalizedSkill(item.skill)
    if (key !== '') {
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
  }
  return new Set([...counts.entries()].filter(([, count]) => count > 1).map(([key]) => key))
})

const visibleRequirements = computed(() => {
  const query = requirementSearch.value.trim().toLowerCase()
  if (!query) {
    return requirements.value
  }
  return requirements.value.filter(
    (item) =>
      (item.skill ?? '').toLowerCase().includes(query) ||
      (item.quote ?? '').toLowerCase().includes(query),
  )
})

const visibleSkillRequirements = computed(() =>
  visibleRequirements.value.filter((item) => !item.qualification),
)
const visibleQualificationRequirements = computed(() =>
  visibleRequirements.value.filter((item) => item.qualification),
)

async function revealRequirement(key: string, focusSkill = false) {
  highlightedKey.value = key
  await nextTick()
  const element = document.querySelector(`[data-requirement-key="${key}"]`)
  element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  if (focusSkill) {
    const input = element?.querySelector<HTMLInputElement>('input[aria-label="Название навыка"]')
    input?.focus({ preventScroll: true })
  }
  window.setTimeout(() => {
    if (highlightedKey.value === key) {
      highlightedKey.value = null
    }
  }, 1800)
}

function updateRequirement(key: string, patch: Partial<EditableRequirement>) {
  const item = requirements.value.find((candidate) => candidate.key === key)
  if (item) {
    Object.assign(item, patch)
  }
}

function removeRequirement(key: string) {
  requirements.value = requirements.value.filter((item) => item.key !== key)
}

async function addRequirement(kind: SkillKind = 'MUST') {
  requirementSearch.value = ''
  const key = `new-${Date.now()}-${requirements.value.length}`
  requirements.value.push({
    key,
    id: null,
    skill: '',
    skillId: null,
    normalizationStatus: 'NEW',
    kind,
    kindSource: 'HR',
    llmKind: null,
    yearsRequired: null,
    quote: null,
    clarityWarning: null,
    history: null,
    qualification: false,
  })
  if (kind !== 'MUST') {
    info('Требование добавлено', `Колонка «${kindLabels[kind]}»`)
  }
  await revealRequirement(key, true)
}

function requestSave() {
  if (canSave.value) {
    void save('save')
  }
}

function requestUseTemplate() {
  if (canSave.value) {
    useTemplateOpen.value = true
  }
}

async function confirmUseTemplate() {
  useTemplateOpen.value = false
  await save('template')
}

async function save(mode: 'save' | 'template') {
  if (!vacancy.value || saving.value) {
    return
  }
  if (requirements.value.length === 0) {
    error.value = 'Нужно хотя бы одно требование'
    return
  }
  if (requirements.value.some((item) => !item.skill?.trim() && !item.quote)) {
    error.value = 'У требования должен быть навык или цитата'
    return
  }
  if (!sourceTextDraft.value.trim()) {
    error.value = 'Текст вакансии не может быть пустым'
    return
  }
  savingMode.value = mode
  error.value = ''
  savedMessage.value = ''
  conflict.value = false
  try {
    const response = await vacanciesApi.updateVacancyTemplate({
      id: props.id,
      updateTemplateRequest: {
        base_version: vacancy.value.template.version,
        specialization: specialization.value,
        level: needsLevel.value ? level.value : undefined,
        title: title.value.trim() || null,
        source_text: sourceTextDraft.value,
        confirm: mode === 'template' || isConfirmed.value,
        requirements: requirements.value.map((item) => ({
          id: item.id,
          skill: item.skill?.trim() || null,
          kind: item.kind,
          years_required: item.yearsRequired,
          quote: item.quote,
          clarity_warning: item.clarityWarning,
        })),
      },
    })
    apply(response.data)
    if (mode === 'template') {
      success('Шаблон сохранён')
      await router.push(`/vacancies/${props.id}`)
      return
    }
    savedMessage.value = 'Изменения сохранены'
    success('Изменения сохранены')
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    conflict.value = apiErrorCode(e) === 'VERSION_CONFLICT'
    toastError('Не удалось сохранить вакансию', message)
  } finally {
    savingMode.value = null
  }
}

async function confirmDeleteVacancy() {
  if (deleting.value) {
    return
  }
  deleting.value = true
  try {
    await vacanciesApi.deleteVacancy({ id: props.id })
    deleteOpen.value = false
    success('Вакансия удалена', 'Её вклад убран из истории типов')
    await router.push('/vacancies')
  } catch (e) {
    toastError('Не удалось удалить вакансию', apiErrorMessage(e))
  } finally {
    deleting.value = false
  }
}

async function copySource() {
  if (!sourceTextDraft.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(sourceTextDraft.value)
    success('Текст скопирован')
  } catch {
    toastError('Не удалось скопировать текст')
  }
}

onBeforeRouteLeave(() => {
  if (dirty.value && !saving.value && !deleting.value) {
    return window.confirm('Есть несохранённые изменения. Перейти без сохранения?')
  }
  return true
})

onMounted(load)
</script>

<template>
  <section class="space-y-5 pb-4">
    <div>
      <RouterLink
        :to="`/vacancies/${props.id}`"
        class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900"
      >
        <ArrowLeft :size="16" />
        К вакансии
      </RouterLink>
    </div>

    <div v-if="loading" class="space-y-4">
      <div class="skeleton h-9 w-1/3" />
      <div class="skeleton h-32" />
      <div class="skeleton h-96" />
    </div>

    <template v-else-if="vacancy">
      <header class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <p class="section-label">Редактирование вакансии</p>
          <h1 class="mt-1 truncate text-3xl font-semibold tracking-tight text-slate-900">
            {{ title || 'Без названия' }}
          </h1>
          <p class="mt-1 text-sm text-slate-500">
            {{ specializations.find((item) => item.code === specialization)?.name ?? specialization }}
            · {{ levelLabel(level) }}
          </p>
        </div>
      </header>

      <div
        v-if="savedMessage"
        class="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800"
      >
        <CheckCircle2 :size="16" />
        {{ savedMessage }}
      </div>

      <div
        v-if="error"
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
      >
        <span class="flex items-center gap-2">
          <AlertTriangle :size="16" />
          {{ error }}
        </span>
        <button v-if="conflict" class="btn-secondary shrink-0" @click="load">Перезагрузить</button>
      </div>

      <div
        v-if="duplicateKeys.size > 0"
        class="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
      >
        <AlertTriangle :size="16" />
        Найдены дубликаты навыков. Исправьте их, чтобы сохранить вакансию.
      </div>

      <div class="card p-5">
        <h2 class="text-base font-semibold text-slate-900">Параметры вакансии</h2>
        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <label class="label" for="editor-title">Название вакансии</label>
            <input id="editor-title" v-model="title" class="input" />
          </div>
          <div>
            <label class="label" for="editor-specialization">Специализация</label>
            <select id="editor-specialization" v-model="specialization" class="input">
              <option v-for="item in specializations" :key="item.code" :value="item.code">
                {{ item.name }}
              </option>
            </select>
          </div>
          <div>
            <label class="label" for="editor-level">Уровень</label>
            <select v-if="needsLevel" id="editor-level" v-model="level" class="input">
              <option v-for="item in levels" :key="item.code" :value="item.code">
                {{ item.name }}
              </option>
            </select>
            <p
              v-else
              class="flex h-[42px] items-center rounded-lg border border-dashed border-slate-300 bg-slate-50 px-3 text-sm text-slate-500"
            >
              Не требуется: специализация не IT
            </p>
          </div>
        </div>
      </div>

      <div class="card overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
          <div>
            <h2 class="text-base font-semibold text-slate-900">
              Требования <span class="text-slate-400">({{ requirements.length }})</span>
            </h2>
            <p class="mt-0.5 text-xs text-slate-500">
              <template v-for="(counter, index) in kindCounters" :key="counter.kind">
                <span v-if="index > 0"> · </span>
                {{ counter.label }} {{ counter.count }}
              </template>
              <span v-if="qualificationCount > 0"> · Квалификации {{ qualificationCount }}</span>
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <div
              class="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-0.5"
              role="group"
              aria-label="Режим редактирования требований"
            >
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition"
                :class="viewMode === 'board' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
                @click="viewMode = 'board'"
              >
                <LayoutGrid :size="14" />
                Доска
              </button>
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition"
                :class="viewMode === 'list' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
                @click="viewMode = 'list'"
              >
                <List :size="14" />
                Список
              </button>
            </div>
            <template v-if="viewMode === 'list'">
              <label class="relative">
                <Search
                  :size="14"
                  class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  v-model="requirementSearch"
                  class="input w-48 py-1.5 pl-8 text-xs"
                  placeholder="Найти требование"
                />
              </label>
              <button type="button" class="btn-secondary px-3 py-1.5 text-xs" @click="addRequirement('MUST')">
                <Plus :size="14" />
                Добавить
              </button>
            </template>
          </div>
        </div>

        <div v-if="viewMode === 'board'" class="p-4">
          <RequirementBoard
            :requirements="requirements"
            :duplicate-keys="duplicateKeys"
            :highlighted-key="highlightedKey"
            @update="updateRequirement"
            @remove="removeRequirement"
            @add="addRequirement"
          />
        </div>

        <div v-else>
          <div
            class="hidden gap-2 border-b border-slate-100 bg-slate-50/80 px-3 py-2 text-[11px] font-medium uppercase tracking-wide text-slate-400 md:grid md:grid-cols-[minmax(0,1fr)_9rem_5.5rem_4.5rem]"
          >
            <span>Требование</span>
            <span>Тип</span>
            <span>Опыт</span>
            <span class="text-right">Действия</span>
          </div>
          <div class="divide-y divide-slate-100">
            <div
              v-for="item in visibleSkillRequirements"
              :key="item.key"
              :data-requirement-key="item.key"
              class="transition-all duration-300"
              :class="highlightedKey === item.key ? 'ring-2 ring-inset ring-indigo-300' : ''"
            >
              <RequirementRow
                :requirement="item"
                :duplicate="duplicateKeys.has(normalizedSkill(item.skill))"
                @update="(patch) => updateRequirement(item.key, patch)"
                @remove="removeRequirement(item.key)"
              />
            </div>
          </div>

          <div v-if="visibleQualificationRequirements.length" class="border-t border-slate-200">
            <div
              class="hidden gap-2 border-b border-slate-100 bg-slate-50/80 px-3 py-2 text-[11px] font-medium uppercase tracking-wide text-slate-400 md:grid md:grid-cols-[minmax(0,1fr)_9rem_5.5rem_4.5rem]"
            >
              <span>Квалификация</span>
              <span>Тип</span>
              <span>Опыт</span>
              <span class="text-right">Действия</span>
            </div>
            <div class="divide-y divide-slate-100">
              <div
                v-for="item in visibleQualificationRequirements"
                :key="item.key"
                :data-requirement-key="item.key"
                class="transition-all duration-300"
                :class="highlightedKey === item.key ? 'ring-2 ring-inset ring-indigo-300' : ''"
              >
                <RequirementRow
                  :requirement="item"
                  :duplicate="duplicateKeys.has(normalizedSkill(item.skill))"
                  @update="(patch) => updateRequirement(item.key, patch)"
                  @remove="removeRequirement(item.key)"
                />
              </div>
            </div>
          </div>

          <p
            v-if="visibleSkillRequirements.length === 0 && visibleQualificationRequirements.length === 0"
            class="px-4 py-10 text-center text-sm text-slate-400"
          >
            {{ requirements.length === 0 ? 'Пока нет требований. Добавьте первое.' : 'Ничего не найдено по запросу.' }}
          </p>
        </div>
      </div>

      <article class="card">
        <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
          <h2 class="text-sm font-semibold text-slate-900">Текст вакансии</h2>
          <button class="btn-secondary px-3 py-1.5 text-xs" @click="copySource">
            <Copy :size="14" />
            Скопировать
          </button>
        </div>
        <textarea
          ref="sourceTextarea"
          v-model="sourceTextDraft"
          data-testid="vacancy-source-text"
          class="min-h-64 w-full resize-none overflow-hidden border-0 bg-transparent px-4 py-4 font-mono text-sm leading-6 text-slate-700 outline-none"
          aria-label="Текст вакансии"
          @input="resizeSource"
        />
      </article>

      <div class="sticky bottom-3 z-20">
        <div class="card flex flex-wrap items-center gap-2 p-3 shadow-xl backdrop-blur">
          <button class="btn-secondary" :disabled="!canSave" @click="requestUseTemplate">
            <LoaderCircle v-if="savingMode === 'template'" :size="16" class="animate-spin" />
            <Sparkles v-else :size="16" />
            {{ savingMode === 'template' ? 'Сохраняем…' : 'Использовать как шаблон' }}
          </button>
          <span
            class="mr-auto flex items-center gap-2 px-1 text-xs font-medium"
            :class="dirty ? 'text-amber-700' : 'text-slate-400'"
          >
            <span class="h-2 w-2 rounded-full" :class="dirty ? 'bg-amber-400' : 'bg-slate-300'" />
            {{ dirty ? 'Есть несохранённые изменения' : 'Изменений нет' }}
          </span>
          <button class="btn-danger" :disabled="deleting || saving" @click="deleteOpen = true">
            <Trash2 :size="16" />
            Удалить
          </button>
          <button class="btn-primary" :disabled="!canSave" @click="requestSave">
            <LoaderCircle v-if="savingMode === 'save'" :size="16" class="animate-spin" />
            <Save v-else :size="16" />
            {{ savingMode === 'save' ? 'Сохраняем…' : 'Сохранить' }}
          </button>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :open="useTemplateOpen"
      title="Использовать как шаблон?"
      message="Текущие требования и текст вакансии будут сохранены как шаблон."
      confirm-label="Использовать"
      @cancel="useTemplateOpen = false"
      @confirm="confirmUseTemplate"
    />

    <ConfirmDialog
      :open="deleteOpen"
      title="Удалить вакансию?"
      message="Вакансия будет удалена без возможности восстановления."
      confirm-label="Удалить"
      danger
      @cancel="deleteOpen = false"
      @confirm="confirmDeleteVacancy"
    />
  </section>
</template>
