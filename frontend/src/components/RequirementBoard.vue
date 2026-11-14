<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp, GripVertical, Plus, Trash2 } from '@lucide/vue'
import type { SkillKind } from '@/api/generated'
import type { EditableRequirement } from '@/types'
import { educationLevelOptions, educationLevelLabel, kindLabels } from '@/lib/labels'
import RequirementDetails from './RequirementDetails.vue'

const props = defineProps<{
  requirements: EditableRequirement[]
  duplicateKeys?: Set<string>
  highlightedKey?: string | null
}>()

const emit = defineEmits<{
  update: [key: string, patch: Partial<EditableRequirement>]
  remove: [key: string]
  add: [kind: SkillKind]
  addEducation: []
}>()

const columns: Array<{ kind: SkillKind; title: string; hint: string; accent: string; dot: string }> = [
  {
    kind: 'MUST',
    title: 'Обязательно',
    hint: 'Без этого кандидат не подходит',
    accent: 'border-rose-200 bg-rose-50/40',
    dot: 'bg-rose-400',
  },
  {
    kind: 'NICE',
    title: 'Желательно',
    hint: 'Плюс к оценке, но не блокирует',
    accent: 'border-indigo-200 bg-indigo-50/40',
    dot: 'bg-indigo-400',
  },
  {
    kind: 'OPTIONAL',
    title: 'Научим',
    hint: 'Можно освоить на месте',
    accent: 'border-emerald-200 bg-emerald-50/40',
    dot: 'bg-emerald-400',
  },
]

const draggedKey = ref<string | null>(null)
const dragOver = ref<SkillKind | null>(null)
const expanded = ref<Set<string>>(new Set())

const kindOptions = (Object.keys(kindLabels) as SkillKind[]).map((kind) => ({
  value: kind,
  label: kindLabels[kind],
}))

function normalizedSkill(skill: string | null): string {
  return (skill ?? '').trim().toLowerCase()
}

function isDuplicate(item: EditableRequirement): boolean {
  if (item.educationLevel) {
    return false
  }
  return props.duplicateKeys?.has(normalizedSkill(item.skill)) ?? false
}

const skillRequirements = computed(() =>
  props.requirements.filter((item) => !item.qualification && !item.educationLevel),
)
const qualificationRequirements = computed(() =>
  props.requirements.filter((item) => item.qualification),
)
const educationRequirements = computed(() =>
  props.requirements.filter((item) => item.educationLevel),
)

function requirementsFor(kind: SkillKind): EditableRequirement[] {
  return skillRequirements.value.filter((item) => item.kind === kind)
}

function displayName(item: EditableRequirement): string {
  return (
    item.educationSpecialty?.trim() ||
    item.skill?.trim() ||
    item.quote?.slice(0, 48) ||
    'Без названия'
  )
}

function hasDetails(item: EditableRequirement): boolean {
  return !!item.quote || !!item.clarityWarning
}

function isExpanded(item: EditableRequirement): boolean {
  return expanded.value.has(item.key)
}

function toggleDetails(item: EditableRequirement) {
  const next = new Set(expanded.value)
  if (next.has(item.key)) {
    next.delete(item.key)
  } else {
    next.add(item.key)
  }
  expanded.value = next
}

function updateSkill(event: Event, item: EditableRequirement) {
  const value = (event.target as HTMLInputElement).value
  emit('update', item.key, { skill: value.trim() === '' ? null : value })
}

function updateEducationSpecialty(event: Event, item: EditableRequirement) {
  const value = (event.target as HTMLInputElement).value
  emit('update', item.key, { educationSpecialty: value.trim() === '' ? null : value })
}

function updateEducationLevel(event: Event, item: EditableRequirement) {
  emit('update', item.key, {
    educationLevel: (event.target as HTMLSelectElement).value || null,
  })
}

function updateYears(event: Event, item: EditableRequirement) {
  const value = (event.target as HTMLInputElement).value
  emit('update', item.key, { yearsRequired: value === '' ? null : Number(value) })
}

function onDragStart(event: DragEvent, item: EditableRequirement) {
  draggedKey.value = item.key
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', item.key)
  }
}

function onDrop(event: DragEvent, kind: SkillKind) {
  const key = draggedKey.value ?? event.dataTransfer?.getData('text/plain') ?? ''
  draggedKey.value = null
  dragOver.value = null
  const item = props.requirements.find((candidate) => candidate.key === key)
  if (item && item.kind !== kind) {
    emit('update', item.key, { kind })
  }
}

function onMove(event: Event, item: EditableRequirement) {
  const kind = (event.target as HTMLSelectElement).value as SkillKind
  if (item.kind !== kind) {
    emit('update', item.key, { kind })
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="grid gap-4 lg:grid-cols-3">
      <section
        v-for="column in columns"
        :key="column.kind"
        :data-column="column.kind"
        class="flex min-h-48 flex-col rounded-xl border p-3 transition"
        :class="[
          column.accent,
          dragOver === column.kind ? 'ring-2 ring-indigo-300 ring-offset-2' : '',
        ]"
        @dragover.prevent="dragOver = column.kind"
        @dragleave.prevent="dragOver = null"
        @drop.prevent="onDrop($event, column.kind)"
      >
        <header class="flex items-start justify-between gap-2 px-1 pb-3">
          <div>
            <h3 class="flex items-center gap-2 text-sm font-semibold text-slate-900">
              <span class="h-2.5 w-2.5 rounded-full" :class="column.dot" />
              {{ column.title }}
            </h3>
            <p class="mt-0.5 text-xs text-slate-500">{{ column.hint }}</p>
          </div>
          <span class="badge-neutral shrink-0">{{ requirementsFor(column.kind).length }}</span>
        </header>

        <div class="flex-1 space-y-2">
          <article
            v-for="item in requirementsFor(column.kind)"
            :key="item.key"
            data-testid="board-card"
            class="cursor-grab rounded-lg border border-slate-200 bg-white p-2.5 shadow-sm transition active:cursor-grabbing"
            :class="highlightedKey === item.key ? 'ring-2 ring-indigo-300 ring-offset-2' : ''"
            draggable="true"
            @dragstart="onDragStart($event, item)"
            @dragend="draggedKey = null; dragOver = null"
          >
            <div class="flex items-start gap-1.5">
              <GripVertical :size="14" class="mt-1 shrink-0 text-slate-300" />
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-1.5">
                  <input
                    :value="item.skill ?? ''"
                    class="min-w-0 flex-1 rounded-md border border-transparent bg-transparent px-1 py-0.5 text-sm font-semibold text-slate-900 outline-none transition placeholder:font-normal placeholder:text-slate-400 hover:border-slate-200 focus:border-indigo-300 focus:bg-white"
                    aria-label="Название навыка"
                    placeholder="Название навыка"
                    @input="updateSkill($event, item)"
                  />
                  <span v-if="isDuplicate(item)" class="badge-danger shrink-0">дубликат</span>
                </div>

                <div class="mt-1.5 flex items-center gap-1.5">
                  <select
                    :value="item.kind"
                    class="input min-w-0 flex-1 py-1 text-xs"
                    :aria-label="`Тип требования: ${displayName(item)}`"
                    @change="onMove($event, item)"
                  >
                    <option v-for="option in kindOptions" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                  <label class="flex items-center gap-1 text-[11px] text-slate-500">
                    <input
                      :value="item.yearsRequired ?? ''"
                      type="number"
                      min="0"
                      max="50"
                      step="1"
                      class="w-12 rounded-md border border-slate-300 px-1.5 py-1 text-xs text-slate-900 outline-none focus:border-indigo-500"
                      aria-label="Лет опыта"
                      @input="updateYears($event, item)"
                    />
                    лет
                  </label>
                  <button
                    v-if="hasDetails(item)"
                    type="button"
                    class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                    :title="isExpanded(item) ? 'Скрыть подробности' : 'Показать подробности'"
                    @click="toggleDetails(item)"
                  >
                    <ChevronUp v-if="isExpanded(item)" :size="14" />
                    <ChevronDown v-else :size="14" />
                  </button>
                  <button
                    type="button"
                    class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
                    title="Удалить требование"
                    @click="emit('remove', item.key)"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>

                <div v-if="isExpanded(item)" class="mt-2">
                  <RequirementDetails :requirement="item" />
                </div>
              </div>
            </div>
          </article>

          <p
            v-if="requirementsFor(column.kind).length === 0"
            class="rounded-lg border border-dashed border-slate-300 bg-white/60 px-3 py-8 text-center text-xs text-slate-400"
          >
            Перетащите сюда карточку
          </p>
        </div>

        <button
          type="button"
          class="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-slate-300 bg-white/70 px-3 py-2 text-xs font-medium text-slate-500 transition hover:border-indigo-300 hover:bg-indigo-50/60 hover:text-indigo-700"
          @click="emit('add', column.kind)"
        >
          <Plus :size="14" />
          Добавить
        </button>
      </section>
    </div>

    <section v-if="qualificationRequirements.length" class="space-y-3 border-t border-slate-200 pt-5">
      <div class="flex items-end justify-between gap-3">
        <div>
          <h3 class="text-sm font-semibold text-slate-900">Квалификации</h3>
          <p class="mt-0.5 text-xs text-slate-500">
            Допуски, категории и аттестаты. Они не смешиваются с навыками в колонках.
          </p>
        </div>
        <span class="badge-neutral">{{ qualificationRequirements.length }}</span>
      </div>

      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="item in qualificationRequirements"
          :key="item.key"
          data-testid="qualification-card"
          class="rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
        >
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-1.5">
                <span class="badge-info shrink-0">Квалификация</span>
                <span v-if="isDuplicate(item)" class="badge-danger shrink-0">дубликат</span>
              </div>
              <input
                :value="item.skill ?? ''"
                class="mt-2 w-full rounded-md border border-transparent bg-transparent px-1 py-0.5 text-sm font-semibold text-slate-900 outline-none transition placeholder:font-normal placeholder:text-slate-400 hover:border-slate-200 focus:border-indigo-300 focus:bg-white"
                aria-label="Название квалификации"
                placeholder="Название квалификации"
                @input="updateSkill($event, item)"
              />
            </div>
          </div>

          <div class="mt-2 flex flex-wrap items-center gap-2">
            <select
              :value="item.kind"
              class="input min-w-0 flex-1 py-1 text-xs"
              :aria-label="`Тип требования: ${displayName(item)}`"
              @change="onMove($event, item)"
            >
              <option v-for="option in kindOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <label class="flex items-center gap-1 text-[11px] text-slate-500">
              <input
                :value="item.yearsRequired ?? ''"
                type="number"
                min="0"
                max="50"
                step="1"
                class="w-12 rounded-md border border-slate-300 px-1.5 py-1 text-xs text-slate-900 outline-none focus:border-indigo-500"
                aria-label="Лет опыта"
                @input="updateYears($event, item)"
              />
              лет
            </label>
            <button
              v-if="hasDetails(item)"
              type="button"
              class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              :title="isExpanded(item) ? 'Скрыть подробности' : 'Показать подробности'"
              @click="toggleDetails(item)"
            >
              <ChevronUp v-if="isExpanded(item)" :size="14" />
              <ChevronDown v-else :size="14" />
            </button>
            <button
              type="button"
              class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
              title="Удалить квалификацию"
              @click="emit('remove', item.key)"
            >
              <Trash2 :size="14" />
            </button>
          </div>

          <div v-if="isExpanded(item)" class="mt-2">
            <RequirementDetails :requirement="item" />
          </div>
        </article>
      </div>
    </section>

    <section class="space-y-3 border-t border-slate-200 pt-5">
      <div class="flex items-end justify-between gap-3">
        <div>
          <h3 class="text-sm font-semibold text-slate-900">Образование</h3>
          <p class="mt-0.5 text-xs text-slate-500">
            Уровень и специальность. Требования к образованию не смешиваются с навыками.
          </p>
        </div>
        <div class="flex items-center gap-2">
          <span class="badge-neutral">{{ educationRequirements.length }}</span>
          <button
            type="button"
            class="btn-secondary px-2.5 py-1 text-xs"
            data-testid="add-education-board"
            @click="emit('addEducation')"
          >
            <Plus :size="13" />
            Добавить
          </button>
        </div>
      </div>

      <div v-if="educationRequirements.length" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="item in educationRequirements"
          :key="item.key"
          data-testid="education-card"
          class="rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
        >
          <div class="flex items-center gap-1.5">
            <span class="badge-info shrink-0">Образование</span>
          </div>
          <input
            :value="item.educationSpecialty ?? ''"
            class="mt-2 w-full rounded-md border border-transparent bg-transparent px-1 py-0.5 text-sm font-semibold text-slate-900 outline-none transition placeholder:font-normal placeholder:text-slate-400 hover:border-slate-200 focus:border-indigo-300 focus:bg-white"
            aria-label="Специальность"
            placeholder="Специальность или направление"
            @input="updateEducationSpecialty($event, item)"
          />

          <div class="mt-2 flex flex-wrap items-center gap-2">
            <select
              :value="item.educationLevel ?? ''"
              class="input min-w-0 flex-1 py-1 text-xs"
              aria-label="Уровень образования"
              @change="updateEducationLevel($event, item)"
            >
              <option v-for="level in educationLevelOptions" :key="level.value" :value="level.value">
                {{ level.label }}
              </option>
            </select>
            <select
              :value="item.kind"
              class="input min-w-0 flex-1 py-1 text-xs"
              :aria-label="`Тип требования: ${displayName(item)}`"
              @change="onMove($event, item)"
            >
              <option v-for="option in kindOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
            <button
              v-if="hasDetails(item)"
              type="button"
              class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              :title="isExpanded(item) ? 'Скрыть подробности' : 'Показать подробности'"
              @click="toggleDetails(item)"
            >
              <ChevronUp v-if="isExpanded(item)" :size="14" />
              <ChevronDown v-else :size="14" />
            </button>
            <button
              type="button"
              class="icon-btn h-7 w-7 shrink-0 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
              title="Удалить требование к образованию"
              @click="emit('remove', item.key)"
            >
              <Trash2 :size="14" />
            </button>
          </div>

          <p class="mt-2 text-[11px] text-slate-400">
            {{ educationLevelLabel(item.educationLevel) }}
          </p>

          <div v-if="isExpanded(item)" class="mt-2">
            <RequirementDetails :requirement="item" />
          </div>
        </article>
      </div>
      <p
        v-else
        class="rounded-xl border border-dashed border-slate-300 bg-slate-50/60 px-4 py-6 text-center text-xs text-slate-400"
      >
        Пока нет требований к образованию. Добавьте уровень и специальность.
      </p>
    </section>
  </div>
</template>
