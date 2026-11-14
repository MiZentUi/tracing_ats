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

interface Term {
  text: string
  status: string
}

const statusMarkClasses: Record<string, string> = {
  COVERED: 'bg-emerald-100 text-emerald-900',
  PARTIAL: 'bg-amber-100 text-amber-900',
  GAP: 'bg-rose-100 text-rose-900',
  NOT_STATED: 'bg-slate-200 text-slate-800',
  ABSENT: 'bg-rose-100 text-rose-900',
}

const vacancyTerms = computed<Term[]>(() =>
  props.requirements
    .filter((item) => !!item.text)
    .map((item) => ({ text: item.text as string, status: item.status })),
)

const resumeTerms = computed<Term[]>(() =>
  props.requirements.flatMap((item) => {
    const terms: Term[] = []
    if (item.evidenceQuote) {
      terms.push({ text: item.evidenceQuote, status: item.status })
    }
    if (item.text) {
      terms.push({ text: item.text, status: item.status })
    }
    return terms
  }),
)

const vacancySegments = computed(() => segments(props.vacancyText, vacancyTerms.value))
const resumeSegments = computed(() => segments(props.resumeText, resumeTerms.value))

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function normalizeTerm(value: string): string {
  return value.toLowerCase().replace(/\s+/g, ' ').trim()
}

function markClass(status: string | undefined): string {
  return statusMarkClasses[status ?? ''] ?? 'bg-slate-200 text-slate-800'
}

interface Segment {
  text: string
  status?: string
}

function segments(text: string, terms: Term[]): Segment[] {
  if (!text) {
    return []
  }
  const lower = text.toLowerCase()
  const effective: Term[] = []
  for (const term of terms) {
    const trimmed = term.text.trim()
    if (trimmed.length < 3) {
      continue
    }
    if (lower.includes(trimmed.toLowerCase())) {
      effective.push({ text: trimmed, status: term.status })
      continue
    }
    for (const token of trimmed.split(/[^\p{L}\p{N}+#.]+/u)) {
      if (token.length >= 4) {
        effective.push({ text: token, status: term.status })
      }
    }
  }
  const unique = new Map<string, Term>()
  for (const term of effective) {
    const key = normalizeTerm(term.text)
    if (!unique.has(key)) {
      unique.set(key, term)
    }
  }
  if (!unique.size) {
    return [{ text }]
  }
  const ordered = [...unique.values()].sort(
    (left, right) => right.text.length - left.text.length,
  )
  const pattern = ordered
    .map((term) => escapeRegex(term.text).replace(/\s+/g, '\\s+'))
    .join('|')
  const regex = new RegExp(pattern, 'gi')
  const result: Segment[] = []
  let last = 0
  for (const match of text.matchAll(regex)) {
    const index = match.index ?? 0
    if (index > last) {
      result.push({ text: text.slice(last, index) })
    }
    const matched = match[0]
    result.push({
      text: matched,
      status: unique.get(normalizeTerm(matched))?.status,
    })
    last = index + matched.length
  }
  if (last < text.length) {
    result.push({ text: text.slice(last) })
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
                v-if="segment.status"
                class="rounded px-0.5"
                :class="markClass(segment.status)"
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
                v-if="segment.status"
                class="rounded px-0.5"
                :class="markClass(segment.status)"
              >{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <p v-else class="text-sm text-slate-500">Текст резюме недоступен.</p>
        </div>
      </article>
    </div>
  </section>
</template>
