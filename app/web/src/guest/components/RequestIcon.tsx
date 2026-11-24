import type { TaskType } from '../api/types';
import { Icon } from './Icon';
import type { IconName } from './icon-paths';

const TYPE_ICON: Record<TaskType, IconName> = {
  amenity: 'cube_box',
  repair: 'hammer',
  cleaning: 'sparkles',
  other: 'bubble_left',
};

const SIZE = {
  row: 'size-9 rounded-[11px] text-[20px]',
  card: 'size-[52px] rounded-guest-card text-[28px]',
} as const;

const TONE = {
  brand: 'bg-guest-brand-container text-guest-brand-on-container-strong',
  neutral: 'bg-guest-status-neutral-container text-guest-status-on-neutral-container',
} as const;

export function RequestIcon({
  type,
  size,
  tone = 'brand',
}: {
  type: TaskType;
  size: keyof typeof SIZE;
  tone?: keyof typeof TONE;
}) {
  return (
    <span className={`flex shrink-0 items-center justify-center ${SIZE[size]} ${TONE[tone]}`}>
      <Icon name={TYPE_ICON[type]} />
    </span>
  );
}
