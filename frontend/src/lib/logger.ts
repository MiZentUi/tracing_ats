const PREFIX = '[ats]'

/**
 * Общий logger фронтенда. В production остаются только warn/error; debug/info
 * доступны в dev-сборке. PII, токены и тела ответов сюда не передаются.
 */
export const logger = {
  debug(...args: unknown[]): void {
    if (import.meta.env.DEV) {
      console.debug(PREFIX, ...args)
    }
  },
  info(...args: unknown[]): void {
    if (import.meta.env.DEV) {
      console.info(PREFIX, ...args)
    }
  },
  warn(...args: unknown[]): void {
    console.warn(PREFIX, ...args)
  },
  error(...args: unknown[]): void {
    console.error(PREFIX, ...args)
  },
}
