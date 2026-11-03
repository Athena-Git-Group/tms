# language: zh-TW
@staff
功能: 員工在部門待接池認領、開始、完成
  作為房務員／工務，我希望在部門待接池認領需求並用兩段回報，
  以便不靠對講機交辦。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name | login_id | password_hash | role         | dept         |
      | s-hk1 | 1         | 小美 | hk1      | hash-hk1      | housekeeping | housekeeping |
      | s-hk2 | 1         | 小華 | hk2      | hash-hk2      | housekeeping | housekeeping |
      | s-mt1 | 1         | 阿強 | mt1      | hash-mt1      | engineering  | engineering  |
    並且 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301 | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305 | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
      | r-502 | 1         | 502     | 5     | suite           | dirty    | occupied  |
    並且 系統中存在以下任務:
      | id      | tenant_id | type    | source | room_id | dept         | state | title  |
      | t-305-1 | 1         | amenity | guest  | r-305   | housekeeping | pool  | 毛巾×2 |
    並且 系統中存在以下SOP項目:
      | id    | tenant_id | applies_to | item_key  | label | sort_order |
      | sop-1 | 1         | cleaning   | bed       | 床鋪  | 1          |
      | sop-2 | 1         | cleaning   | bathroom  | 浴室  | 2          |
      | sop-3 | 1         | cleaning   | floor     | 地板  | 3          |
      | sop-4 | 1         | cleaning   | amenities | 備品  | 4          |

  Rule: 認領——限同部門、限 pool、同秒只有一人成功

    @error @boundary
    場景: 跨部門不能認領
      當 工務「阿強」認領任務 "t-305-1"
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"
      並且 任務 "t-305-1" 的 state 應為 "pool"
      並且 任務 "t-305-1" 仍只有 "created" 一筆事件

    @error @boundary
    場景: 已被認領的單再認領被擋
      假設 「小美」已認領任務 "t-305-1"
      當 另一位房務「小華」認領任務 "t-305-1"
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"

    @boundary
    場景: 兩人同一秒搶單恰一人成功
      當 「小美」與「小華」同秒對任務 "t-305-1" 送出認領
      那麼 恰有一人成功、另一人的請求應該失敗，回應為 409
      並且 任務 event_seq 僅遞增一次：created 的 seq 為 1、claimed 的 seq 為 2，輸家不佔號

    @happy
    場景: 同部門認領成功
      當 房務「小美」認領任務 "t-305-1"
      那麼 任務 "t-305-1" 的資料應為:
        | state    | assignee_id |
        | accepted | s-hk1       |
      並且 任務 "t-305-1" 已寫入事件 "claimed"
      並且 任務 "t-305-1" 的事件 "claimed" 的操作者應為「小美」

  Rule: 任務狀態機——非法轉移一律 409

    @error @boundary
    場景大綱: 非法狀態轉移
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state  | title | assignee_id   |
        | t-301-9 | 1         | amenity | guest  | r-301   | housekeeping | <目前> | 面紙  | <assignee_id> |
      當 「小美」對任務 "t-301-9" 執行 <動作>
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"

      例子:
        | 目前     | assignee_id | 動作     |
        | accepted | s-hk1       | complete |
        | pool     |             | start    |
        | done     | s-hk1       | start    |

    # 實際工時由 done_at 減 started_at 算出，不另外存分鐘欄位。
    @happy
    場景: 開始與完成記錄實際工時
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state    | title    | assignee_id | est_min | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | accepted | 退房清潔 | s-hk1       | 30      | 2026-10-19 |
      當 「小美」按「開始」再按「完成」
      那麼 state 依序為 "doing"、"done"
      並且 started_at 與 done_at 被記錄
      並且 事件 "started" 與 "completed" 寫入

  Rule: 兩段回報限認領者本人——他人操作一律 409

    @error @boundary
    場景: 別部門員工對他人的單按開始被擋
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state    | title    | assignee_id | est_min | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | accepted | 退房清潔 | s-hk1       | 30      | 2026-10-19 |
      當 「阿強」對任務 "t-301-1" 執行 start
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"

    @error @boundary
    場景: 非認領者按完成被擋
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | est_min | started_at           | work_date  |
        | t-502-1 | 1         | cleaning | system | r-502   | housekeeping | doing | 退房清潔 | s-hk1       | 30      | 2026-10-19T09:00:00Z | 2026-10-19 |
      當 「小華」對任務 "t-502-1" 執行 complete
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"

  Rule: SOP 逐項打勾——跳過不擋流程但留紀錄

    @happy
    場景: 跳過備品項仍可完成
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | est_min | started_at           | work_date  |
        | t-502-1 | 1         | cleaning | system | r-502   | housekeeping | doing | 退房清潔 | s-hk1       | 30      | 2026-10-19T09:00:00Z | 2026-10-19 |
      當 「小美」勾選「床鋪」「浴室」「地板」並跳過「備品」再按完成
      那麼 完成成功
      並且 勾記共 4 筆且「備品」留痕為跳過，記錄操作者、時間與 label 快照
      並且 每次勾選與跳過都寫入事件 "sop_checked"

    @error @boundary
    場景: 勾選與跳過互斥
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state    | title    | assignee_id | est_min | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | accepted | 退房清潔 | s-hk1       | 30      | 2026-10-19 |
      當 「小美」對任務 "t-301-1" 的「床鋪」同時送出勾選與跳過
      那麼請求應該失敗，回應為 422

    @error @boundary
    場景: 空白動作被拒
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state    | title    | assignee_id | est_min | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | accepted | 退房清潔 | s-hk1       | 30      | 2026-10-19 |
      當 「小美」對任務 "t-301-1" 的「床鋪」送出空白動作
      那麼請求應該失敗，回應為 422

    @error @boundary
    場景: 未知項目鍵 404
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state    | title    | assignee_id | est_min | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | accepted | 退房清潔 | s-hk1       | 30      | 2026-10-19 |
      當 「小美」對任務 "t-301-1" 的未知項目 "qr_sticker" 送出勾選
      那麼請求應該失敗，回應為 404 且原因是 "not_found"

  Rule: 待接池只回本部門

    @happy
    場景: 房務看不到工務的單
      假設 系統中存在以下任務:
        | id      | tenant_id | type   | source | room_id | dept        | state | title    |
        | t-305-2 | 1         | repair | guest  | r-305   | engineering | pool  | 冷氣不冷 |
      當 「小美」查詢待接池
      那麼 清單含「毛巾×2」但不含「冷氣不冷」且全是 pool 狀態的房務單
      並且 「阿強」查詢待接池時清單含「冷氣不冷」但不含「毛巾×2」

  Rule: 不存在的任務一律 404 not_found——跨租戶的任務視同不存在

    @error @boundary
    場景: 認領不存在的任務
      當 「小美」認領一個不存在的任務
      那麼請求應該失敗，回應為 404 且原因是 "not_found"

    @error @boundary
    場景: 對不存在的任務勾選 SOP 項目
      當 「小美」對不存在的任務勾選 SOP 項目
      那麼請求應該失敗，回應為 404 且原因是 "not_found"
