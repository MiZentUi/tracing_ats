<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp, History, Quote, Trash2 } from '@lucide/vue'
import type { SkillKind } from '@/api/generated'
import type { EditableRequirement } from '@/types'
import { kindLabels } from '@/lib/labels'

const props = defineProps<{
  requirement: EditableRequirement
  duplicate?: boolean
}>()

const emit = defineEmits<{
  update: [patch: Partial<EditableRequirement>]
  remove: []
}>()

const showDetails = ref(false)

const sourceLabel = computed(() => {
  switch (props.requirement.kindSource) {
    case 'HISTORY':
      return 'История'
    case 'HR':
      return 'HR'
    default:
      return 'LLM'
  }
})

const sourceHint = computed(() => {
  const history = props.requirement.history
  if (!history) {
    return 'Тип требования предложен моделью или изменён HR'
  }
  const llm = props.requirement.llmKind ? kindLabels[props.requirement.llmKind] : '—'
  return `История HR: ${history.must} раз обязательно, ${history.nice} раз желательно, ${history.optional} раз «научим». LLM предлагала: ${llm}`
})

const historyConflict = computed(
  () =>
    props.requirement.kindSource === 'HISTORY' &&
    props.requirement.llmKind !== null &&
    props.requirement.kind !== props.requirement.llmKind,
)

const historyRows = computed(() => {
  const history = props.requirement.history
  if (!history) {
    return []
  }
  const total = history.must + history.nice + history.optional
  if (total === 0) {
    return []
  }
  return [
    { label: 'Обязательно', count: history.must, color: 'bg-rose-400' },
    { label: 'Желательно', count: history.nice, color: 'bg-indigo-400' },
    { label: 'Научим', count: history.optional, color: 'bg-emerald-400' },
  ]
    .filter((row) => row.count > 0)
    .map((row) => ({ ...row, percent: Math.round((row.count / total) * 100) }))
})

const hasDetails = computed(
  () => !!props.requirement.quote || !!props.requirement.clarityWarning || historyRows.value.length > 0,
)

const kindOptions: Array<{ value: SkillKind; label: string }> = [
  { value: 'MUST', label: kindLabels.MUST },
  { value: 'NICE', label: kindLabels.NICE },
  { value: 'OPTIONAL', label: kindLabels.OPTIONAL },
]

function updateSkill(event: Event) {
  emit('update', { skill: (event.target as HTMLInputElement).value })
}

function updateYears(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update', { yearsRequired: value === '' ? null : Number(value) })
}

function updateKind(event: Event) {
  emit('update', { kind: (event.target as HTMLSelectElement).value as SkillKind })
}
</script>

<template>
  <article
    data-testid="requirement-row"
    class="px-4 py-3 transition hover:bg-slate-50/60"
    :class="duplicate ? 'bg-red-50/40' : ''"
  >
    <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_10rem_6.5rem_7rem_5.5rem] md:items-start">
      <div class="min-w-0">
        <input
          v-if="requirement.skill !== null"
          :value="requirement.skill ?? ''"
          class="w-full rounded-lg border border-transparent bg-transparent px-1 py-1 text-sm font-semibold text-slate-900 outline-none transition hover:border-slate-200 focus:border-indigo-400 focus:bg-white focus:ring-4 focus:ring-indigo-100"
          aria-label="Название навыка"
          placeholder="Название навыка"
          @input="updateSkill"
        />
        <span v-else class="block px-1 py-1 text-sm font-medium text-slate-500">
          Расплывчатое требование
        </span>

        <div class="mt-1 flex flex-wrap items-center gap-1.5 px-1">
          <span :class="requirement.normalizationStatus === 'MATCHED' ? 'badge-success' : 'badge-warning'">
            {{ requirement.normalizationStatus === 'MATCHED' ? 'из словаря' : 'новый навык' }}
          </span>
          <span v-if="duplicate" class="badge-danger">дубликат</span>
        </div>
      </div>

      <select
        :value="requirement.kind"
        class="input py-1.5 text-sm"
        aria-label="Тип требования"
        @change="updateKind"
      >
        <option v-for="kind in kindOptions" :key="kind.value" :value="kind.value">
          {{ kind.label }}
        </option>
      </select>

      <label class="flex items-center gap-1.5 text-xs text-slate-500">
        <input
          :value="requirement.yearsRequired ?? ''"
          type="number"
          min="0"
          max="50"
          step="0.5"
          class="w-16 rounded-lg border border-slate-300 px-2 py-1.5 text-sm text-slate-900 outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
          aria-label="Лет опыта"
          @input="updateYears"
        />
        лет
      </label>

      <span
        :class="requirement.kindSource === 'HISTORY' ? 'badge-info' : 'badge-neutral'"
        class="w-fit"
        :title="sourceHint"
      >
        {{ sourceLabel }}
      </span>

      <div class="flex items-center justify-end gap-1">
        <button
          v-if="hasDetails"
          type="button"
          class="icon-btn text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          :title="showDetails ? 'Скрыть подробности' : 'Показать цитату и историю'"
          @click="showDetails = !showDetails"
        >
          <ChevronUp v-if="showDetails" :size="16" />
          <ChevronDown v-else :size="16" />
        </button>
        <button
          type="button"
          class="icon-btn text-slate-400 hover:bg-red-50 hover:text-red-600"
          title="Удалить требование"
          @click="emit('remove')"
        >
          <Trash2 :size="16" />
        </button>
      </div>
    </div>

    <p
      v-if="duplicate"
      class="mt-2 rounded-lg bg-red-50 px-2 py-1 text-xs font-medium text-red-700"
    >
      Дубликат навыка — сохранить шаблон нельзя
    </p>

    <div v-if="showDetails" class="mt-3 space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3">
      <div v-if="requirement.history && historyRows.length > 0">
        <div class="flex items-center gap-1.5 text-xs font-semibold text-indigo-800">
          <History :size="13" />
          История HR
        </div>
        <div class="mt-2 space-y-1.5">
          <div
            v-for="row in historyRows"
            :key="row.label"
            class="flex items-center gap-2 text-[11px] text-indigo-900/70"
          >
            <span class="w-24 shrink-0 font-medium">{{ row.label }}</span>
            <span class="h-1.5 flex-1 overflow-hidden rounded-full bg-white/80">
              <span class="block h-full rounded-full" :class="row.color" :style="{ width: `${row.percent}%` }" />
            </span>
            <span class="w-8 text-right font-semibold">{{ row.percent }}%</span>
          </div>
        </div>
        <p v-if="historyConflict" class="mt-2 text-xs leading-5 text-indigo-800">
          LLM предлагала <strong>{{ requirement.llmKind ? kindLabels[requirement.llmKind] : '—' }}</strong>,
          но история HR переопределила тип.
        </p>
      </div>

      <blockquote
        v-if="requirement.quote"
        class="flex gap-2 rounded-lg bg-white px-3 py-2 text-sm leading-6 text-slate-600"
      >
        <Quote :size="14" class="mt-1 shrink-0 text-slate-400" />
        <span>{{ requirement.quote }}</span>
      </blockquote>

      <p
        v-if="requirement.clarityWarning"
        class="rounded-lg bg-amber-50 px-3 py-2 text-sm leading-6 text-amber-800 ring-1 ring-inset ring-amber-100"
      >
        {{ requirement.clarityWarning }}
      </p>
    </div>
  </article>
</template>
