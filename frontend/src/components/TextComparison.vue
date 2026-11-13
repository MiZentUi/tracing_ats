<script setup lang="ts">
import { computed } from 'vue'
import { Eye, EyeOff, Loader2 } from '@lucide/vue'
import type { RequirementReportItem } from '@/api/generated'

const props = defineProps<{
  vacancyText: string
  resumeText: string
  requirements: RequirementReportItem[]
  resumeId?: string
  resumeRevealed?: boolean
  revealing?: boolean
}>()

const emit = defineEmits<{
  reveal: []
  hide: []
}>()

const matched = computed(() =>
  props.requirements.filter(
    (item) => item.status === 'COVERED' || item.status === 'PARTIAL',
  ),
)

const vacancyTerms = computed(() =>
  matched.value.map((item) => item.text).filter((text): text is string => !!text),
)

const resumeTerms = computed(() =>
  matched.value
    .flatMap((item) => [item.evidenceQuote, item.text])
    .filter((text): text is string => !!text),
)

const vacancySegments = computed(() => segments(props.vacancyText, vacancyTerms.value))
const resumeSegments = computed(() => segments(props.resumeText, resumeTerms.value))

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

interface Segment {
  text: string
  match: boolean
}

function segments(text: string, terms: string[]): Segment[] {
  if (!text) {
    return []
  }
  const lower = text.toLowerCase()
  const effective: string[] = []
  for (const term of terms) {
    const trimmed = term.trim()
    if (trimmed.length < 3) {
      continue
    }
    if (lower.includes(trimmed.toLowerCase())) {
      effective.push(trimmed)
      continue
    }
    for (const token of trimmed.split(/[^\p{L}\p{N}+#.]+/u)) {
      if (token.length >= 4) {
        effective.push(token)
      }
    }
  }
  const unique = [...new Set(effective)]
  if (!unique.length) {
    return [{ text, match: false }]
  }
  const pattern = unique
    .sort((left, right) => right.length - left.length)
    .map((term) => escapeRegex(term).replace(/\s+/g, '\\s+'))
    .join('|')
  const regex = new RegExp(pattern, 'gi')
  const result: Segment[] = []
  let last = 0
  for (const match of text.matchAll(regex)) {
    const index = match.index ?? 0
    if (index > last) {
      result.push({ text: text.slice(last, index), match: false })
    }
    result.push({ text: match[0], match: true })
    last = index + match[0].length
  }
  if (last < text.length) {
    result.push({ text: text.slice(last), match: false })
  }
  return result
}
</script>

<template>
  <section class="space-y-3">
    <h2 class="text-base font-semibold text-slate-900">Сравнение текстов</h2>
    <div class="grid gap-4 lg:grid-cols-2">
      <article class="rounded-xl border border-slate-200 bg-white">
        <header class="flex items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
          <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Вакансия</p>
        </header>
        <div class="px-4 py-3">
          <pre
            v-if="vacancyText"
            class="whitespace-pre-wrap font-mono text-sm leading-6 text-slate-800"
          ><template v-for="(segment, index) in vacancySegments" :key="index"><mark
                v-if="segment.match"
                class="rounded bg-emerald-200/80 px-0.5 text-slate-900"
              >{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <p v-else class="text-sm text-slate-500">Текст вакансии недоступен.</p>
        </div>
      </article>

      <article class="rounded-xl border border-slate-200 bg-white">
        <header class="flex items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
          <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Резюме</p>
          <button
            v-if="resumeId && !resumeRevealed"
            type="button"
            class="btn-secondary px-2.5 py-1 text-xs"
            :disabled="revealing"
            @click="emit('reveal')"
          >
            <Loader2 v-if="revealing" :size="13" class="animate-spin" />
            <Eye v-else :size="13" />
            Показать персональные данные
          </button>
          <button
            v-else-if="resumeRevealed"
            type="button"
            class="btn-secondary px-2.5 py-1 text-xs"
            @click="emit('hide')"
          >
            <EyeOff :size="13" />
            Скрыть персональные данные
          </button>
        </header>
        <div class="px-4 py-3">
          <pre
            v-if="resumeText"
            class="whitespace-pre-wrap font-mono text-sm leading-6 text-slate-800"
          ><template v-for="(segment, index) in resumeSegments" :key="index"><mark
                v-if="segment.match"
                class="rounded bg-emerald-200/80 px-0.5 text-slate-900"
              >{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <p v-else class="text-sm text-slate-500">Текст резюме недоступен.</p>
        </div>
      </article>
    </div>
  </section>
</template>
