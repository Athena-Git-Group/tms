import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import type { DeskCreateRequestDept } from '../../api/types';
import { SegmentedControl } from '../../components/SegmentedControl';

const DEPTS: readonly DeskCreateRequestDept[] = ['housekeeping', 'engineering'];

export function OtherRequest({
  text,
  dept,
  showDept,
  disabled,
  onTextChange,
  onDeptChange,
}: {
  text: string;
  dept: DeskCreateRequestDept;
  showDept: boolean;
  disabled: boolean;
  onTextChange: (text: string) => void;
  onDeptChange: (dept: DeskCreateRequestDept) => void;
}) {
  const { t } = useTranslation();
  const titleId = useId();

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-2">
      <span id={titleId} className="px-1 text-[13px] text-text-2">
        {t('createRequest.otherTitle')}
      </span>
      <div className="rounded-card bg-surface px-4 py-3 focus-within:shadow-[0_0_0_3px_var(--color-brand)]">
        <textarea
          rows={2}
          aria-label={t('createRequest.otherLabel')}
          placeholder={t('createRequest.otherPlaceholder')}
          readOnly={disabled}
          value={text}
          onChange={(event) => onTextChange(event.target.value)}
          className="block w-full resize-none border-none bg-transparent p-0 text-base leading-[1.45] text-text outline-none placeholder:text-text-4"
        />
      </div>
      {showDept && (
        <div className="flex items-center gap-3 px-1">
          <span className="text-[13px] text-text-2">{t('createRequest.sendTo')}</span>
          <SegmentedControl
            label={t('createRequest.otherDeptGroup')}
            options={DEPTS.map((value) => ({
              value,
              label: t(`board.card.depts.${value}`),
            }))}
            value={dept}
            onChange={onDeptChange}
            disabled={disabled}
          />
        </div>
      )}
    </section>
  );
}
