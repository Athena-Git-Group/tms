# language: zh-TW
@sse
功能: SSE 即時推送——住客與員工的鈴
  作為住客與員工，我希望任務一有變化畫面就自動更新，
  以便不用重新整理也不漏單。

  背景:
    假設 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301 | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305 | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
    並且 系統中存在以下訂單:
      | id      | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source |
      | res-305 | 1         | PMS-88321       | r-305   | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      | res-301 | 1         | PMS-88322       | r-301   | 2026-10-18T16:00:00Z | 2026-10-21T12:00:00Z | pms    |
    並且 系統中存在以下住客session:
      | id   | tenant_id | session_token | room_id | reservation_id | expires_at           | revoked_at |
      | gs-a | 1         | sess-a        | r-305   | res-305        | 2026-10-20T12:00:00Z |            |
      | gs-b | 1         | sess-b        | r-301   | res-301        | 2026-10-21T12:00:00Z |            |
    並且 系統中存在以下員工:
      | name | id | tenant_id | login_id | password_hash | role | dept |
      | 小美 | s-hk1 | 1 | hk1 | hash-hk1 | housekeeping | housekeeping |
      | 阿強 | s-mt1 | 1 | mt1 | hash-mt1 | engineering | engineering |

  Rule: 連線建立先送 init 再送 resync，提醒客戶端重拉快照

    @boundary
    場景: 住客連線依序收到 init、resync，resync 指向住客首頁端點
      假設 住客 A 以 session "sess-a" 開啟住客事件串流
      那麼 住客 A 的連線依序收到 "init"、"resync"
      並且 住客 A 的 resync 的 refetch 指向 "/guest/home"

    @boundary
    場景: 員工連線依序收到 init、resync，resync 指向待接池端點
      假設 房務「小美」開啟員工事件串流
      那麼 「小美」的連線依序收到 "init"、"resync"
      並且 「小美」的 resync 的 refetch 指向 "/staff/pool"

  Rule: 住客串流只推本 session 綁定訂單的任務變化

    @happy @boundary
    場景: 兩位住客各收各的鈴、互相零外洩
      假設 住客 A 以 session "sess-a" 開啟住客事件串流
      並且 住客 B 以 session "sess-b" 開啟住客事件串流
      當 訂單 "res-305" 與訂單 "res-301" 各建立一張任務
      那麼 住客 A 收到自己任務 seq 1 的 "created"
      並且 住客 B 收到自己任務的 "created"
      並且 彼此的串流不出現對方任務的任何事件

  Rule: 員工串流推本部門待接池與本人任務

    @happy
    場景: 新單即時出現在部門看板
      假設 房務「小美」開啟員工事件串流
      當 房務池建立一張新單並由「小美」認領、開工
      那麼 「小美」依序收到該單的 "created"、"claimed"、"started"

    @boundary
    場景: 跨部門零外洩
      假設 房務「小美」開啟員工事件串流
      並且 工務「阿強」開啟員工事件串流
      並且 房務池建立一張新單並由「小美」認領、開工
      當 工務池建立一張新單
      那麼 「阿強」收到工務新單的 "created"
      並且 兩人的串流互不出現對方部門的任務事件

  Rule: 心跳、連線層流水號與降級

    # 心跳由連線層自發，不經 Kafka，所以不佔 stream_pos。
    @boundary
    場景: 心跳自發、不佔流水號
      假設 房務「小美」開啟員工事件串流
      那麼 連續收到兩次 "heartbeat" 且 stream_pos 維持 0

    @boundary
    場景: stream_pos 逐一遞增
      假設 住客 A 以 session "sess-a" 開啟住客事件串流
      當 訂單 "res-305" 上的一張任務走完 認領、開工、完成 全鏈
      那麼 住客 A 依序收到 seq 1 到 4 的四筆事件且 stream_pos 逐一遞增

    @boundary
    場景: 重複事件被吸收
      假設 住客 A 以 session "sess-a" 開啟住客事件串流
      並且 訂單 "res-305" 上的一張任務走完 認領、開工、完成 全鏈
      並且 住客 A 依序收到 seq 1 到 4 的四筆事件且 stream_pos 逐一遞增
      # 重複事件模擬 relay「保證送到、但可能送超過一次」（at-least-once）的重送：
      # relay 送出後才標記已送，若中途當機，重啟後會再送同一筆。
      當 同 (task_id, seq) 的事件被重複直送 Kafka 兩筆
      那麼 該任務推給住客 A 的事件維持 4 筆、零重推

    @boundary
    場景: Kafka 消費延遲超門檻推 degraded
      假設 房務「小美」開啟員工事件串流
      當 一筆 record 時間戳落後 60 秒的事件進入 task-events topic
      那麼 串流收到 "degraded" 事件、reason 為 "kafka_consumer_lag"
