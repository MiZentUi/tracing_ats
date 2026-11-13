import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResumeUploader from './ResumeUploader.vue'

const uploadResumes = vi.fn()
const listVacancies = vi.fn()

vi.mock('@/api', () => ({
  resumesApi: {
    uploadResumes: (...args: unknown[]) => uploadResumes(...args),
  },
  vacanciesApi: {
    listVacancies: (...args: unknown[]) => listVacancies(...args),
  },
  apiErrorMessage: () => 'Ошибка',
}))

describe('ResumeUploader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listVacancies.mockResolvedValue({
      data: {
        total: 1,
        items: [{ id: 'vacancy-1', title: 'Backend', specialization: 'backend', level: 'middle', status: 'ACTIVE' }],
      },
    })
  })

  it('отправляет вставленный текст без согласия на GitHub и привязывает к вакансии', async () => {
    uploadResumes.mockResolvedValue({ data: { resumes: [] } })
    const wrapper = mount(ResumeUploader, { props: { vacancyId: 'vacancy-1' } })

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

  it('без карточки вакансии позволяет выбрать вакансию вручную', async () => {
    uploadResumes.mockResolvedValue({ data: { resumes: [] } })
    const wrapper = mount(ResumeUploader)
    await flushPromises()

    await wrapper.get('textarea').setValue('Java, Kafka, 5 лет опыта')
    await wrapper.get('select').setValue('vacancy-1')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Добавить и подобрать'))
      ?.trigger('click')
    await flushPromises()

    expect(uploadResumes.mock.calls[0][0].vacancyId).toBe('vacancy-1')
  })
})
