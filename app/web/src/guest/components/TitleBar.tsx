import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { guestPath } from '../paths';
import { useReadySession } from '../session';
import { useDocumentTitle } from '../use-document-title';
import { Icon } from './Icon';

/**
 * 子頁共用的標題列：返回鈕、頁標題、介面語言的房號標籤，黏在頂端；內容捲到它底下才出現細線。
 * 也決定分頁上的標題「{頁標題} · {房號} 號房」。首頁、送出成功頁與進入層沒有標題列。返回沒有指定去處時回首頁。
 */
export function TitleBar({ title, backTo }: { title: string; backTo?: string }) {
  const { t } = useTranslation();
  const { roomNo, qrToken } = useReadySession();
  const scrolled = useScrolledPast(4);
  useDocumentTitle(t('guest.pageTitle', { title, room: t('guest.roomName', { roomNo }) }));
  return (
    <header
      className={`sticky top-0 z-20 -mr-(--guest-gutter-right) -ml-(--guest-gutter-left) flex min-h-guest-title-bar items-center gap-2 bg-guest-bg pt-[calc(8px+env(safe-area-inset-top))] pr-(--guest-gutter-right) pb-2 pl-[calc(var(--guest-gutter-left)-8px)] ${
        scrolled ? 'shadow-[0_.5px_0_var(--color-guest-separator)]' : ''
      }`}
    >
      <Link
        to={backTo ?? guestPath(qrToken)}
        aria-label={t('guest.back')}
        className="inline-flex size-guest-touch shrink-0 items-center justify-center rounded-guest-pill text-[24px] text-guest-brand-primary active:opacity-60"
      >
        <Icon name="chevron_back" />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col">
        <h1 className="m-0 text-guest-headline font-semibold tracking-[-.43px] [overflow-wrap:anywhere]">{title}</h1>
        <span className="text-guest-footnote tracking-[-.08px] text-guest-text-secondary">
          {t('guest.roomNo', { roomNo })}
        </span>
      </div>
    </header>
  );
}

function useScrolledPast(offset: number): boolean {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const update = () => setScrolled(window.scrollY > offset);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, [offset]);
  return scrolled;
}
