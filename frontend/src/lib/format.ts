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

export function resumeDisplayTitle(
  title: string | null | undefined,
  createdAt: string,
): string {
  return title?.trim() || `Резюме от ${formatDate(createdAt)}`
}
