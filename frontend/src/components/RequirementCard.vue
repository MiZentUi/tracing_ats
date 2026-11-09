<script setup lang="ts">
import { computed } from 'vue'
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

function updateSkill(event: Event) {
  emit('update', { skill: (event.target as HTMLInputElement).value })
}

function updateKind(event: Event) {
  emit('update', { kind: (event.target as HTMLSelectElement).value as SkillKind })
}

function updateYears(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update', { yearsRequired: value === '' ? null : Number(value) })
}
</script>

<template>
  <article
    data-testid="requirement-card"
    class="rounded-lg border bg-white p-4 shadow-sm"
    :class="duplicate ? 'border-red-400 ring-1 ring-red-200' : 'border-slate-200'"
  >
    <div class="flex flex-wrap items-start gap-2">
      <input
        v-if="requirement.skill !== null"
        :value="requirement.skill ?? ''"
        class="min-w-48 flex-1 rounded border border-slate-300 px-2 py-1"
        aria-label="Название навыка"
        @input="updateSkill"
      />
      <div v-else class="min-w-48 flex-1 text-sm text-slate-500">
        Расплывчатое требование (навык не выделен)
      </div>

      <span
        class="rounded px-2 py-1 text-xs"
        :class="
          requirement.normalizationStatus === 'MATCHED'
            ? 'bg-emerald-50 text-emerald-700'
            : 'bg-amber-50 text-amber-700'
        "
      >
        {{ requirement.normalizationStatus === 'MATCHED' ? 'из словаря' : 'новый навык' }}
      </span>

      <span class="rounded bg-slate-100 px-2 py-1 text-xs" :title="sourceHint">
        {{ sourceLabel }}
      </span>
    </div>

    <p v-if="duplicate" class="mt-2 text-sm text-red-600">Дубликат навыка</p>

    <div class="mt-3 flex flex-wrap items-center gap-3">
      <label class="text-sm">
        Тип
        <select
          :value="requirement.kind"
          class="ml-1 rounded border border-slate-300 px-2 py-1"
          aria-label="Тип требования"
          @change="updateKind"
        >
          <option value="MUST">Обязательное</option>
          <option value="NICE">Желательное</option>
          <option value="OPTIONAL">Научим / не требуется</option>
        </select>
      </label>

      <label class="text-sm">
        Лет опыта
        <input
          :value="requirement.yearsRequired ?? ''"
          type="number"
          min="0"
          max="50"
          step="0.5"
          class="ml-1 w-20 rounded border border-slate-300 px-2 py-1"
          aria-label="Лет опыта"
          @input="updateYears"
        />
      </label>

      <button class="ml-auto text-sm text-red-600 hover:underline" @click="emit('remove')">
        Удалить
      </button>
    </div>

    <p v-if="historyConflict" class="mt-2 text-xs text-indigo-700">
      {{ sourceHint }}
    </p>

    <blockquote v-if="requirement.quote" class="mt-3 border-l-4 border-slate-200 pl-3 text-sm text-slate-600">
      {{ requirement.quote }}
    </blockquote>

    <p v-if="requirement.clarityWarning" class="mt-3 rounded bg-amber-50 p-2 text-sm text-amber-800">
      {{ requirement.clarityWarning }}
    </p>
  </article>
</template>
