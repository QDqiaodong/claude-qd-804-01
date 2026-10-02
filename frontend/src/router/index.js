import { ref } from 'vue'
import { createWebHistory } from 'vue-router'
import BayMapView from '../views/BayMapView.vue'
import WorkTicketView from '../views/WorkTicketView.vue'
import OperationView from '../views/OperationView.vue'
import DefectView from '../views/DefectView.vue'

/**
 * 本仓不走 vue-router 插件式安装（main.js 不在可改动清单里），
 * 这里用 vue-router 的 history 对象自己驱动一层轻量路由：
 * 地址栏路径 ↔ 当前页面组件，下方 NavView 组件即可直接渲染。
 */
const history = createWebHistory()

export const routes = [
  { path: '/bays', name: 'bays', title: '间隔平面示意图', component: BayMapView },
  { path: '/work-tickets', name: 'work-tickets', title: '两票票面 · 三审签字', component: WorkTicketView },
  { path: '/operations', name: 'operations', title: '倒闸操作 · 步骤勾选', component: OperationView },
  { path: '/defects', name: 'defects', title: '缺陷超期排行 · 消缺闭环', component: DefectView }
]

const DEFAULT_PATH = '/bays'

export const currentPath = ref(normalize(window.location.pathname))

function normalize(path) {
  const clean = (path || '').replace(/\/+$/, '')
  return routes.some((r) => r.path === clean) ? clean : DEFAULT_PATH
}

if (normalize(window.location.pathname) !== window.location.pathname) {
  history.replace(DEFAULT_PATH)
}

history.listen((to) => {
  currentPath.value = normalize(to)
})

export function go(path) {
  if (path === currentPath.value) return
  history.push(path)
  currentPath.value = path
}

export function currentView() {
  return routes.find((r) => r.path === currentPath.value) || routes[0]
}

export default { routes, go, currentPath, currentView }
