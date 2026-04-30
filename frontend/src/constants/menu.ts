import type { MenuItem } from '@/types/menu';

export const menus: MenuItem[] = [
  {
    path: '/upload',
    title: '多模态资源上传',
    icon: 'UploadFilled',
  },
  {
    path: '/defect-detection',
    title: '缺陷识别大屏',
    icon: 'VideoCameraFilled',
  },
  {
    path: '/assessment',
    title: '生产质量评估',
    icon: 'DataAnalysis',
    children: [
      {
        path: '/assessment/qualified',
        title: '产品质量合格评估',
      },
      {
        path: '/assessment/judgment',
        title: '工艺参数研判评估',
      },
      {
        path: '/assessment/prediction',
        title: '工艺参数预测评估',
      },
    ],
  },
  {
    path: '/knowledge-graph',
    title: '知识图谱可视化',
    icon: 'Connection',
  },
  {
    path: '/export',
    title: '结果筛选与资源导出',
    icon: 'DocumentCopy',
  },
];
