/**
 * 繁體中文的介面文字，也是「有哪些鍵」的唯一依據：其他三種語言的檔只能有這裡有的鍵。
 * 鍵照畫面分組（例如 `login.title`、`board.columns.triage`），各畫面的文字由做那個畫面的人加進來，
 * 字面照驗收規格，不放 `triage`、`pool` 這類內部值。
 */
export const zhTW = {
  app: {
    name: 'TMS 櫃檯',
  },
} as const;

type Shape<T> = { [K in keyof T]?: T[K] extends string ? string : Shape<T[K]> };

/** 其他語言的檔用這個型別，鍵打錯或多出繁體中文沒有的鍵時編譯就會擋下來。 */
export type Translation = Shape<typeof zhTW>;
