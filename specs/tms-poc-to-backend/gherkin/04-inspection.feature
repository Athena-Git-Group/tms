# language: zh-TW
@inspection
功能: 主管查房與退回
  作為主管，我希望逐項判定查房、任一不過整間退回、全過才回寫 PMS，
  以便守住品質關卡。（spec 故事 4；查房項目溯源 POC 承接的租戶設定：床鋪/浴室/備品/QR 貼紙）

  背景:
    假設 租戶 1 的查房項目為 床鋪、浴室、備品、QR 貼紙
    並且 「小美」清完的 301 退房清任務 state 為 "done"（已勾 SOP、started_at 與 done_at 已記）

  規則: FR-032 自清自查必擋（伺服器端強制）

    @error @boundary
    場景: 清房者查自己的房被拒
      當 以「小美」的身分對 301 的打掃單送出查房
      那麼 回應為 403

  規則: FR-030 查房輸入守衛——項目全集、僅 done 可查

    @error @boundary
    場景: 缺項不收
      當 王主管只送出 床鋪、浴室 兩項的判定
      那麼 回應為 422（items 須涵蓋租戶查房項目全集）

    @error @boundary
    場景: 非 done 狀態不可查房
      假設 任務 state 為 "doing"
      當 王主管送出查房
      那麼 回應為 409

  規則: FR-031 兩分支——全過可賣、任一不過整間退回

    @boundary
    場景: 任一項不過整間退回並清空重來
      假設 任務退回前 redo_count 為 0
      當 王主管判定 床鋪:不過、其餘:過
      那麼 任務由 "done" 退回 "accepted"、redo_count 為 1
      並且 started_at、done_at 與 SOP 勾記被清空、房 301 的 hk_state 回 "dirty"
      並且 事件 "inspection_failed" 帶不過項目「床鋪」
      但是 第一輪「誰在幾點勾過什麼」仍可由事件流完整回溯（SC-004）

    @boundary
    場景: 退回時原清房者已下班則連動退池
      假設 照租戶班次設定「小美」已下班
      當 王主管對她名下 done 的打掃單判定 浴室:不過
      那麼 同一筆交易寫入兩筆事件："inspection_failed" 與 "returned_to_pool"
      並且 任務最終 state 為 "pool"、優先推播同樓層

    @happy
    場景: 全過即可賣並排 PMS 回寫
      當 王主管判定四項全過
      那麼 任務轉為 "inspected"、房 301 的 hk_state 為 "inspected"
      並且 同一筆交易在 outbox 排入一筆 kind "pms_status"（aggregate_key 為 room:1:<301 的房 uuid>）
