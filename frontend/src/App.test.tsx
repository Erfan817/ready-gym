import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import App from './App';
describe('肌群与动作探索', () => {
  it('肌群过滤展示匹配动作且多选取并集', async () => {
    render(<App />); const user = userEvent.setup();
    expect(screen.getByText(/人工编写的界面演示数据/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '胸' }));
    expect(screen.getByRole('button', { name: '查看 俯卧撑' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '查看 徒手深蹲' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '腿' }));
    expect(screen.getByRole('button', { name: '查看 徒手深蹲' })).toBeInTheDocument();
  });
  it('SVG 支持键盘 Enter 与空格切换', async () => {
    render(<App />); const user = userEvent.setup();
    const chest = screen.getByRole('button', { name: '正面 胸' }); chest.focus();
    await user.keyboard('{Enter}'); expect(chest).toHaveAttribute('aria-pressed', 'true');
    await user.keyboard(' '); expect(chest).toHaveAttribute('aria-pressed', 'false');
  });
  it('正反面 SVG 可点击且与文字按钮同步', async () => {
    render(<App />); const user = userEvent.setup();
    const front = screen.getByRole('group', { name: '人体正面' });
    const back = screen.getByRole('group', { name: '人体背面' });
    await user.click(within(front).getByRole('button', { name: '正面 胸' }));
    expect(screen.getByRole('button', { name: '胸' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: '背' }));
    expect(within(back).getByRole('button', { name: '背面 背' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(back).getByRole('button', { name: '背面 背' })).toHaveTextContent('✓ 背');
  });
  it('文字肌群按钮点选与取消后同步状态', async () => {
    render(<App />); const user = userEvent.setup();
    const chest = screen.getByRole('button', { name: '胸' });
    await user.click(chest);
    expect(chest).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('status')).toHaveTextContent('已选：胸');
    await user.click(chest);
    expect(chest).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('status')).toHaveTextContent('未选择肌群');
  });
  it('说明当前功能边界与未审核状态', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: '先选肌群，再找动作。' })).toBeInTheDocument();
    expect(screen.getByText(/未接入 Agent/)).toBeInTheDocument();
    expect(screen.getByText(/未经专业审核/)).toBeInTheDocument();
  });
});
