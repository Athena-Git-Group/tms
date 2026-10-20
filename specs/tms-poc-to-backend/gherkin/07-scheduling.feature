# language: zh-TW
@scheduling
功能: 班前自動排房、手動調整、重產發佈與人力試算
  作為主管，我希望系統班前排好清潔房、我可調整、重產時新舊對照後發佈，
  以便整天打掃有秩序。（spec 故事 7、11；計畫樣本溯源 spec §5.2：today×早班 published v2）

  背景:
    假設 租戶 1 今日早班已有 published 計畫（version 2）：301→小美(est 30)、305→阿芳(est 10)

  規則: E-08 調整帶版本——過期版本 409 並附最新內容

    @error @boundary
    場景: 兩位主管同時調整不互吃
      假設 王主管打開畫面時計畫 version 為 1，其後另一位主管已改成 version 2
      當 王主管帶 If-Match version 1 送出調整
      那麼 回應為 409 並附最新計畫內容
      並且 任何成功調整使 version +1 且寫事件 "plan_adjusted"

  規則: E-09 一房一天只有一筆未完成打掃單（資料庫兜底）

    @error @boundary
    場景: 重複開單在最底層被擋
      假設 房 301 今日已有 state "accepted" 的打掃單
      當 任何路徑嘗試再開房 301 今日的第二筆打掃單
      那麼 寫入被 partial unique (tenant_id, room_id, work_date) 拒絕

  規則: FR-060 班前自動排房——樓層×工時、一層一人、演算法可替換

    @happy
    場景: 以今日房態產生草稿
      假設 今日待掃清單含 3 樓與 5 樓的房、租戶標準工時「退房清 30 分、夜床 10 分」
      當 班前排房排程執行
      那麼 產生 status "draft" 的計畫（algo_version "v1"）
      並且 同一樓層的房分給同一人、直到其班次工時填滿
      並且 est_min 取 cleaning_time_stats 平均；樣本數 <20 的房型用標準工時

  規則: 發佈即指派（狀態機唯一不經池的起點）

    @happy
    場景: 發佈逐間開單
      假設 今日晚班存在 draft 計畫：305→小美(夜床 est 10)
      當 王主管發佈該計畫
      那麼 開出 type "cleaning"、source "system"、state "accepted"、work_date 今日 的任務
      並且 plan_rooms.task_id 回填對應任務、事件 "assigned" 寫入

  規則: 重產對帳——新舊並存、發佈切換同一交易三分支

    @boundary
    場景: 發佈新計畫的對帳三分支
      假設 現行 published 計畫含 301（未開始）、502（doing 中）、305（未開始）
      並且 重產的 draft 含 301（換負責人阿芳）與 306，不含 305 與 502
      當 王主管發佈新草稿
      那麼 同一筆交易內：新計畫轉 "published"、舊計畫標 "superseded"
      並且 301 的未開始單改掛新計畫、負責人換為阿芳（事件 "reassigned"）
      並且 305 的未開始單轉 "cancelled"（事件原因 "plan_superseded"）
      並且 502 做到一半的單不被自動更動、列入回應清單交王主管決定

    @boundary
    場景: 同班次只有一份已發佈
      假設 今日早班已有 published 計畫
      那麼 重產只會產生與之並存的 draft；直接插入第二份 published 被 partial unique 拒絕

  規則: FR-062 延後的單隔日自動回池

    @happy
    場景: deferred 隔日班前回池
      假設 一筆空房巡任務昨日被延後為 "deferred"
      當 今日班前排程執行
      那麼 該任務回到 "pool" 且事件 "returned_to_pool" 的 payload 註明回池原因

  規則: FR-061 班表與計畫查詢

    @happy
    場景: 房務看今日負責房
      當 「小美」查詢今日班表
      那麼 回傳 301（est 30 分、route_seq 1）

  規則: FR-100 人力缺口試算——只試算、怎麼降由主管決定

    @happy
    場景: 缺口計算
      假設 今日待掃合計 260 分鐘、租戶班次容量 240 分鐘
      當 王主管查詢人力缺口
      那麼 回傳缺口 20 分鐘與可延後的空房巡清單
      並且 系統不自動延後任何任務
