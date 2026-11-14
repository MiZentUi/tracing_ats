<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Copy, RefreshCcw } from '@lucide/vue'
import { CandidateMessageKind } from '@/api/generated'
import type { CandidateMessage } from '@/api/generated'
import { apiErrorMessage, reportsApi } from '@/api'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  applicationId: string
  phase: 'PRELIMINARY' | 'FINAL'
  hasGaps: boolean
  preferredKind?: CandidateMessageKind
}>()

const { success, error: toastError } = useToast()

const tabs = [
  { kind: CandidateMessageKind.InterviewInvitation, label: 'Приглашение на собеседование' },
  { kind: CandidateMessageKind.Rejection, label: 'Письмо отказа' },
] as const

const kind = ref<CandidateMessageKind>(
  props.preferredKind ?? CandidateMessageKind.InterviewInvitation,
)
const messages = ref<Partial<Record<CandidateMessageKind, CandidateMessage>>>({})
const loading = ref(false)
const generating = ref(false)
const error = ref('')

const activeMessage = computed(() => messages.value[kind.value] ?? null)
const isRejection = computed(() => kind.value === CandidateMessageKind.Rejection)
const phaseLabel = computed(() =>
  props.phase === 'PRELIMINARY' ? 'Предварительный расчёт' : 'Итоговый расчёт',
)
const generateLabel = computed(() => {
  if (generating.value) {
    return 'Генерируем…'
  }
  return activeMessage.value ? 'Сгенерировать заново' : 'Сгенерировать'
})

async function loadMessages() {
  if (!props.applicationId) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await reportsApi.getCandidateMessages({
      applicationId: props.applicationId,
      phase: props.phase,
    })
    messages.value = Object.fromEntries(
      response.data.map((message) => [message.kind, message]),
    ) as Partial<Record<CandidateMessageKind, CandidateMessage>>
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (generating.value || (isRejection.value && !props.hasGaps)) {
    return
  }
  generating.value = true
  error.value = ''
  try {
    const response = await reportsApi.generateCandidateMessage({
      applicationId: props.applicationId,
      candidateMessageRequest: { kind: kind.value },
      phase: props.phase,
    })
    messages.value = {
      ...messages.value,
      [response.data.kind]: response.data,
    }
    success('Черновик письма готов')
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось сгенерировать письмо', message)
  } finally {
    generating.value = false
  }
}

async function copy() {
  const message = activeMessage.value
  if (!message) {
    return
  }
  try {
    await navigator.clipboard.writeText(`${message.subject}\n\n${message.body}`)
    success('Письмо скопировано')
  } catch {
    toastError('Не удалось скопировать письмо')
  }
}

watch(
  () => [props.applicationId, props.phase],
  () => {
    messages.value = {}
    void loadMessages()
  },
)

watch(
  () => props.preferredKind,
  (value) => {
    if (value) {
      kind.value = value
    }
  },
)

onMounted(loadMessages)
</script>

<template>
  <section class="card p-5">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p class="text-xs uppercase tracking-wide text-slate-500">Письмо</p>
        <p class="mt-1 text-sm text-slate-500">
          {{ phaseLabel }}. Черновик для проверки HR. Автоматическая отправка не выполняется.
        </p>
      </div>
      <span
        class="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700"
      >
        Черновик
      </span>
    </div>

    <div
      class="mt-4 inline-flex flex-wrap rounded-xl border border-slate-200 bg-white p-1"
      role="tablist"
    >
      <button
        v-for="item in tabs"
        :key="item.kind"
        type="button"
        role="tab"
        :aria-selected="kind === item.kind"
        class="rounded-lg px-3 py-1.5 text-sm font-medium transition"
        :class="
          kind === item.kind
            ? 'bg-slate-900 text-white shadow-sm'
            : 'text-slate-600 hover:bg-slate-100'
        "
        @click="kind = item.kind"
      >
        {{ item.label }}
      </button>
    </div>

    <p v-if="error" class="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
      {{ error }}
    </p>

    <div v-if="loading" class="mt-4 text-sm text-slate-500">Загрузка…</div>

    <template v-else-if="activeMessage">
      <div class="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <p class="text-sm font-semibold text-slate-900">{{ activeMessage.subject }}</p>
        <p class="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">
          {{ activeMessage.body }}
        </p>
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <button class="btn-secondary" type="button" @click="copy">
          <Copy :size="14" />
          Скопировать
        </button>
        <button class="btn-primary" type="button" :disabled="generating" @click="generate">
          <RefreshCcw :size="14" />
          {{ generateLabel }}
        </button>
      </div>
    </template>

    <div
      v-else
      class="mt-4 rounded-xl border border-dashed border-slate-300 bg-slate-50/60 p-4"
    >
      <p v-if="isRejection && !hasGaps" class="text-sm text-slate-600">
        Нет непокрытых требований — письмо отказа не требуется.
      </p>
      <p v-else class="text-sm text-slate-600">Черновик ещё не сгенерирован.</p>
      <button
        v-if="!isRejection || hasGaps"
        class="btn-primary mt-3"
        type="button"
        :disabled="generating"
        @click="generate"
      >
        <RefreshCcw :size="14" />
        {{ generateLabel }}
      </button>
    </div>

    <p class="mt-3 text-xs text-slate-500">
      Проверьте текст, добавьте имя и контакты перед отправкой.
    </p>
  </section>
</template>
