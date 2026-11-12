import type { SkillKind } from '@/api/generated'

/** Пользовательские подписи вместо технических enum из API. */
export const resumeStatusLabels: Record<string, string> = {
  UPLOADED: 'Загружено',
  MASKED: 'Данные обезличены',
  EXTRACTED: 'Текст разобран',
  MATCHING: 'Подбираем вакансии',
  MATCHED: 'Готово',
  MATCH_FAILED: 'Ошибка подбора',
  MASKING_FAILED: 'Ошибка обезличивания',
  TEXT_EMPTY: 'В файле нет текста',
  FAILED: 'Ошибка обработки',
}

export function resumeStatusLabel(status: string): string {
  return resumeStatusLabels[status] ?? status
}

export function resumeStatusClass(status: string): string {
  switch (status) {
    case 'MATCHED':
      return 'bg-emerald-50 text-emerald-700'
    case 'MATCH_FAILED':
    case 'MASKING_FAILED':
    case 'FAILED':
      return 'bg-rose-50 text-rose-700'
    case 'TEXT_EMPTY':
      return 'bg-amber-50 text-amber-700'
    default:
      return 'bg-slate-100 text-slate-600'
  }
}

export const decisionLabels: Record<string, string> = {
  SHORTLISTED: 'В шорт-листе',
  BORDERLINE: 'На границе',
  NOT_SHORTLISTED: 'Вне шорт-листа',
}

export function decisionLabel(decision: string): string {
  return decisionLabels[decision] ?? decision
}

export const phaseLabels: Record<string, string> = {
  PRELIMINARY: 'Предварительный',
  FINAL: 'Итоговый',
}

export function phaseLabel(phase: string): string {
  return phaseLabels[phase] ?? phase
}

export const kindLabels: Record<SkillKind, string> = {
  MUST: 'Обязательно',
  NICE: 'Желательно',
  OPTIONAL: 'Научим',
}

export function kindLabel(kind: SkillKind): string {
  return kindLabels[kind]
}

export const batchStatusLabels: Record<string, string> = {
  PENDING: 'В очереди',
  RUNNING: 'Идёт оценка',
  COMPLETED: 'Готово',
  FAILED: 'Ошибка',
}

export function batchStatusLabel(status: string): string {
  return batchStatusLabels[status] ?? status
}
