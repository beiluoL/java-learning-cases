import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/', redirect: '/products' },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Home.vue'),
    children: [
      { path: 'products', name: 'ProductList', component: () => import('../views/ProductList.vue') },
      { path: 'product/:id', name: 'ProductDetail', component: () => import('../views/ProductDetail.vue') },
      { path: 'cart', name: 'Cart', component: () => import('../views/Cart.vue'), meta: { auth: true } },
      { path: 'checkout', name: 'Checkout', component: () => import('../views/Checkout.vue'), meta: { auth: true } },
      { path: 'orders', name: 'MyOrders', component: () => import('../views/MyOrders.vue'), meta: { auth: true } },
      { path: 'seckill', name: 'Seckill', component: () => import('../views/Seckill.vue') },
      { path: 'user', name: 'UserCenter', component: () => import('../views/UserCenter.vue'), meta: { auth: true } }
    ]
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { auth: true, admin: true },
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'products', name: 'AdminProducts', component: () => import('../views/admin/ProductManage.vue') },
      { path: 'orders', name: 'AdminOrders', component: () => import('../views/admin/OrderManage.vue') },
      { path: 'seckill', name: 'AdminSeckill', component: () => import('../views/admin/SeckillManage.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.auth && !user.token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && user.role !== 'ADMIN') {
    return { name: 'ProductList' }
  }
})

export default router
