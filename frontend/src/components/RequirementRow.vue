<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp, Trash2 } from '@lucide/vue'
import type { SkillKind } from '@/api/generated'
import type { EditableRequirement } from '@/types'
import { educationLevelOptions, kindLabels } from '@/lib/labels'
import RequirementDetails from './RequirementDetails.vue'

const props = defineProps<{
  requirement: EditableRequirement
  duplicate?: boolean
}>()

const emit = defineEmits<{
  update: [patch: Partial<EditableRequirement>]
  remove: []
}>()

const showDetails = ref(false)
const isEducation = computed(() => !!props.requirement.educationLevel)

const hasDetails = computed(
  () => !!props.requirement.quote || !!props.requirement.clarityWarning,
)

const kindOptions: Array<{ value: SkillKind; label: string }> = [
  { value: 'MUST', label: kindLabels.MUST },
  { value: 'NICE', label: kindLabels.NICE },
  { value: 'OPTIONAL', label: kindLabels.OPTIONAL },
]

function updateName(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (isEducation.value) {
    emit('update', { educationSpecialty: value })
  } else {
    emit('update', { skill: value })
  }
}

function updateEducationLevel(event: Event) {
  emit('update', { educationLevel: (event.target as HTMLSelectElement).value || null })
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
    class="px-3 py-2 transition hover:bg-slate-50/60"
    :class="duplicate ? 'bg-red-50/40' : ''"
  >
    <div
      class="grid gap-2 md:items-center"
      :class="
        isEducation
          ? 'md:grid-cols-[minmax(0,1fr)_10rem_8rem_4.5rem]'
          : 'md:grid-cols-[minmax(0,1fr)_9rem_5.5rem_4.5rem]'
      "
    >
      <div class="flex min-w-0 items-center gap-2">
        <input
          :value="isEducation ? (requirement.educationSpecialty ?? '') : (requirement.skill ?? '')"
          class="min-w-0 flex-1 rounded-md border border-transparent bg-transparent px-1.5 py-1 text-sm font-semibold text-slate-900 outline-none transition placeholder:font-normal placeholder:text-slate-400 hover:border-slate-200 focus:border-indigo-400 focus:bg-white"
          :aria-label="
            isEducation
              ? 'Специальность'
              : requirement.qualification
                ? 'Название квалификации'
                : 'Название навыка'
          "
          :placeholder="
            isEducation
              ? 'Специальность или направление'
              : requirement.qualification
                ? 'Название квалификации'
                : 'Название навыка'
          "
          @input="updateName"
        />
        <span v-if="isEducation" class="badge-info shrink-0">Образование</span>
        <span v-else-if="requirement.qualification" class="badge-info shrink-0">Квалификация</span>
        <span v-if="duplicate" class="badge-danger shrink-0">дубликат</span>
      </div>

      <template v-if="isEducation">
        <select
          :value="requirement.educationLevel ?? ''"
          class="input py-1 text-sm"
          aria-label="Уровень образования"
          @change="updateEducationLevel"
        >
          <option v-for="level in educationLevelOptions" :key="level.value" :value="level.value">
            {{ level.label }}
          </option>
        </select>
        <select
          :value="requirement.kind"
          class="input py-1 text-sm"
          aria-label="Тип требования"
          @change="updateKind"
        >
          <option v-for="kind in kindOptions" :key="kind.value" :value="kind.value">
            {{ kind.label }}
          </option>
        </select>
      </template>

      <template v-else>
        <select
          :value="requirement.kind"
          class="input py-1 text-sm"
          aria-label="Тип требования"
          @change="updateKind"
        >
          <option v-for="kind in kindOptions" :key="kind.value" :value="kind.value">
            {{ kind.label }}
          </option>
        </select>

        <label class="flex items-center gap-1 text-xs text-slate-500">
          <input
            :value="requirement.yearsRequired ?? ''"
            type="number"
            min="0"
            max="50"
            step="1"
            class="w-14 rounded-md border border-slate-300 px-1.5 py-1 text-sm text-slate-900 outline-none focus:border-indigo-500"
            aria-label="Лет опыта"
            @input="updateYears"
          />
          лет
        </label>
      </template>

      <div class="flex items-center justify-end gap-0.5">
        <button
          v-if="hasDetails"
          type="button"
          class="icon-btn h-8 w-8 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          :title="showDetails ? 'Скрыть подробности' : 'Показать подробности'"
          @click="showDetails = !showDetails"
        >
          <ChevronUp v-if="showDetails" :size="15" />
          <ChevronDown v-else :size="15" />
        </button>
        <button
          type="button"
          class="icon-btn h-8 w-8 text-slate-400 hover:bg-red-50 hover:text-red-600"
          title="Удалить требование"
          @click="emit('remove')"
        >
          <Trash2 :size="15" />
        </button>
      </div>
    </div>

    <div v-if="showDetails" class="mt-2">
      <RequirementDetails :requirement="requirement" />
    </div>
  </article>
</template>
