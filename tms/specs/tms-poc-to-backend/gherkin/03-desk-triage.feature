# language: zh-TW
@desk
功能: 櫃檯判讀自由文字
  作為櫃檯，我希望對待判讀需求三選一（給房務／給工務／當場回答），
  以便猜不準的需求有人裁決。（spec 故事 3）

  背景:
    假設 房 305 的自由文字需求「房間有怪聲」停在 triage（dept frontdesk）

  規則: FR-021 判讀的部門值域與任務同一套

    @error @boundary
    場景: 不合法部門被拒
      當 櫃檯以 dept "cleaning" 判讀該單
      那麼 回應為 422（值域僅 housekeeping|engineering）

    @happy
    場景: 轉給房務
      當 櫃檯以 action "assign"、dept "housekeeping" 判讀
      那麼 任務轉為 "pool"、dept 為 "housekeeping"、事件 "triaged" 帶部門與命名

  規則: FR-020 當場回答直接結案、回覆住客看得到

    @happy
    場景: 資訊類問題當場回答
      假設 另一筆 triage 需求原文為「早餐幾點」
      當 櫃檯以 action "answer"、note "早餐 07:00–10:00" 判讀
      那麼 任務轉為 "done" 且事件記 "answered"
      並且 回覆存在任務 note、住客查詢進度時可見

  規則: FR-022 櫃檯看得到本店所有單（防重複建單）

    @happy
    場景: 全單視圖
      假設 本店同時存在 triage、pool、accepted、done 的任務
      當 櫃檯查詢任務清單
      那麼 各狀態的任務全部可見
