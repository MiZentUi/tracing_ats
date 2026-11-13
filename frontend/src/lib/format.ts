export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

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

export function shortId(value: string): string {
  return `${value.slice(0, 8)}…`
}

export function resumeTitle(createdAt: string, fileType?: string | null): string {
  const date = formatDate(createdAt)
  return fileType ? `Резюме · ${fileType.toUpperCase()} от ${date}` : `Резюме от ${date}`
}
