import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResumeListView from './ResumeListView.vue'

const listResumes = vi.fn()
const deleteResume = vi.fn()

vi.mock('@/api', () => ({
  resumesApi: {
    listResumes: (...args: unknown[]) => listResumes(...args),
    deleteResume: (...args: unknown[]) => deleteResume(...args),
  },
  apiErrorMessage: () => 'Ошибка',
}))

function page() {
  return {
    total: 1,
    items: [
      {
        resumeId: 'resume-1',
        title: 'Java Engineer',
        status: 'MATCHED',
        fileType: 'PDF',
        sizeBytes: 1024,
        createdAt: '2026-11-12T19:11:18Z',
      },
    ],
  }
}

function mountList() {
  return mount(ResumeListView, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        teleport: true,
      },
    },
  })
}

describe('ResumeListView', () => {
  beforeEach(() => {
    listResumes.mockReset()
    deleteResume.mockReset()
    listResumes.mockResolvedValue({ data: page() })
    deleteResume.mockResolvedValue({ data: undefined })
  })

  it('показывает название и теги резюме', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).toContain('Java Engineer')
    expect(wrapper.text()).toContain('PDF')
    expect(wrapper.text()).toContain('1.0 КБ')
    expect(wrapper.text()).toContain('Готово')
  })

  it('удаляет резюме', async () => {
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('button[title="Удалить резюме"]').trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().trim() === 'Удалить')
      ?.trigger('click')
    await flushPromises()

    expect(deleteResume).toHaveBeenCalledWith({ resumeId: 'resume-1' })
    expect(listResumes).toHaveBeenCalledTimes(2)
  })
})
