export type PageSurface =
  | 'APPROVAL'
  | 'DETAIL'
  | 'UPLOAD'
  | 'FORM'
  | 'DASHBOARD'
  | 'CONFIG'
  | 'PORTAL'
  | 'WIZARD'
  | 'SEARCH_LIST'
  | 'LIST'
  | 'CUSTOM'

const SURFACE_RULES: Array<{ surface: PageSurface; re: RegExp }> = [
  { surface: 'APPROVAL', re: /审批|审核|复核|流程|节点|流转|状态变更|approval|audit|review|workflow|approve/i },
  { surface: 'DETAIL', re: /详情|明细|档案|资料页|个人资料|账户信息|detail|profile/i },
  { surface: 'UPLOAD', re: /上传|导入|upload|import/i },
  { surface: 'FORM', re: /创建|新建|新增|录入|登记|编辑|修改|表单|create|new|add|edit|form/i },
  { surface: 'DASHBOARD', re: /看板|大屏|驾驶舱|统计|报表|分析|指标|趋势|图表|dashboard|report|analytics|metric|chart|big.?screen/i },
  { surface: 'CONFIG', re: /配置|设置|setting|config/i },
  { surface: 'PORTAL', re: /门户|首页|工作台|总览|概览|portal|home|workbench|overview/i },
  { surface: 'WIZARD', re: /向导|引导|分步|步骤|wizard|step|stepper/i },
  { surface: 'SEARCH_LIST', re: /搜索|筛选|查询|分页|search|filter|query/i },
  { surface: 'LIST', re: /列表|表格|list|table|crud/i },
]

export function inferSurface(text: string): PageSurface {
  const t = (text || '').toLowerCase()
  for (const rule of SURFACE_RULES) {
    if (rule.re.test(t)) return rule.surface
  }
  return 'CUSTOM'
}