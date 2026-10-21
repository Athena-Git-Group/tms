# language: zh-TW
@owner
功能: 老闆儀表板與「出事才響」
  作為老闆，我希望打開就看到當日數字、只在出事時收推播。（spec 故事 10）

  規則: E-15（SC-007）取消單不進任何統計

    @boundary
    場景大綱: 各種取消原因都不影響成績
      假設 今日有一筆任務以原因 <原因> 被取消
      當 worker 刷新 stats_daily
      那麼 該單不計入完成數、平均工時、評分與任何卡片

      例子:
        | 原因            |
        | guest_withdrew  |
        | manager_void    |
        | plan_superseded |

  規則: FR-090 推播來源 v1 只有兩個——低分評分與主管手動上報

    @happy
    場景: 低分評分觸發老闆推播
      假設 租戶設定低分門檻為 2 星
      當 住客對完成的任務評 2 星
      那麼 outbox 排入一筆 kind "push" 的老闆通報
      並且 推播消費者處理該筆（MVP 落日誌＋資料列，不接真 APNS/FCM——假設 A1）

    @happy
    場景: 主管手動上報
      當 王主管送出上報訊息
      那麼 outbox 排入一筆 kind "push" 的老闆通報（假設 A11）

    @boundary
    場景: SLA 逾時不觸發任何推播（v1 不做升級鏈）
      假設 一筆備品單在待接池停了 120 分鐘
      那麼 系統不產生任何升級或推播（sla_min/esc_level 僅保留欄位）

  規則: FR-091 儀表板只讀預算值、投影可重建

    @happy
    場景: 開畫面不現算
      假設 stats_daily 今日列為 sellable 42、avg_response_min 6、rating_avg 4.6、inspection_returns 2
      當 老闆讀取儀表板
      那麼 回傳四張卡片與一行摘要，值與 stats_daily 完全一致

    @待釐清
    場景: 回應速度的計算定義
      # 待釐清: avg_response_min 的起訖點未定義——「回應」是建單到認領、還是建單到完成？
      # 待釐清: sellable 的計算基準未定義——是 hk_state=inspected 的房數，還是再扣掉 occupied？
      假設 需要從事件流計算 avg_response_min 與 sellable
      那麼 計算公式待 PM 拍板後由投影 worker 實作
