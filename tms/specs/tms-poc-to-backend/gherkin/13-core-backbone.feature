# language: zh-TW
@backbone
功能: 核心骨幹——冪等、事件真相、輸送帶與即時推送
  作為系統，我必須保證不掉單、不重複、歷史不滅、看板即時。（spec 全域需求 GR-001～GR-006）

  規則: GR-002（E-11／SC-003）冪等寫入——重送效果與一次相同

    @boundary
    場景: 弱網重送完成不報錯不重做
      假設 「小美」在電梯間按「完成」且請求帶 Idempotency-Key "op-7f3a"
      並且 第一次請求已成功、回應尚未送達
      當 客戶端以同一 Idempotency-Key 重送 3 次
      那麼 每次都收到與第一次相同的狀態碼與回應（processed_ops 回放）
      並且 任務只被完成一次、事件只寫一筆、推播只排一筆

  規則: GR-001 狀態機守衛——條件式 UPDATE、取號同句

    @boundary
    場景: 守衛與取號原子完成
      當 任務由 "done" 轉 "inspected"
      那麼 該轉移以單一條件式 UPDATE 完成（WHERE state='done'），更新 0 列即回 409
      並且 事件 seq 取自同句 UPDATE 回傳的 event_seq（禁止 SELECT max(seq)+1）

  規則: GR-003（E-14）outbox→relay→Kafka——同交易落匣、同鍵串行

    @boundary
    場景: 同房兩筆回寫不會顛倒
      假設 房 305 的「房況回 dirty」回寫送失敗、正在排隊重試
      並且 其後產生同房的「房況改 inspected」回寫
      當 relay 輪詢 outbox
      那麼 relay 不送出 inspected 那筆，直到 dirty 那筆成功（同 aggregate_key room:1:<305 的房 uuid> 串行）
      並且 其他鍵的事件照常轉送、不被擋

    @boundary
    場景: 應用當掉事件不丟
      假設 任務狀態變更與 outbox 在同一筆交易 commit
      當 應用在 commit 後、relay 轉送前當掉重啟
      那麼 outbox 中 pending 的事件仍在、由 relay 繼續轉送（不掉單）

    @boundary
    場景: Kafka 下游不回寫 tasks
      當 任何 Kafka 消費者處理事件
      那麼 它只更新衍生投影或發送副作用，不得 UPDATE tasks

  規則: GR-004（E-13／SC-004／SC-006）事件流唯一真相、投影可重建

    @error @boundary
    場景: 事件列不可改不可刪
      當 任何連線嘗試 UPDATE 或 DELETE task_events 的列
      那麼 操作被資料庫權限拒絕

    @happy
    場景: 投影清空重放一致
      假設 一批任務已走完 建立→認領→開始→完成→查房退回→重做→通過 的歷程
      當 在影子庫清空 tasks 並由 task_events 重放
      那麼 重建結果與線上 tasks 逐列一致（CI 自動驗證）

  規則: GR-005（SC-002）SSE——跳號重拉、心跳、降級要明講

    @boundary
    場景: 跳號偵測
      假設 客戶端上一則收到 stream_pos 41
      當 下一則收到 stream_pos 43
      那麼 客戶端重拉一次快照補上漏掉的一則

    @boundary
    場景: 靜默停擺要降級
      假設 推送模組的 Kafka 消費延遲超過門檻
      那麼 連線上收到 "degraded" 事件、客戶端切為每 5 秒輪詢並標示「資料可能延遲」
      並且 推送模組每 15 秒自發心跳（不經 Kafka），心跳停了客戶端即知連線已死

  規則: GR-006 去識別化——刪的是可識別性，不是歷史

    @boundary
    場景: 保留期限到期的處理
      假設 租戶保留期限為 180 天且「小美」已離職
      當 去識別化排程執行
      那麼 到期事件列的 payload 個資欄位被抹除、其帳號列換成不可回推的代號
      並且 事件列本身不被刪除、統計形狀（時間、分鐘、跳過項）完整保留
      並且 這次執行本身也寫入一筆事件（誰、何時、處理了哪些列）
