# language: zh-TW
@backbone
功能: 核心骨幹——冪等、事件真相、輸送帶與即時推送
  作為系統，我必須保證不掉單、不重複、歷史不滅、看板即時。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name   | login_id | password_hash | role         | dept         |
      | s-hk1 | 1         | 小美   | hk1      | hash-hk1      | housekeeping | housekeeping |
      | s-mt1 | 1         | 阿強   | mt1      | hash-mt1      | engineering  | engineering  |
      | s-mgr | 1         | 王主管 | mgr1     | hash-mgr1     | manager      |              |
    並且 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301 | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305 | 1         | 305     | 3     | standard_double | cleaned  | occupied  |

  Rule: 冪等寫入——重送效果與一次相同

    @boundary
    場景: 弱網重送完成不報錯不重做
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | doing | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19 |
      並且 「小美」在電梯間對任務 "t-301-1" 按「完成」且請求帶 Idempotency-Key "7f3a0d2e-5b1c-4f08-9a27-3c9d1e6b4a51"
      並且 第一次請求已成功、回應尚未送達
      當 客戶端以同一 Idempotency-Key 重送 3 次
      那麼 每次都收到與第一次相同的狀態碼與回應
      並且 任務只被完成一次、事件只寫一筆、推播只排一筆

  Rule: 狀態機守衛——條件式 UPDATE、取號同句

    @boundary
    場景: 守衛與取號原子完成
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | assignee_id | started_at           | done_at              | work_date  |
        | t-301-1 | 1         | cleaning | system | r-301   | housekeeping | done  | 退房清潔 | s-hk1       | 2026-10-19T09:00:00Z | 2026-10-19T09:30:00Z | 2026-10-19 |
      當 任務 "t-301-1" 由 "done" 轉 "inspected"
      那麼 該轉移以單一條件式 UPDATE 完成，重複再轉即更新 0 列、該請求應該失敗，回應為 409
      並且 事件 seq 取自同句 UPDATE 回傳的 event_seq，嚴格遞增且無洞

    @boundary
    場景: 兩個併發請求同秒認領同一張單恰有一次成功
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state | title  |
        | t-305-1 | 1         | amenity | desk   | r-305   | housekeeping | pool  | 毛巾×2 |
      當 兩個併發請求同秒認領任務 "t-305-1"
      那麼 恰有一次認領成功、另一次請求應該失敗，回應為 409 且原因是 "state_conflict"
      並且 任務停在 "accepted"、事件只有 created 與 claimed、輸的那次不佔 seq

    @boundary
    場景: 非法轉移一律 409 且不留事件
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state | title  |
        | t-305-1 | 1         | amenity | desk   | r-305   | housekeeping | pool  | 毛巾×2 |
      當 依序嘗試 待接池直接開工、跨部門認領、認領後住客收回 三種非法轉移
      那麼 三次請求應該都失敗，回應皆為 409 且原因都是 "state_conflict"
      並且 被擋的轉移不產生任何事件

    @happy
    場景: 全鏈歷程事件 seq 嚴格遞增無洞
      假設 系統中存在以下任務:
        | id      | tenant_id | type     | source | room_id | dept         | state | title    | work_date  |
        | t-301-2 | 1         | cleaning | desk   | r-301   | housekeeping | pool  | 退房清潔 | 2026-10-19 |
      當 任務 "t-301-2" 走完 認領、開工、完成、查房退回、重新開工、再完成、查房通過 的全鏈歷程
      那麼 事件 seq 為 1 到 8 嚴格遞增且無洞
      並且 created 快照之種類為 "cleaning"、來源為 "desk"、房號為 "301"、標題為 "退房清潔"，且不含訂單鍵
      並且 任務停在 "inspected"、redo_count 為 1、之後任何轉移的請求應該失敗，回應為 409

    @boundary
    場景: 排房起點建單同時發 created 與 assigned
      當 系統以排房起點直接建立一張指派給「小美」的打掃單
      那麼 事件依序為 created 與 assigned、event_seq 為 2
      並且 任務停在 "accepted" 且受派人是「小美」

  Rule: outbox→relay→Kafka——同交易落匣、同鍵串行

    @boundary
    場景: 同鍵兩筆訊息不會顛倒
      假設 系統中存在以下待送匣:
        | tenant_id | aggregate_key  | kind | payload          | idempotency_key | status  | attempts | next_retry_at |
        | 1         | task:t-queue-1 | push | {"note":"first"} | ob-q1-first     | pending | 1        | +1h           |
        | 1         | task:t-queue-1 | push | {"note":"second"}| ob-q1-second    | pending | 0        |               |
        | 1         | task:t-queue-2 | push | {"note":"other"} | ob-q2-other     | pending | 0        |               |
      當 relay 輪詢 outbox
      那麼 relay 不送出 "ob-q1-second" 那筆，直到同 aggregate_key 的 "ob-q1-first" 那筆成功
      並且 其他鍵的事件照常轉送、不被擋

    @boundary
    場景: 應用當掉事件不丟
      假設 系統中存在以下待送匣:
        | tenant_id | aggregate_key | kind       | payload                      | idempotency_key | status  | attempts |
        | 1         | task:t-305-1  | task_event | {"type":"claimed","seq":2}   | ob-evt-1        | pending | 0        |
      當 relay 下一輪輪詢
      那麼 這筆 pending 事件仍被轉送上 Kafka、不掉單

    @boundary
    場景: 租約／退避到期前，別的輪詢不會重送
      假設 系統中存在以下待送匣:
        | tenant_id | aggregate_key  | kind       | payload                     | idempotency_key | status  | attempts | next_retry_at |
        | 1         | task:t-lease-1 | task_event | {"type":"created","seq":1}  | ob-lease-1      | pending | 1        | +1h           |
      當 relay 下一輪輪詢
      那麼 待送匣 "ob-lease-1" 仍是 "pending"、attempts 仍是 1

    @boundary
    場景: relay 送出成功但標記前當掉，租約到期後重跑會重送、下游去重吸收
      假設 系統中存在以下待送匣:
        | tenant_id | aggregate_key  | kind       | payload                     | idempotency_key | status  | attempts | next_retry_at |
        | 1         | task:t-crash-1 | task_event | {"type":"created","seq":1}  | ob-crash-1      | pending | 1        | -1h           |
      並且 待送匣 "ob-crash-1" 其實已經送過一次 Kafka（模擬 relay 上一輪送出成功、標記前當機）
      當 relay 下一輪輪詢
      那麼 待送匣 "ob-crash-1" 最終被標記為 "sent"、attempts 為 2
      並且 "ob-crash-1" 這個鍵在 Kafka 上有 2 筆內容相同的訊息、重複交由下游去重吸收

    @boundary
    場景: 同鍵首筆標 failed 後，後面排隊的訊息不會插隊送出
      假設 系統中存在以下待送匣:
        | tenant_id | aggregate_key  | kind | payload          | idempotency_key | status  | attempts | next_retry_at |
        | 1         | task:t-queue-3 | push | {"note":"first"} | ob-q3-first     | failed  | 8        |               |
        | 1         | task:t-queue-3 | push | {"note":"second"}| ob-q3-second    | pending | 0        |               |
      當 relay 跑過一輪
      那麼 待送匣 "ob-q3-second" 仍是 "pending"、attempts 仍是 0

    @boundary
    場景: Kafka 下游不回寫 tasks
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state | title  |
        | t-305-1 | 1         | amenity | desk   | r-305   | housekeeping | pool  | 毛巾×2 |
      並且 任務 "t-305-1" 已走完 認領、開工、完成 且事件已全數上 Kafka
      當 Kafka 消費者處理完這些事件
      那麼 tasks 列未被任何消費者改動，狀態、event_seq、updated_at 均不變

    @happy
    場景: 任務事件依 seq 順序帶租戶編號上 Kafka
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | dept         | state | title  |
        | t-305-1 | 1         | amenity | desk   | r-305   | housekeeping | pool  | 毛巾×2 |
      並且 任務 "t-305-1" 已走完 認領、開工、完成 且事件已全數上 Kafka
      當 消費者從頭讀取 task-events topic
      那麼 該任務的四筆事件依 seq 1 到 4 依序到達、分區鍵為 task 加任務編號
      並且 每筆事件 envelope 都帶 tenant_id

  Rule: 事件流唯一真相——事件列不可改不可刪

    @error @boundary
    場景: 事件列不可改不可刪
      當 應用連線嘗試 UPDATE 或 DELETE task_events 的列
      那麼 操作被資料庫權限拒絕
