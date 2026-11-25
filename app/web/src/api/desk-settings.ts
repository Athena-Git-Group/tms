import { queryOptions } from '@tanstack/react-query';
import { apiGet, apiPatch } from './client';

export interface DeskSettings {
  inspectionRequired: boolean;
  repairVerificationRequired: boolean;
}

export type DeskSettingsPatch = Partial<DeskSettings>;

export const deskSettingsQuery = queryOptions({
  queryKey: ['desk-settings'],
  queryFn: ({ signal }) => apiGet<DeskSettings>('/desk/settings', { signal }),
  retry: false,
});

export function patchDeskSettings(patch: DeskSettingsPatch): Promise<DeskSettings> {
  return apiPatch<DeskSettings>('/desk/settings', patch);
}
