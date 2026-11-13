import { createRouter, createWebHistory } from 'vue-router'
import VacancyListView from '@/views/VacancyListView.vue'
import VacancyCreateView from '@/views/VacancyCreateView.vue'
import VacancyView from '@/views/VacancyView.vue'
import VacancyEditView from '@/views/VacancyEditView.vue'
import ResumeListView from '@/views/ResumeListView.vue'
import ResumeCreateView from '@/views/ResumeCreateView.vue'
import ResumeDetailView from '@/views/ResumeDetailView.vue'
import ReportView from '@/views/ReportView.vue'
import DictionaryView from '@/views/DictionaryView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/vacancies' },
    { path: '/vacancies', name: 'vacancies', component: VacancyListView },
    { path: '/vacancies/new', name: 'vacancy-create', component: VacancyCreateView },
    { path: '/vacancies/:id', name: 'vacancy-view', component: VacancyView, props: true },
    {
      path: '/vacancies/:id/edit',
      name: 'vacancy-edit',
      component: VacancyEditView,
      props: true,
    },
    { path: '/resumes', name: 'resumes', component: ResumeListView },
    { path: '/resumes/new', name: 'resume-create', component: ResumeCreateView },
    { path: '/resumes/:id', name: 'resume-view', component: ResumeDetailView, props: true },
    {
      path: '/dictionary',
      name: 'dictionary',
      component: DictionaryView,
    },
    {
      path: '/applications/:id/report',
      name: 'application-report',
      component: ReportView,
    },
    {
      path: '/resumes/:resumeId/vacancies/:vacancyId/report',
      name: 'resume-vacancy-report',
      component: ReportView,
    },
    // Старые ссылки: «Подбор» больше не отдельный раздел.
    {
      path: '/match',
      redirect: (to) => {
        const vacancyId = typeof to.query.vacancyId === 'string' ? to.query.vacancyId : ''
        return vacancyId ? `/vacancies/${vacancyId}` : '/resumes'
      },
    },
    { path: '/analyses', redirect: (to) => ({ path: '/resumes', query: to.query }) },
    {
      path: '/compare',
      redirect: (to) => {
        const applicationId =
          typeof to.query.applicationId === 'string' ? to.query.applicationId : ''
        if (applicationId) {
          return `/applications/${applicationId}/report`
        }
        const resumeId = typeof to.query.resumeId === 'string' ? to.query.resumeId : ''
        const vacancyId = typeof to.query.vacancyId === 'string' ? to.query.vacancyId : ''
        if (resumeId && vacancyId) {
          return `/resumes/${resumeId}/vacancies/${vacancyId}/report`
        }
        return resumeId ? `/resumes/${resumeId}` : '/resumes'
      },
    },
    { path: '/vacancies/:id/candidates', redirect: (to) => `/vacancies/${to.params.id}` },
    { path: '/resumes/:id/text', redirect: (to) => `/resumes/${to.params.id}` },
  ],
})

export default router
