import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import VacancyCreateView from './VacancyCreateView.vue'

const createVacancy = vi.fn()
const createSpecialization = vi.fn()

vi.mock('@/api', () => ({
  vacanciesApi: {
    createVacancy: (...args: unknown[]) => createVacancy(...args),
  },
  referenceApi: {
    listSpecializations: () =>
      Promise.resolve({
        data: [
          { code: 'analitika', name: 'Аналитика', it: false },
          { code: 'backend', name: 'Backend', it: true },
        ],
      }),
    listLevels: () => Promise.resolve({ data: [{ code: 'middle', name: 'Middle' }] }),
    createSpecialization: (...args: unknown[]) => createSpecialization(...args),
  },
  apiErrorMessage: () => 'Ошибка',
  apiErrorCode: () => null,
}))

async function mountCreate() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/vacancies/new', component: VacancyCreateView },
      { path: '/vacancies/:id', component: { template: '<div />' } },
    ],
  })
  await router.push('/vacancies/new')
  await router.isReady()
  const wrapper = mount(VacancyCreateView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return wrapper
}

describe('VacancyCreateView', () => {
  it('блокирует повторную отправку и показывает индикатор', async () => {
    let resolveRequest: (value: unknown) => void = () => {}
    createVacancy.mockImplementation(
      () => new Promise((resolve) => (resolveRequest = resolve)),
    )
    const wrapper = await mountCreate()

    await wrapper.findAll('select')[0].setValue('backend')
    await wrapper.findAll('select')[1].setValue('middle')
    await wrapper.get('textarea').setValue('Java')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const button = wrapper.get('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Разбор может занять до 5 минут')

    resolveRequest({ data: { vacancy_id: 'vacancy-1' } })
    await flushPromises()
  })

  it('добавляет специализацию и выбирает её', async () => {
    createSpecialization.mockResolvedValue({
      data: { code: 'analitika', name: 'Аналитика', it: false },
    })
    const wrapper = await mountCreate()

    await wrapper.get('button[title="Добавить специализацию"]').trigger('click')
    await wrapper.get('input[placeholder="Например, Аналитика"]').setValue('Аналитика')
    const dialog = wrapper.get('[role="dialog"]')
    const addButton = dialog
      .findAll('button')
      .find((button) => button.text().trim() === 'Добавить')
    await addButton?.trigger('click')
    await flushPromises()

    expect(createSpecialization).toHaveBeenCalledWith({
      createSpecializationRequest: { name: 'Аналитика', code: null, it: true },
    })
    expect((wrapper.findAll('select')[0].element as HTMLSelectElement).value).toBe('analitika')
  })

  it('требует грейд для IT-специализации', async () => {
    const wrapper = await mountCreate()
    await wrapper.findAll('select')[0].setValue('backend')
    await wrapper.get('textarea').setValue('Java')
    expect(wrapper.text()).toContain('Для IT-специализации грейд обязателен')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.findAll('select')[1].setValue('middle')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('не запрашивает грейд для не-IT специализации', async () => {
    createVacancy.mockResolvedValue({ data: { vacancy_id: 'vacancy-1' } })
    const wrapper = await mountCreate()
    await wrapper.findAll('select')[0].setValue('analitika')
    await wrapper.get('textarea').setValue('Аналитика рынка')
    expect(wrapper.text()).toContain('Не требуется: специализация не IT')
    expect(wrapper.findAll('select')).toHaveLength(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createVacancy).toHaveBeenCalledWith(
      expect.objectContaining({ specialization: 'analitika', level: undefined }),
    )
  })

  it('не отправляет форму без текста', async () => {
    const wrapper = await mountCreate()
    await wrapper.findAll('select')[0].setValue('backend')
    await wrapper.findAll('select')[1].setValue('middle')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
