import { exercises } from './data/demoExercises';
import { useState } from 'react';
const muscles = ['胸', '背', '肩', '手臂', '核心', '腿'];
export default function App() {
  const [selected, setSelected] = useState<string[]>([]);
  function toggle(muscle: string) { setSelected(current => current.includes(muscle) ? current.filter(m => m !== muscle) : [...current, muscle]); }
  return <main><header><a href="#">Ready Gym<span> / 动作探索</span></a><span>第一块 · 前端原型</span></header>
    <h1>先选肌群，再找动作。</h1><p>从身体出发，探索适合器械条件的动作。</p>
    <aside>未接入 Agent · 不生成训练计划</aside>
    <section aria-label="肌群选择"><h2>01 / 选择目标肌群</h2><div className="body-pair">{['正面', '背面'].map(side => <figure key={side}><svg viewBox="0 0 200 340" role="group" aria-label={`人体${side}`}><circle cx="100" cy="30" r="20" className="body-base"/><path className="body-base" d="M85 53 L115 53 L145 68 L162 166 L145 178 L130 110 L126 189 L133 307 L109 322 L100 224 L91 322 L67 307 L74 189 L70 110 L55 178 L38 166 L55 68 Z"/>{[
      { m: side === '正面' ? '胸' : '背', d: 'M74 78 Q100 67 126 78 L125 119 Q100 130 75 119 Z', x:100, y:102 },
      { m:'肩', d:'M57 68 L77 69 L71 91 L51 92 Z M123 69 L143 68 L149 92 L129 91 Z', x:100, y:70 },
      { m:'手臂', d:'M51 99 L69 98 L56 162 L42 159 Z M131 98 L149 99 L158 159 L144 162 Z', x:100, y:157 },
      { m:'核心', d:'M77 129 L123 129 L120 186 L80 186 Z', x:100, y:145 },
      { m:'腿', d:'M78 195 L96 195 L88 304 L71 300 Z M104 195 L122 195 L129 300 L112 304 Z', x:100, y:249 }
    ].map(region => <g key={region.m} role="button" aria-label={`${side} ${region.m}`} aria-pressed={selected.includes(region.m)} tabIndex={0} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggle(region.m); } }} onClick={() => toggle(region.m)} className={`muscle-region ${selected.includes(region.m) ? 'selected' : ''}`}><path d={region.d}/><text x={region.x} y={region.y} textAnchor="middle">{selected.includes(region.m) ? '✓ ' : ''}{region.m}</text></g>)}</svg><figcaption>{side} · 示意图</figcaption></figure>)}</div><div className="muscle-buttons">{muscles.map(m => <button key={m} aria-label={m} aria-pressed={selected.includes(m)} onClick={() => toggle(m)}>{selected.includes(m) ? '✓ ' : '+ '}{m}</button>)}</div>
    <p role="status">{selected.length ? `已选：${selected.join('、')}` : '未选择肌群 · 浏览全部动作'}</p></section>
    <section aria-label="动作列表"><h2>02 / 浏览动作</h2><p>人工编写的界面演示数据 · 非真实源数据</p>{exercises.filter(exercise => !selected.length || selected.includes(exercise.muscle)).map(exercise => <article key={exercise.id}><h3>{exercise.name}</h3><p>{exercise.muscle} · {exercise.equipment}</p><button disabled title="详情尚未实现" aria-label={`查看 ${exercise.name}`}>查看 {exercise.name}</button></article>)}</section>
    <p>动作信息未经专业审核，不构成训练或医疗建议。</p>
  </main>;
}
