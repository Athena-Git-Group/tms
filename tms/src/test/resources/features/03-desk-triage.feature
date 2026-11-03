# language: zh-TW
@desk
功能: 櫃檯判讀自由文字
  作為櫃檯，我希望對待判讀需求三選一（給房務／給工務／當場回答），
  以便猜不準的需求有人裁決。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name   | login_id | password_hash | role         | dept         |
      | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk    | frontdesk    |
      | s-hk1 | 1         | 小美   | hk1      | hash-hk1      | housekeeping | housekeeping |
    並且 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-305 | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
    並且 系統中存在以下任務:
      | id      | tenant_id | type  | source | room_id | dept      | state  | title      |
      | t-305-1 | 1         | other | guest  | r-305   | frontdesk | triage | 房間有怪聲 |

  Rule: 判讀的部門值域與任務同一套

    @error @boundary
    場景: 不合法部門被拒
      當 櫃檯以 dept "cleaning" 判讀任務 "t-305-1"
      那麼請求應該失敗，回應為 422
      並且 該任務仍停在 "triage"

    @error @boundary
    場景: 未知 action 被拒
      當 櫃檯以 action "forward" 判讀任務 "t-305-1"
      那麼請求應該失敗，回應為 422
      並且 該任務仍停在 "triage"

    @error @boundary
    場景: 不帶回覆的 answer 被拒
      當 櫃檯以 action "answer" 但不帶 note 判讀任務 "t-305-1"
      那麼請求應該失敗，回應為 422
      並且 該任務仍停在 "triage"

    @happy
    場景: 轉給房務
      當 櫃檯以 action "assign"、dept "housekeeping" 判讀任務 "t-305-1"
      那麼 任務 "t-305-1" 的資料應為:
        | state | dept         |
        | pool  | housekeeping |
      並且 任務 "t-305-1" 的事件 "triaged" 的 dept 應為 "housekeeping"
      並且 任務 "t-305-1" 的事件 "triaged" 的操作者應為「林櫃檯」
      並且 房務「小美」的待接池立刻看得到這單

  Rule: 當場回答直接結案、回覆住客看得到

    @happy
    場景: 資訊類問題當場回答
      假設 系統中存在以下任務:
        | id      | tenant_id | type  | source | room_id | dept      | state  | title    |
        | t-305-2 | 1         | other | guest  | r-305   | frontdesk | triage | 早餐幾點 |
      當 櫃檯以 action "answer"、note "早餐 07:00–10:00" 判讀任務 "t-305-2"
      那麼 任務 "t-305-2" 的 state 應為 "done"
      並且 回應的任務 "t-305-2" 的 doneAt 應有值
      並且 任務 "t-305-2" 已寫入事件 "answered"
      並且 任務 "t-305-2" 的事件 "answered" 的 answer 應為 "早餐 07:00–10:00"
      並且 任務 "t-305-2" 的 note 應為 "早餐 07:00–10:00"

  Rule: 櫃檯看得到本店所有單，才能避免重複建單

    @happy
    場景: 多筆任務清單
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state    | title    | assignee_id |
        | t-305-3 | 1         | amenity | guest  | r-305   | housekeeping | pool     | 加毛巾   |             |
        | t-305-4 | 1         | amenity | guest  | r-305   | housekeeping | accepted | 礦泉水   | s-hk1       |
        | t-305-5 | 1         | amenity | guest  | r-305   | housekeeping | done     | 補牙刷   | s-hk1       |
      當 櫃檯查詢任務清單
      那麼 清單含標題「房間有怪聲」且其狀態為 "triage"
      並且 清單含標題「加毛巾」且其狀態為 "pool"
      並且 清單含標題「礦泉水」且其狀態為 "accepted"
      並且 清單含標題「補牙刷」且其狀態為 "done"
      並且 待判讀清單只含 triage 的任務

  Rule: 不存在的任務一律 404 not_found

    @error @boundary
    場景: 判讀不存在的任務
      當 櫃檯判讀一個不存在的任務
      那麼請求應該失敗，回應為 404 且原因是 "not_found"
