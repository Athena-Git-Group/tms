# language: zh-TW
@inspection
功能: 主管查房與退回
  作為主管，我希望逐項判定查房、任一不過整間退回、全過才回寫 PMS，
  以便守住品質關卡。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name   | login_id | password_hash | role         | dept         |
      | s-mgr | 1         | 王主管 | mgr1     | hash-mgr1     | manager      |              |
      | s-hk1 | 1         | 小美   | hk1      | hash-hk1      | housekeeping | housekeeping |
      | s-hk2 | 1         | 阿強   | hk3      | hash-hk3      | housekeeping | housekeeping |
    並且 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301 | 1         | 301     | 3     | standard_double | cleaned  | occupied  |
    並且 系統中存在以下SOP項目:
      | id     | tenant_id | applies_to | item_key  | label | sort_order |
      | insp-1 | 1         | inspection | bed       | 床鋪  | 1          |
      | insp-2 | 1         | inspection | bathroom  | 浴室  | 2          |
      | insp-3 | 1         | inspection | amenities | 備品  | 3          |
      | insp-4 | 1         | inspection | floor     | 地板  | 4          |
      | insp-5 | 1         | inspection | odor      | 氣味  | 5          |
    並且 租戶 1 的班次設定涵蓋現在，清房者視為在班

  Rule: 自清自查必擋

    @error @boundary
    場景: 清房者查自己的房被拒
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-mgr       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管對任務 "t-301-1" 送出全過的查房
      那麼請求應該失敗，回應為 403 且原因是 "forbidden"
      並且 查房不留記錄、任務狀態不變
      並且 房務以自己的身分對查房端點送出判定的請求也應該失敗，回應為 403

  Rule: 查房輸入驗證——判定須與設定的查房項目一項不差、僅 done 任務可查

    @error @boundary
    場景: 缺項不收
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管對任務 "t-301-1" 只送出 bed、bathroom 兩項判定
      那麼請求應該失敗，回應為 422 且原因是 "validation_failed"
      並且 查房不留記錄、任務狀態不變

    @error @boundary
    場景: 夾帶清單外的項目不收
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管對任務 "t-301-1" 送出全過的查房，但夾帶清單外的項目 "window"
      那麼請求應該失敗，回應為 422 且原因是 "validation_failed"
      並且 查房不留記錄、任務狀態不變

    @error @boundary
    場景: 非 done 狀態不可查房
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | doing | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19 |
      當 王主管對任務 "t-301-1" 送出全過的查房
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"
      並且 查房不留記錄、任務狀態不變

  Rule: 兩分支——全過可賣、任一不過整間退回

    @happy
    場景: 全過即可賣且 outbox 僅落任務事件
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管對任務 "t-301-1" 送出全過的查房
      那麼 回應的查房結果為 "passed" 且任務轉為 "inspected"
      並且 房間 "r-301" 的資料應為:
        | hk_state  |
        | inspected |
      並且 outbox 排入的訊息 kind 僅有 "task_event"
      並且 inspections 留下一列 passed、items 完整存下每個查房項目的判定

    @boundary
    場景: 任一項不過整間退回並清空重來
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk2       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      並且 系統中存在以下SOP勾記:
        | id    | tenant_id | task_id | item_key | label | checked |
        | chk-1 | 1         | t-301-1 | bed      | 床鋪  | true    |
        | chk-2 | 1         | t-301-1 | bathroom | 浴室  | true    |
      當 王主管判定 bed 不過、其餘全過
      那麼 回應的查房結果為 "failed" 且任務狀態為 "accepted"
      並且 任務 "t-301-1" 的資料應為:
        | state    | redo_count | assignee_id | started_at | done_at |
        | accepted | 1          | s-hk2       |            |         |
      並且 任務 "t-301-1" 的 SOP 勾記數應為 0
      並且 房間 "r-301" 的資料應為:
        | hk_state |
        | dirty    |
      並且 事件 "inspection_failed" 帶不過項目「床鋪」
      # 連動退池：清房者請假或下班時，他手上未完成的任務會自動退回待接池讓別人接。
      # 這裡清房者仍在班上，所以查房退回不會觸發這個機制。
      並且 沒有連動退池
      但是 第一輪的 "started" 與 "completed" 仍可由事件流完整回溯

    @boundary
    場景: 重查通過追加 inspections 歷史
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk2       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      並且 王主管判定 bed 不過、其餘全過
      當 原清房者重掃重交、王主管再送出全過的查房
      那麼 inspections 共兩列、最新一列為 passed、先前一列為 failed，且 redo_count 仍為 1

    @boundary
    場景: 退回時原清房者已下班則連動退池
      假設 照租戶班次設定「小美」已下班
      並且 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管判定 bathroom 不過、其餘全過
      那麼 回應的 returnedToPool 為 true
      並且 任務 "t-301-1" 的資料應為:
        | state | assignee_id |
        | pool  |             |
      並且 同一筆交易寫入兩筆相鄰事件："inspection_failed" 緊跟 "returned_to_pool"
      並且 outbox 排入一筆 push、payload 帶優先同樓層提示，含 floor 3、房 301、dept housekeeping

    @boundary
    場景: 退回時原清房者請假中則連動退池
      假設 系統中存在以下請假:
        | id   | tenant_id | staff_id | from_at              | to_at                | created_by |
        | lv-1 | 1         | s-hk1    | 2026-10-01T00:00:00Z | 2026-12-31T00:00:00Z | s-mgr      |
      並且 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 王主管判定 odor 不過、其餘全過
      那麼 任務 "t-301-1" 的資料應為:
        | state |
        | pool  |
      並且 事件流含 "returned_to_pool"
