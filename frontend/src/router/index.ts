import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import { pinia } from '@/stores';
import { useUserStore, type UserRole } from '@/stores/user';

declare module 'vue-router' {
  interface RouteMeta {
    title?: string;
    hidden?: boolean;
    icon?: string;
    roles?: UserRole[];
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: {
      title: '登录',
      hidden: true,
    },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: {
      title: '无权限访问',
      hidden: true,
    },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/upload',
    children: [
      {
        path: 'upload',
        name: 'upload',
        component: () => import('@/views/upload/UploadView.vue'),
        meta: {
          title: '多模态资源上传',
          icon: 'UploadFilled',
          roles: ['admin', 'engineer', 'operator'],
        },
      },
      {
        path: 'defect-detection',
        name: 'defect-detection',
        component: () => import('@/views/defect-detection/DefectDetectionView.vue'),
        meta: {
          title: '缺陷识别大屏',
          icon: 'VideoCameraFilled',
          roles: ['admin', 'engineer', 'operator'],
        },
      },
      {
        path: 'assessment',
        redirect: '/assessment/qualified',
        meta: {
          title: '生产质量评估',
          icon: 'DataAnalysis',
          roles: ['admin', 'engineer'],
        },
        children: [
          {
            path: 'qualified',
            name: 'assessment-qualified',
            component: () => import('@/views/assessment/AssessmentQualifiedView.vue'),
            meta: {
              title: '产品质量合格评估',
              roles: ['admin', 'engineer'],
            },
          },
          {
            path: 'judgment',
            name: 'assessment-judgment',
            component: () => import('@/views/assessment/AssessmentJudgmentView.vue'),
            meta: {
              title: '工艺参数研判评估',
              roles: ['admin', 'engineer'],
            },
          },
          {
            path: 'prediction',
            name: 'assessment-prediction',
            component: () => import('@/views/assessment/AssessmentPredictionView.vue'),
            meta: {
              title: '工艺参数预测评估',
              roles: ['admin', 'engineer'],
            },
          },
        ],
      },
      {
        path: 'knowledge-graph',
        name: 'knowledge-graph',
        component: () => import('@/views/graph/KnowledgeGraphView.vue'),
        meta: {
          title: '知识图谱可视化',
          icon: 'Connection',
          roles: ['admin', 'engineer'],
        },
      },
      {
        path: 'graph-sync',
        name: 'graph-sync',
        component: () => import('@/views/graph/SyncView.vue'),
        meta: {
          title: '知识图谱同步',
          icon: 'Refresh',
          roles: ['admin', 'engineer'],
        },
      },
      {
        path: 'import-history',
        name: 'import-history',
        component: () => import('@/views/upload/ImportHistoryView.vue'),
        meta: {
          title: '导入历史记录',
          icon: 'Clock',
          roles: ['admin', 'engineer', 'operator'],
        },
      },
      {
        path: 'export',
        name: 'export',
        component: () => import('@/views/export/ExportView.vue'),
        meta: {
          title: '结果筛选与资源导出',
          icon: 'DocumentCopy',
          roles: ['admin', 'engineer'],
        },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  const userStore = useUserStore(pinia);
  const token = userStore.token;
  const role = userStore.role;
  const isPublicRoute = to.path === '/login';

  if (!token && !isPublicRoute) {
    return {
      path: '/login',
      query: to.path === '/403' ? undefined : { redirect: to.fullPath },
    };
  }

  if (token && to.path === '/login') {
    return '/upload';
  }

  // token 存在但 profile 为空，清除 token 重新登录
  if (token && !role) {
    userStore.clearAuth();
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  const requiredRoles = to.meta.roles;
  if (requiredRoles?.length && (!role || !requiredRoles.includes(role))) {
    return '/403';
  }

  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '质量评估系统';
  document.title = `${title} | 电子元器件装配生产线质量评估系统`;
});

export default router;
