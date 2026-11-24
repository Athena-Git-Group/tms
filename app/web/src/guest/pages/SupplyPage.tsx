import { useReducer } from 'react';
import { useTranslation } from 'react-i18next';
import { createRequest } from '../api/client';
import { BottomBar } from '../components/BottomBar';
import { buttonClass } from '../components/button-class';
import { Icon } from '../components/Icon';
import { InlineNotice } from '../components/InlineNotice';
import { PageLoading } from '../components/PageLoading';
import { TitleBar } from '../components/TitleBar';
import {
  MAX_QUANTITY,
  NO_QUANTITIES,
  chosenCount,
  quantitiesReducer,
  quantityOf,
  submitLabel,
} from '../lib/supply-quantities';
import { useGuestHome, useGuestQuota, useGuestRequests } from '../queries';
import { useReadySession } from '../session';
import { useGuestSubmit } from '../use-guest-submit';
import { useQuotaGuard } from '../use-quota-guard';

export function SupplyPage() {
  const { t } = useTranslation();
  const session = useReadySession();
  const home = useGuestHome(session.qrToken);
  const requests = useGuestRequests(session.qrToken);
  const [quantities, dispatchQuantity] = useReducer(quantitiesReducer, NO_QUANTITIES);
  const guard = useQuotaGuard(useGuestQuota(session.qrToken));
  const { sending, submit } = useGuestSubmit();

  const title = t('guest.supply.title');
  const titleBar = <TitleBar title={title} />;
  if (!home.data || !requests.data) {
    return (
      <>
        {titleBar}
        <PageLoading />
      </>
    );
  }

  const chosen = chosenCount(quantities);
  const menu = home.data.menu;
  const dispatch: typeof dispatchQuantity = (action) => {
    guard.dismiss();
    dispatchQuantity(action);
  };

  const send = async () => {
    if (sending || !guard.check(chosen)) return;
    const chosenItems = menu.filter((item) => quantityOf(quantities, item.key) > 0);
    const result = await submit(chosenItems, (item, sessionToken, key) =>
      createRequest(sessionToken, { itemId: item.key, quantity: quantityOf(quantities, item.key) }, key),
    );
    if (result === null) return;
    for (const item of chosenItems.slice(0, result.created.length)) dispatchQuantity({ type: 'reset', key: item.key });
    guard.report(result);
  };

  return (
    <>
      {titleBar}
      <main className="flex min-h-[calc(100dvh-var(--spacing-guest-title-bar))] flex-col gap-6 pt-2">
        <p className="m-0 text-guest-callout tracking-[-.23px] text-guest-text-secondary">{t('guest.supply.lead')}</p>
        <div className="flex flex-col overflow-hidden rounded-guest-lg bg-guest-surface shadow-guest-card">
          {menu.map((item) => {
            const name = item.label ?? item.key;
            const quantity = quantityOf(quantities, item.key);
            return (
              <div
                key={item.key}
                className="flex min-h-16 w-full items-center gap-3 px-4 py-2.5 text-guest-text [&+&]:shadow-[inset_0_.5px_0_var(--color-guest-separator)]"
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[11px] bg-guest-brand-accent-container text-[20px] text-guest-brand-on-accent-container">
                  <Icon name="cube_box" />
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="text-[17px] leading-[22px] font-semibold [overflow-wrap:anywhere]">{name}</span>
                  <span className="text-guest-footnote text-guest-text-secondary tabular-nums">
                    {t('guest.supply.max', { max: MAX_QUANTITY })}
                  </span>
                </span>
                <span className="inline-flex shrink-0 items-center gap-1">
                  <button
                    type="button"
                    aria-label={t('guest.supply.decrease', { name })}
                    disabled={sending || quantity <= 0}
                    onClick={() => dispatch({ type: 'decrement', key: item.key })}
                    className={STEPPER_BUTTON}
                  >
                    <Icon name="minus" />
                  </button>
                  <output
                    aria-live="polite"
                    className={`min-w-8 text-center text-[17px] leading-[22px] font-semibold tabular-nums ${
                      quantity === 0 ? 'text-guest-text-placeholder' : ''
                    }`}
                  >
                    {quantity}
                  </output>
                  <button
                    type="button"
                    aria-label={t('guest.supply.increase', { name })}
                    disabled={sending || quantity >= MAX_QUANTITY}
                    onClick={() => dispatch({ type: 'increment', key: item.key })}
                    className={STEPPER_BUTTON}
                  >
                    <Icon name="plus" />
                  </button>
                </span>
              </div>
            );
          })}
        </div>
        {guard.notice ? <InlineNotice notice={guard.notice} /> : null}
        <div className="flex-auto" />
        <BottomBar note={t('guest.supply.cancelNote')}>
          <button
            type="button"
            disabled={chosen === 0 || sending}
            aria-busy={sending || undefined}
            onClick={send}
            className={buttonClass('filled', { large: true })}
          >
            {t(submitLabel(chosen), { count: chosen })}
          </button>
        </BottomBar>
      </main>
    </>
  );
}

const STEPPER_BUTTON =
  'inline-flex size-guest-touch cursor-pointer items-center justify-center rounded-full border-0 bg-guest-fill p-0 text-[20px] text-guest-brand-primary disabled:cursor-default disabled:text-guest-text-placeholder disabled:opacity-60';
