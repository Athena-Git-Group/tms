import type { DeskTaskDetail, RepairPhoto } from '../api/types';

export type PhotoRowKind = 'report' | 'completion';

export interface PhotoRow {
  kind: PhotoRowKind;
  photoIds: string[];
}

export function repairPhotoRows(task: Pick<DeskTaskDetail, 'repair'>): PhotoRow[] {
  const repair = task.repair;
  if (!repair) return [];
  const rows: [PhotoRowKind, RepairPhoto[] | undefined][] = [
    ['report', repair.photos],
    ['completion', repair.completionPhotos],
  ];
  return rows.flatMap(([kind, photos]) => (photos?.length ? [{ kind, photoIds: photos.map((photo) => photo.id) }] : []));
}
