import { describe, expect, it } from 'vitest';
import type { DeskTaskDetail } from '../api/types';
import { repairPhotoRows } from './repair-photo-rows';

const photo = (id: string) => ({ id, status: 'uploaded' as const, takenAt: '2026-11-26T06:32:00Z' });

function repair(photos: string[], completionPhotos: string[]): Pick<DeskTaskDetail, 'repair'> {
  return { repair: { photos: photos.map(photo), completionPhotos: completionPhotos.map(photo) } };
}

describe('repairPhotoRows', () => {
  it('不是工務單沒有任何排', () => {
    expect(repairPhotoRows({ repair: null })).toEqual([]);
    expect(repairPhotoRows({})).toEqual([]);
  });

  it('報修在上、完修在下，順序與回應相同', () => {
    expect(repairPhotoRows(repair(['r2', 'r1'], ['c1']))).toEqual([
      { kind: 'report', photoIds: ['r2', 'r1'] },
      { kind: 'completion', photoIds: ['c1'] },
    ]);
  });

  it('沒有照片的那一排不出現', () => {
    expect(repairPhotoRows(repair(['r1'], []))).toEqual([{ kind: 'report', photoIds: ['r1'] }]);
    expect(repairPhotoRows(repair([], []))).toEqual([]);
  });

  it('舊回應沒有照片欄位時沒有任何排', () => {
    expect(repairPhotoRows({ repair: { resolutionType: null } })).toEqual([]);
  });
});
