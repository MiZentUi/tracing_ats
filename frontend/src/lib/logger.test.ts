import { afterEach, describe, expect, it, vi } from 'vitest'
import { logger } from './logger'

describe('logger', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('пишет warn и error с префиксом', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})

    logger.warn('api_request_failed', 'status=500', 'request_id=abc')
    logger.error('ui_error', 'boom')

    expect(warn).toHaveBeenCalledWith('[ats]', 'api_request_failed', 'status=500', 'request_id=abc')
    expect(error).toHaveBeenCalledWith('[ats]', 'ui_error', 'boom')
  })

  it('пишет debug только в dev-сборке', () => {
    const debug = vi.spyOn(console, 'debug').mockImplementation(() => {})

    logger.debug('api_request', 'status=200')

    if (import.meta.env.DEV) {
      expect(debug).toHaveBeenCalledWith('[ats]', 'api_request', 'status=200')
    } else {
      expect(debug).not.toHaveBeenCalled()
    }
  })
})
