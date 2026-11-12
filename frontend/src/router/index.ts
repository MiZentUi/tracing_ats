import { createRouter, createWebHistory } from 'vue-router'
import VacancyListView from '@/views/VacancyListView.vue'
import VacancyCreateView from '@/views/VacancyCreateView.vue'
import VacancyEditView from '@/views/VacancyEditView.vue'
import VacancyCandidatesView from '@/views/VacancyCandidatesView.vue'
import ResumeListView from '@/views/ResumeListView.vue'
import ResumeRankingView from '@/views/ResumeRankingView.vue'
import ResumeTextView from '@/views/ResumeTextView.vue'
import CompareView from '@/views/CompareView.vue'
import ReportView from '@/views/ReportView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/vacancies' },
    { path: '/vacancies', name: 'vacancies', component: VacancyListView },
    { path: '/vacancies/new', name: 'vacancy-create', component: VacancyCreateView },
    { path: '/vacancies/:id', name: 'vacancy-edit', component: VacancyEditView, props: true },
    {
      path: '/vacancies/:id/candidates',
      name: 'vacancy-candidates',
      component: VacancyCandidatesView,
      props: true,
    },
    { path: '/resumes', name: 'resumes', component: ResumeListView },
    { path: '/resumes/:id', name: 'resume-ranking', component: ResumeRankingView, props: true },
    {
      path: '/resumes/:id/text',
      name: 'resume-text',
      component: ResumeTextView,
      props: true,
    },
    {
      path: '/compare',
      name: 'compare',
      component: CompareView,
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
        return vacancyId ? `/vacancies/${vacancyId}/candidates` : '/resumes'
      },
    },
    { path: '/analyses', redirect: (to) => ({ path: '/resumes', query: to.query }) },
  ],
})

export default router
