import type { TFunction } from 'i18next';

export type DisplayText = (t: TFunction) => string;

export function displayString(text: DisplayText, t: TFunction): string {
  return text(t);
}
