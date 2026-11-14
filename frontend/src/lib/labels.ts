import type { SkillKind } from '@/api/generated'

/** Пользовательские подписи вместо технических enum из API. */
export const resumeStatusLabels: Record<string, string> = {
  UPLOADED: 'Загружено',
  MASKED: 'Данные обезличены',
  EXTRACTED: 'Текст разобран',
  SCORING: 'Считаем скор',
  SCORED: 'Скор готов',
  SCORING_FAILED: 'Ошибка скоринга',
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
    case 'SCORED':
      return 'bg-emerald-50 text-emerald-700'
    case 'SCORING':
      return 'bg-indigo-50 text-indigo-700'
    case 'SCORING_FAILED':
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

const uploadWarningLabels: Record<string, string> = {
  FILE_TOO_LARGE: 'Файл больше допустимого размера',
  UNSUPPORTED_TYPE: 'Формат файла не поддерживается. Нужен PDF, DOCX, TXT или MD',
  CORRUPTED_FILE: 'Файл повреждён или не читается',
  ENCRYPTED_FILE: 'PDF защищён паролем — сохраните копию без пароля',
  TEXT_EMPTY: 'В файле нет текста. Если это скан, вставьте текст резюме вручную',
  LIMIT_EXCEEDED: 'Файл слишком большой или содержит слишком много текста',
  EXTRACTION_TIMEOUT: 'Не удалось извлечь текст за отведённое время',
  MASKING_FAILED: 'Не удалось обезличить текст резюме',
  FILE_READ_FAILED: 'Не удалось прочитать файл',
  ENCODING_FALLBACK_CP1251: 'Кодировка файла определена как windows-1251',
}

export function uploadWarningLabel(code: string): string {
  return uploadWarningLabels[code] ?? code
}

export function uploadFailureMessage(
  status: string,
  warnings?: string[] | null,
): string {
  const code = warnings?.[0]
  if (code) {
    return uploadWarningLabel(code)
  }
  if (status === 'TEXT_EMPTY' || status === 'MASKING_FAILED') {
    return uploadWarningLabels[status]
  }
  return 'Не удалось обработать резюме'
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

export const educationLevelLabels: Record<string, string> = {
  SECONDARY: 'Среднее',
  SECONDARY_SPECIALIZED: 'Среднее специальное',
  BACHELOR: 'Бакалавриат',
  MASTER: 'Магистратура',
  DOCTOR: 'Аспирантура или докторантура',
  COURSES: 'Курсы',
}

export const educationLevelOptions = Object.entries(educationLevelLabels).map(
  ([value, label]) => ({ value, label }),
)

export function educationLevelLabel(level: string | null | undefined): string {
  if (!level) {
    return 'Уровень не указан'
  }
  return educationLevelLabels[level] ?? level
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
