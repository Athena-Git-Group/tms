/**
 * T004 spike：拖曳套件選型的最小畫面（一張可拖的卡片、兩個拖放區），不是產品程式，`src/` 不得引用。
 * 開發伺服器起來後開 `/spikes/dnd/index.html`。T005 用 Playwright 驅動這一頁，可用的 `data-testid`：
 * `card`、`overlay-card`、`zone-housekeeping`、`zone-engineering`、`zone-<部門>-hint`、`drag-state`、
 * `result`、`column-pending`、`column-<部門>`、`assigned-card`；`window.__spike` 記下頁面實際收到的事件。
 */
import { PointerActivationConstraints } from '@dnd-kit/dom';
import { DragDropProvider, DragOverlay, PointerSensor, useDraggable, useDroppable } from '@dnd-kit/react';
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { installLog, logLib } from './log';
import './spike.css';

type Department = 'housekeeping' | 'engineering';
const DEPARTMENT_NAME: Record<Department, string> = { housekeeping: '房務', engineering: '工務' };
const CARD_TEXT = '305 多送兩條毛巾';

/** 滑鼠移動超過 6px 起拖；手指按住 0.25 秒起拖（按住期間容許 5px 的抖動）。 */
const sensors = [
  PointerSensor.configure({
    activationConstraints(event) {
      if (event.pointerType === 'touch') {
        return [new PointerActivationConstraints.Delay({ value: 250, tolerance: 5 })];
      }
      return [new PointerActivationConstraints.Distance({ value: 6 })];
    },
  }),
];

function Card() {
  const { ref, isDragSource } = useDraggable({ id: 'card' });
  return (
    <div ref={ref} data-testid="card" className={isDragSource ? 'card placeholder' : 'card'}>
      {CARD_TEXT}
    </div>
  );
}

function Zone({ department }: { department: Department }) {
  const { ref, isDropTarget } = useDroppable({ id: department });
  const name = DEPARTMENT_NAME[department];
  return (
    <div ref={ref} data-testid={`zone-${department}`} className={isDropTarget ? 'zone over' : 'zone'}>
      <strong>給{name}</strong>
      <span data-testid={`zone-${department}-hint`}>{isDropTarget ? '放開就完成派單' : `放進${name}待認領`}</span>
    </div>
  );
}

function Spike() {
  const [assigned, setAssigned] = useState<Department | null>(null);
  const [dragging, setDragging] = useState(false);
  return (
    <DragDropProvider
      sensors={sensors}
      onDragStart={() => {
        logLib('dragstart');
        setDragging(true);
      }}
      onDragOver={(event) => logLib(`dragover:${String(event.operation.target?.id ?? 'none')}`)}
      onDragEnd={(event) => {
        const target = event.operation.target?.id;
        logLib(`dragend:${String(target ?? 'none')}:${event.canceled ? 'canceled' : 'dropped'}`);
        setDragging(false);
        if (!event.canceled && (target === 'housekeeping' || target === 'engineering')) setAssigned(target);
      }}
    >
      <div className="bar" data-testid="drag-state">
        {dragging ? '拖曳中' : '平時'}
      </div>
      <div className="bar" data-testid="result">
        {assigned ? `已派給${DEPARTMENT_NAME[assigned]}` : '尚未派單'}
      </div>
      <div className="board" data-testid="board">
        <div className="column" data-testid="column-pending">
          <h2>待派單</h2>
          {assigned === null && <Card />}
        </div>
        {(['housekeeping', 'engineering'] as const).map((department) => (
          <div className="column" key={department} data-testid={`column-${department}`}>
            <h2>{DEPARTMENT_NAME[department]}待認領</h2>
            {dragging && <Zone department={department} />}
            {assigned === department && (
              <div className="card" data-testid="assigned-card">
                {CARD_TEXT}
              </div>
            )}
          </div>
        ))}
        <div className="column" style={{ flexBasis: 900 }}>
          <h2>（撐寬用，讓看板可以橫向捲動）</h2>
        </div>
      </div>
      <DragOverlay>
        <div className="card" data-testid="overlay-card">
          {CARD_TEXT}
        </div>
      </DragOverlay>
      <div className="filler">（撐高用，讓頁面可以上下捲動）</div>
    </DragDropProvider>
  );
}

installLog();
createRoot(document.getElementById('root')!).render(<Spike />);
