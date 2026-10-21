# language: zh-TW
@knowledge @settings
功能: 員工問一下與租戶設定
  作為房務與主管，我希望查得到這間房的過往與 SOP、店內規則都可自店調整。（spec 故事 15、16）

  規則: FR-140 問一下（MVP 降級版）——只讀事件流投影、LLM 留椿

    @happy
    場景: 查房間過往記錄
      假設 房 305 的 room_memory 含「上月修過冷氣」（kind repair）
      當 「阿強」以房號 305 問一下
      那麼 回覆含該筆房間記憶摘要與 SOP 關鍵字命中項
      並且 本次查詢不呼叫任何 LLM（假設 A6）

  規則: FR-150 租戶設定——封閉鍵集合、變更可追

    @error @boundary
    場景: 未知設定鍵被拒
      當 王主管寫入鍵 "unknown_key"
      那麼 回應為 422（鍵集合封閉：standard_cleaning_minutes、shift_capacity、inspection_items、sop_items、guest_info_pages、request_menu、minutes_per_credit…）

    @happy
    場景: 改選單立即影響住客端
      當 王主管把 request_menu 中的 "extra_bed" 停用
      那麼 變更記錄 updated_by 與 updated_at
      並且 住客掃碼取得的選單不再含「加床」

    @happy
    場景: SOP 設定門面寫進結構表
      當 王主管更新鍵 "inspection_items"
      那麼 sop_items 表中 applies_to "inspection" 的列同步更新（資料表為儲存真相）
