import LoginPage from '../pages/LoginPage.vue'
import RegisterPage from '../pages/RegisterPage.vue'
import UserHomePage from '../pages/UserHomePage.vue'

export const generatedRoutes = [
  { path: '/', redirect: '/auth/login', name: 'Root', component: LoginPage },
  { path: '/auth/login', name: 'LoginPage', title: '登录页', component: LoginPage },
  { path: '/auth/register', name: 'RegisterPage', title: '注册页', component: RegisterPage },
  { path: '/user/home', name: 'UserHomePage', title: '用户首页', component: UserHomePage },
]
