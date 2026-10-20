# language: zh-TW
@staff
功能: 員工在部門待接池認領、開始、完成
  作為房務員／工務，我希望在部門待接池認領需求並用兩段回報，
  以便不靠對講機交辦。（spec 故事 2；人員溯源 spec §5.2：小美 hk1 房務、阿強 mt1 工務）

  背景:
    假設 租戶 1 有員工
      | name  | login_id | role         | dept         |
      | 小美  | hk1      | housekeeping | housekeeping |
      | 阿強  | mt1      | engineering  | engineering  |
    並且 房 305 的「毛巾×2」任務在房務待接池（type amenity、state pool、dept housekeeping）

  規則: FR-011（E-06）認領守衛——限同部門、限 pool、同秒只有一人成功

    @error @boundary
    場景: 跨部門不能認領
      當 工務「阿強」認領「毛巾×2」
      那麼 回應為 409 且錯誤碼為 "state_conflict"

    @error @boundary
    場景: 已被認領的單再認領被擋
      假設 「毛巾×2」已被「小美」認領
      當 另一位房務認領同一單
      那麼 回應為 409 且提示「這單已被小美認領」

    @boundary
    場景: 兩人同一秒搶單恰一人成功
      當 兩位房務同時對「毛巾×2」送出認領（條件式 UPDATE 守衛）
      那麼 恰有一人成功、另一人收到 409
      並且 任務 event_seq 僅遞增一次

    @happy
    場景: 同部門認領成功
      當 房務「小美」認領「毛巾×2」
      那麼 任務 state 由 "pool" 轉為 "accepted"、assignee 為 hk1、事件 "claimed" 寫入

  規則: FR-012（E-16/GR-001）兩段回報——非法轉移一律 409

    @error @boundary
    場景大綱: 非法狀態轉移
      假設 任務 state 為 <目前>
      當 對任務執行 <動作>
      那麼 回應為 409 且錯誤碼為 "state_conflict"

      例子:
        | 目前     | 動作     |
        | accepted | complete |
        | pool     | start    |
        | done     | start    |

    @happy
    場景: 開始與完成記錄實際工時
      假設 「小美」已認領 301 的退房清任務（est_min 30）
      當 她按「開始」再按「完成」
      那麼 state 依序為 "doing"、"done"
      並且 started_at 與 done_at 被記錄（實際分鐘＝兩者相減，不另外填）

  規則: FR-013 SOP 逐項打勾——跳過不擋流程但留痕

    @happy
    場景: 跳過備品項仍可完成
      假設 「小美」的 301 打掃單在 doing、SOP 項目為 床鋪/浴室/備品/QR 貼紙
      當 她勾選「床鋪」「浴室」「QR 貼紙」並跳過「備品」再按完成
      那麼 完成成功
      並且 每次勾選與跳過都寫入事件 "sop_checked"（誰、幾點、哪一項）

  規則: FR-010 待接池只回本部門

    @happy
    場景: 房務看不到工務的單
      假設 工務池有一筆「冷氣不冷」修繕單
      當 「小美」查詢待接池
      那麼 清單含「毛巾×2」但不含「冷氣不冷」

  規則: FR-014 員工即時更新——本部門池與本人任務

    @happy
    場景: 新單 2 秒內出現在部門看板
      假設 「小美」開啟員工事件串流
      當 住客對房 305 送出新的備品需求
      那麼 「小美」在 2 秒內收到待接池變更通知（SC-002）
