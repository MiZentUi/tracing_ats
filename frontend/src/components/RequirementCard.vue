<script setup lang="ts">
import { computed } from 'vue'
import { History, Quote, Trash2 } from '@lucide/vue'
import type { SkillKind } from '@/api/generated'
import type { EditableRequirement } from '@/types'

const props = defineProps<{
  requirement: EditableRequirement
  duplicate?: boolean
}>()

const emit = defineEmits<{
  update: [patch: Partial<EditableRequirement>]
  remove: []
}>()

const kinds: Array<{ value: SkillKind; label: string }> = [
  { value: 'MUST', label: 'Обязательно' },
  { value: 'NICE', label: 'Желательно' },
  { value: 'OPTIONAL', label: 'Научим' },
]

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
    return ''
  }
  const llm = props.requirement.llmKind ?? '—'
  return `Предложено по истории HR: ${history.must} раз обязательно, ${history.nice} раз желательно, ${history.optional} раз опционально; LLM предлагала: ${llm}`
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
    { label: 'MUST', count: history.must, color: 'bg-rose-400' },
    { label: 'NICE', count: history.nice, color: 'bg-indigo-400' },
    { label: 'OPTIONAL', count: history.optional, color: 'bg-emerald-400' },
  ]
    .filter((row) => row.count > 0)
    .map((row) => ({ ...row, percent: Math.round((row.count / total) * 100) }))
})

function updateSkill(event: Event) {
  emit('update', { skill: (event.target as HTMLInputElement).value })
}

function updateYears(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update', { yearsRequired: value === '' ? null : Number(value) })
}
</script>

<template>
  <article
    data-testid="requirement-card"
    class="card p-4 transition hover:border-slate-300"
    :class="duplicate ? '!border-red-300 ring-2 ring-red-100' : ''"
  >
    <div class="flex flex-wrap items-start gap-2">
      <input
        v-if="requirement.skill !== null"
        :value="requirement.skill ?? ''"
        class="min-w-40 flex-1 rounded-lg border border-transparent bg-transparent px-1 py-0.5 text-base font-semibold text-slate-900 outline-none transition hover:border-slate-200 focus:border-indigo-400 focus:bg-white focus:ring-4 focus:ring-indigo-100"
        aria-label="Название навыка"
        placeholder="Название навыка"
        @input="updateSkill"
      />
      <span v-else class="min-w-40 flex-1 px-1 text-sm font-medium text-slate-500">
        Расплывчатое требование
      </span>

      <span :class="requirement.normalizationStatus === 'MATCHED' ? 'badge-success' : 'badge-warning'">
        {{ requirement.normalizationStatus === 'MATCHED' ? 'из словаря' : 'новый навык' }}
      </span>

      <span
        :class="requirement.kindSource === 'HISTORY' ? 'badge-info' : requirement.kindSource === 'HR' ? 'badge-neutral' : 'badge-neutral'"
        :title="sourceHint"
      >
        {{ sourceLabel }}
      </span>
    </div>

    <p v-if="duplicate" class="mt-2 rounded-lg bg-red-50 px-2 py-1 text-xs font-medium text-red-700">
      Дубликат навыка — сохранить шаблон нельзя
    </p>

    <div class="mt-3 flex flex-wrap items-center gap-3">
      <div class="inline-flex rounded-xl bg-slate-100 p-1" role="group" aria-label="Тип требования">
        <button
          v-for="kind in kinds"
          :key="kind.value"
          type="button"
          :data-kind="kind.value"
          class="rounded-lg px-2.5 py-1.5 text-xs font-semibold transition"
          :class="
            requirement.kind === kind.value
              ? kind.value === 'MUST'
                ? 'bg-white text-rose-700 shadow-sm'
                : kind.value === 'NICE'
                  ? 'bg-white text-indigo-700 shadow-sm'
                  : 'bg-white text-emerald-700 shadow-sm'
              : 'text-slate-500 hover:text-slate-800'
          "
          @click="emit('update', { kind: kind.value })"
        >
          {{ kind.label }}
        </button>
      </div>

      <label class="flex items-center gap-2 text-xs text-slate-500">
        Лет опыта
        <input
          :value="requirement.yearsRequired ?? ''"
          type="number"
          min="0"
          max="50"
          step="0.5"
          class="w-20 rounded-lg border border-slate-300 px-2 py-1.5 text-sm text-slate-900 outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
          aria-label="Лет опыта"
          @input="updateYears"
        />
      </label>

      <button class="icon-btn ml-auto text-slate-400 hover:bg-red-50 hover:text-red-600" title="Удалить требование" @click="emit('remove')">
        <Trash2 :size="16" />
      </button>
    </div>

    <div v-if="requirement.history && historyRows.length > 0" class="mt-3 rounded-xl border border-indigo-100 bg-indigo-50/60 p-3">
      <div class="flex items-center gap-1.5 text-xs font-semibold text-indigo-800">
        <History :size="13" />
        История HR
      </div>
      <div class="mt-2 space-y-1.5">
        <div v-for="row in historyRows" :key="row.label" class="flex items-center gap-2 text-[11px] text-indigo-900/70">
          <span class="w-16 shrink-0 font-medium">{{ row.label }}</span>
          <span class="h-1.5 flex-1 overflow-hidden rounded-full bg-white/80">
            <span class="block h-full rounded-full" :class="row.color" :style="{ width: `${row.percent}%` }" />
          </span>
          <span class="w-8 text-right font-semibold">{{ row.percent }}%</span>
        </div>
      </div>
      <p v-if="historyConflict" class="mt-2 text-xs leading-5 text-indigo-800">
        LLM предлагала <strong>{{ requirement.llmKind }}</strong>, но история HR переопределила тип.
      </p>
    </div>

    <blockquote v-if="requirement.quote" class="mt-3 flex gap-2 rounded-xl bg-slate-50 px-3 py-2.5 text-sm leading-6 text-slate-600">
      <Quote :size="14" class="mt-1 shrink-0 text-slate-400" />
      <span>{{ requirement.quote }}</span>
    </blockquote>

    <p v-if="requirement.clarityWarning" class="mt-3 rounded-xl bg-amber-50 px-3 py-2.5 text-sm leading-6 text-amber-800 ring-1 ring-inset ring-amber-100">
      {{ requirement.clarityWarning }}
    </p>
  </article>
</template>
