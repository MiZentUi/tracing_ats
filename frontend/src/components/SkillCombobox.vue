<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Skill } from '@/api/dictionary'

const props = defineProps<{
  modelValue: string
  skills: Skill[]
  inputId: string
  label: string
  inputLabel: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const query = ref('')
const open = ref(false)
const typing = ref(false)
const activeIndex = ref(-1)
const root = ref<HTMLDivElement | null>(null)
const listboxId = computed(() => `${props.inputId}-listbox`)
const selected = computed(() => props.skills.find((skill) => skill.id === props.modelValue) ?? null)
const filtered = computed(() => {
  const normalized = query.value.trim().toLowerCase()
  const all = !normalized || (selected.value && query.value === selected.value.canonicalName)
  const items = all
    ? props.skills
    : props.skills.filter(
        (skill) =>
          skill.canonicalName.toLowerCase().includes(normalized) ||
          skill.aliases.some((alias) => alias.raw.toLowerCase().includes(normalized)),
      )
  return items.slice(0, 60)
})

watch(
  [() => props.modelValue, () => props.skills],
  ([id]) => {
    if (!id) {
      if (!typing.value) {
        query.value = ''
      }
      return
    }
    const skill = props.skills.find((item) => item.id === id)
    if (skill) {
      query.value = skill.canonicalName
      typing.value = false
    }
  },
  { immediate: true },
)

function onInput(event: Event) {
  query.value = (event.target as HTMLInputElement).value
  typing.value = true
  open.value = true
  activeIndex.value = -1
  emit('update:modelValue', '')
}

function choose(skill: Skill) {
  query.value = skill.canonicalName
  typing.value = false
  open.value = false
  activeIndex.value = -1
  emit('update:modelValue', skill.id)
}

function onFocus() {
  open.value = true
  const selectedIndex = filtered.value.findIndex((skill) => skill.id === props.modelValue)
  activeIndex.value = selectedIndex >= 0 ? selectedIndex : filtered.value.length ? 0 : -1
}

function onBlur() {
  window.setTimeout(() => {
    open.value = false
    activeIndex.value = -1
  }, 120)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    open.value = false
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    open.value = true
    activeIndex.value = Math.min(activeIndex.value + 1, filtered.value.length - 1)
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
    return
  }
  if (event.key === 'Enter' && open.value && activeIndex.value >= 0) {
    const skill = filtered.value[activeIndex.value]
    if (skill) {
      event.preventDefault()
      choose(skill)
    }
  }
}

function onDocumentPointerDown(event: PointerEvent) {
  if (root.value && !root.value.contains(event.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div ref="root" class="relative">
    <label class="label" :for="inputId">{{ label }}</label>
    <input
      :id="inputId"
      v-model="query"
      class="input"
      type="text"
      role="combobox"
      autocomplete="off"
      :aria-label="inputLabel"
      :aria-expanded="open"
      :aria-controls="listboxId"
      aria-autocomplete="list"
      placeholder="Начните вводить название навыка"
      @focus="onFocus"
      @blur="onBlur"
      @input="onInput"
      @keydown="onKeydown"
    />
    <ul
      v-if="open && filtered.length"
      :id="listboxId"
      role="listbox"
      class="absolute z-30 mt-1 max-h-60 w-full overflow-auto rounded-xl border border-slate-200 bg-white py-1 shadow-xl"
    >
      <li
        v-for="(skill, index) in filtered"
        :key="skill.id"
        role="option"
        :aria-selected="skill.id === modelValue"
        class="cursor-pointer px-3 py-2 text-sm transition"
        :class="
          index === activeIndex
            ? 'bg-indigo-50 text-indigo-900'
            : 'text-slate-700 hover:bg-slate-50'
        "
        @mousedown.prevent="choose(skill)"
      >
        <span class="block truncate font-medium">{{ skill.canonicalName }}</span>
        <span v-if="skill.aliases.length" class="mt-0.5 block truncate text-xs text-slate-400">
          {{ skill.aliases.slice(0, 4).map((alias) => alias.raw).join(', ') }}
        </span>
      </li>
    </ul>
    <p
      v-else-if="open && query.trim()"
      class="absolute z-30 mt-1 w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-500 shadow-xl"
    >
      Ничего не найдено
    </p>
  </div>
</template>
