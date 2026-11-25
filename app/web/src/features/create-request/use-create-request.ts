import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { newIdempotencyKey } from '../../api/client';
import { createDeskRequests, receiveCreatedCards } from '../../api/desk';
import type { DeskCreateRequestDept } from '../../api/types';
import { logOut } from '../../lib/log-out';
import { PATHS, hotelPath } from '../../lib/paths';
import { useHotelCode } from '../../lib/use-hotel-code';
import { useBoardViewStore } from '../../stores/board-view';
import { useCreatedHighlightsStore } from '../../stores/created-highlights';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import {
  EMPTY_DRAFT,
  createRequestBody,
  filterRoomInput,
  isUnauthorized,
  keepIdempotencyKey,
  nextOtherText,
  quantityOf,
  submitFailureKind,
  ticketCount,
  withQuantity,
  type CreateRequestDraft,
  type MenuItem,
  type SubmitFailure,
} from './create-request-logic';

export const CREATE_REQUEST_TIMEOUT_MS = 15_000;

export interface NotSentNotice {
  kind: SubmitFailure;
  roomNo: string;
  count: number;
}

interface Submission {
  sent: CreateRequestDraft;
  key: string;
}

async function sendWithTimeout({ sent, key }: Submission) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), CREATE_REQUEST_TIMEOUT_MS);
  try {
    return await createDeskRequests(createRequestBody(sent), key, controller.signal);
  } finally {
    clearTimeout(timer);
  }
}

export function useCreateRequest(initialRoomNo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const hotelCode = useHotelCode();
  const closeCreateRequest = useDeskPanelsStore((state) => state.closeCreateRequest);
  const closeDetail = useDeskPanelsStore((state) => state.closeDetail);
  const updateView = useBoardViewStore((state) => state.update);
  const showCreated = useCreatedHighlightsStore((state) => state.show);
  const [draft, setDraft] = useState<CreateRequestDraft>({
    ...EMPTY_DRAFT,
    roomNo: filterRoomInput(initialRoomNo),
  });
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<NotSentNotice | null>(null);

  const send = useMutation({
    mutationFn: sendWithTimeout,
    onSuccess: (result) => {
      closeCreateRequest();
      closeDetail();
      updateView({ cardFilter: null, roomSearch: '' });
      void navigate(hotelPath(hotelCode, PATHS.board));
      receiveCreatedCards(queryClient, result.tasks);
      showCreated(result.tasks.map((task) => task.id));
    },
    onError: (failure: unknown, { sent }: Submission) => {
      if (isUnauthorized(failure)) {
        logOut(queryClient);
        return;
      }
      if (!keepIdempotencyKey(failure)) setIdempotencyKey(null);
      setNotice({
        kind: submitFailureKind(failure),
        roomNo: sent.roomNo,
        count: ticketCount(sent),
      });
    },
  });
  const submitting = send.isPending;

  const edit = (change: (current: CreateRequestDraft) => CreateRequestDraft) => {
    if (submitting) return;
    setIdempotencyKey(null);
    setDraft(change);
  };

  return {
    draft,
    submitting,
    notice,
    closeNotice() {
      setNotice(null);
    },
    setRoomNo(raw: string) {
      edit((current) => ({ ...current, roomNo: filterRoomInput(raw) }));
    },
    add(item: MenuItem) {
      edit((current) => withQuantity(current, item, quantityOf(current, item.key) + 1));
    },
    subtract(item: MenuItem) {
      edit((current) => withQuantity(current, item, quantityOf(current, item.key) - 1));
    },
    remove(item: MenuItem) {
      edit((current) => withQuantity(current, item, 0));
    },
    setOtherText(text: string) {
      edit((current) => ({
        ...current,
        otherText: nextOtherText(current.otherText, text),
      }));
    },
    setOtherDept(dept: DeskCreateRequestDept) {
      edit((current) => ({ ...current, otherDept: dept }));
    },
    submit() {
      if (submitting) return;
      const key = idempotencyKey ?? newIdempotencyKey();
      setIdempotencyKey(key);
      send.mutate({ sent: draft, key });
    },
  };
}
