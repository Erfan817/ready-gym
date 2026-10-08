export interface Exercise { id: string; name: string; english: string; muscle: string; equipment: string; description: string; source: '人工界面演示'; }
// 人工整理名称和展示分类；不是上游数据，也不是经审核的训练说明。
export const exercises: Exercise[] = [
  { id: 'demo-pushup', name: '俯卧撑', english: 'Push-up', muscle: '胸', equipment: '徒手', description: '展示胸部肌群与徒手器械分类的界面示例。此处不提供动作步骤。', source: '人工界面演示' },
  { id: 'demo-bench', name: '哑铃卧推', english: 'Dumbbell bench press', muscle: '胸', equipment: '哑铃', description: '展示胸部肌群与哑铃器械分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-row', name: '哑铃划船', english: 'Dumbbell row', muscle: '背', equipment: '哑铃', description: '展示背部肌群与哑铃器械分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-raise', name: '哑铃侧平举', english: 'Dumbbell lateral raise', muscle: '肩', equipment: '哑铃', description: '展示肩部肌群分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-curl', name: '哑铃弯举', english: 'Dumbbell curl', muscle: '手臂', equipment: '哑铃', description: '展示手臂肌群分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-plank', name: '平板支撑', english: 'Plank', muscle: '核心', equipment: '徒手', description: '展示核心肌群分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-squat', name: '徒手深蹲', english: 'Bodyweight squat', muscle: '腿', equipment: '徒手', description: '展示腿部肌群与徒手器械分类的界面示例。', source: '人工界面演示' },
  { id: 'demo-barbell-squat', name: '杠铃深蹲', english: 'Barbell squat', muscle: '腿', equipment: '杠铃', description: '展示腿部肌群与杠铃器械分类的界面示例。', source: '人工界面演示' }
];
