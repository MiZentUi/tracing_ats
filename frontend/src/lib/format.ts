export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleDateString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} КБ`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`
}

export function cleanResumeTitle(title: string | null | undefined): string {
  return (title ?? '')
    .replace(/(^|[\s,.;:!?()-])ЧД(?=[\s,.;:!?()-]|$)/gi, '$1 ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function resumeDisplayTitle(
  title: string | null | undefined,
  createdAt: string,
): string {
  return cleanResumeTitle(title) || `Резюме от ${formatDate(createdAt)}`
}

/** Короткий стабильный идентификатор для визуального различения карточек. */
export function shortId(value: string): string {
  return value.replace(/-/g, '').slice(0, 4).toUpperCase()
}

export function pluralizeRu(
  count: number,
  one: string,
  few: string,
  many: string,
): string {
  const mod10 = count % 10
  const mod100 = count % 100
  if (mod10 === 1 && mod100 !== 11) {
    return one
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
    return few
  }
  return many
}

export function requirementsCount(count: number): string {
  return `${count} ${pluralizeRu(count, 'требование', 'требования', 'требований')}`
}
