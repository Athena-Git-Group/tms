import { useTranslation } from 'react-i18next';
import { greetingIsCompact, greetingKey } from '../../lib/greeting';

/** 「{房號} 號房」一行，下面兩行問候：依手機時間的早安／午安／晚安，與「今天需要什麼？」。 */
export function Hello({ roomNo }: { roomNo: string }) {
  const { t } = useTranslation();
  const prompt = t('guest.greeting.prompt');
  const compact = greetingIsCompact([
    t('guest.greeting.morning'),
    t('guest.greeting.afternoon'),
    t('guest.greeting.evening'),
    prompt,
  ]);
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-guest-callout text-guest-text-secondary">{t('guest.roomName', { roomNo })}</span>
      <h1
        className={`m-0 font-bold [overflow-wrap:anywhere] ${
          compact ? 'text-guest-greeting-compact tracking-[.38px]' : 'text-guest-greeting tracking-[.37px]'
        }`}
      >
        {t(greetingKey(new Date()))}
        <em className="block text-guest-brand-primary not-italic">{prompt}</em>
      </h1>
    </div>
  );
}
