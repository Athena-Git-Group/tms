import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { deskSettingsQuery, patchDeskSettings, type DeskSettings, type DeskSettingsPatch } from '../../api/desk-settings';
import { AlertDialog } from '../../components/AlertDialog';
import { LoadFailed } from '../../components/LoadFailed';
import { SkeletonBlock } from '../../components/SkeletonBlock';
import { Switch } from '../../components/Switch';
import { loadStateOf } from '../../lib/load-state';

const SWITCHES = [
  { field: 'inspectionRequired', hints: ['settings.inspectionRequired.hint', 'settings.inspectionRequired.note'] },
  { field: 'repairVerificationRequired', hints: ['settings.repairVerificationRequired.hint'] },
] as const satisfies readonly { field: keyof DeskSettings; hints: readonly string[] }[];

function useSaveSetting() {
  const queryClient = useQueryClient();
  const [notSaved, setNotSaved] = useState(false);
  const save = useMutation({
    mutationFn: patchDeskSettings,
    onMutate: async (patch: DeskSettingsPatch) => {
      await queryClient.cancelQueries({ queryKey: deskSettingsQuery.queryKey });
      const before = queryClient.getQueryData(deskSettingsQuery.queryKey);
      if (before) queryClient.setQueryData(deskSettingsQuery.queryKey, { ...before, ...patch });
      return { before };
    },
    onError: (_error, _patch, context) => {
      if (context?.before) queryClient.setQueryData(deskSettingsQuery.queryKey, context.before);
      setNotSaved(true);
    },
    onSuccess: (saved) => queryClient.setQueryData(deskSettingsQuery.queryKey, saved),
  });
  return { save, notSaved, closeNotice: () => setNotSaved(false) };
}

export function SettingsPage() {
  const { t } = useTranslation();
  const { data, isError, errorUpdatedAt, refetch } = useQuery(deskSettingsQuery);
  const { save, notSaved, closeNotice } = useSaveSetting();
  const idPrefix = useId();
  const load = loadStateOf({ data, isError, errorUpdatedAt });

  if (load === 'failed') {
    return (
      <section data-page="settings" className="flex min-h-0 flex-auto flex-col px-4 pb-6">
        <LoadFailed onRetry={() => void refetch()} />
      </section>
    );
  }
  if (load === 'loading' || !data) {
    return (
      <section data-page="settings" aria-busy="true" className="flex min-h-0 flex-auto flex-col gap-2.5 px-gutter pb-6">
        <SkeletonBlock className="h-40 max-w-[720px] rounded-card" />
      </section>
    );
  }

  return (
    <section data-page="settings" className="flex min-h-0 flex-auto flex-col gap-2 overflow-y-auto px-gutter pt-2 pb-6">
      <div className="flex max-w-[720px] flex-col overflow-hidden rounded-card bg-surface">
        {SWITCHES.map(({ field, hints }, index) => {
          const labelId = `${idPrefix}-${field}`;
          const hintId = `${labelId}-hint`;
          return (
            <div
              key={field}
              className={`flex items-center gap-4 px-4 py-3 ${index > 0 ? 'border-t-[0.5px] border-line' : ''}`}
            >
              <div className="flex grow flex-col gap-1">
                <span id={labelId} className="text-[17px] font-semibold">
                  {t(`settings.${field}.label`)}
                </span>
                <span id={hintId} className="flex flex-col gap-0.5 text-[13px] leading-[1.45] text-text-2">
                  {hints.map((hint) => (
                    <span key={hint}>{t(hint)}</span>
                  ))}
                </span>
              </div>
              <Switch
                checked={data[field]}
                disabled={save.isPending}
                labelledBy={labelId}
                describedBy={hintId}
                onChange={(checked) => save.mutate({ [field]: checked })}
              />
            </div>
          );
        })}
      </div>
      <span className="max-w-[720px] px-4 text-[13px] leading-[1.45] text-text-2">{t('settings.footer')}</span>
      {notSaved && (
        <AlertDialog title={t('settings.notSaved.title')} body={t('settings.notSaved.body')} onClose={closeNotice} />
      )}
    </section>
  );
}
