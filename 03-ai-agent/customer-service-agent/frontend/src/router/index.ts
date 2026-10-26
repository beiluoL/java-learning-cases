import { createRouter, createWebHistory } from 'vue-router';
import CustomerChat from '../views/CustomerChat.vue';
import AdminDashboard from '../views/AdminDashboard.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'chat', component: CustomerChat },
    { path: '/admin', name: 'admin', component: AdminDashboard }
  ]
});

export default router;
