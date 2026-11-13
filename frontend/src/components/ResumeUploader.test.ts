import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResumeUploader from './ResumeUploader.vue'

const uploadResumes = vi.fn()

vi.mock('@/api', () => ({
  resumesApi: {
    uploadResumes: (...args: unknown[]) => uploadResumes(...args),
  },
  apiErrorMessage: () => 'Ошибка',
}))

describe('ResumeUploader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    uploadResumes.mockResolvedValue({ data: { resumes: [] } })
  })

  it('отправляет вставленный текст без согласия на GitHub и привязывает к вакансии', async () => {
    const wrapper = mount(ResumeUploader, { props: { vacancyId: 'vacancy-1', compact: true } })

    await wrapper.get('textarea').setValue('Java, Kafka, 5 лет опыта')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Добавить и подобрать'))
      ?.trigger('click')
    await flushPromises()

    expect(uploadResumes).toHaveBeenCalledTimes(1)
    const payload = uploadResumes.mock.calls[0][0]
    expect(payload.consentGithub).toBe(false)
    expect(payload.vacancyId).toBe('vacancy-1')
    expect(payload.files).toHaveLength(1)
    expect(payload.files[0].name).toBe('pasted-resume-1.txt')
  })

  it('без вакансии отправляет резюме в общий подбор', async () => {
    const wrapper = mount(ResumeUploader)

    expect(wrapper.text()).toContain('Добавить резюме')
    await wrapper.get('textarea').setValue('Java, Kafka, 5 лет опыта')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Добавить резюме'))
      ?.trigger('click')
    await flushPromises()

    expect(uploadResumes.mock.calls[0][0].vacancyId).toBeUndefined()
  })
})
