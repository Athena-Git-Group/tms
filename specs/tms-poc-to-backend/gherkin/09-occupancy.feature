# language: zh-TW
@occupancy
功能: 房態來源——PMS 同步與 Excel 匯入
  作為主管，我希望 PMS 接不上時匯今日房態照常營運，
  以便斷線不停業。（spec 故事 9；訂單樣本溯源 spec §5.2 reservations_cache）

  規則: FR-081（E-10）同房住期重疊的有效訂單只能有一筆

    @error @boundary
    場景: 重疊訂單被區間互斥約束拒絕
      假設 房 305 已有有效訂單「PMS-88321」（10-18 至 10-20）
      當 任何來源寫入房 305 住期 10-19 至 10-21 的第二筆有效訂單
      那麼 寫入被 EXCLUDE 約束拒絕

  規則: 匯入逐列驗證——房號存在才收、留痕

    @error @boundary
    場景: 不存在的房號整列退回
      當 匯入檔含房號 "999" 的入住列
      那麼 該列報錯退回、不寫入

    @happy
    場景: 匯入今日房態補上當前訂單
      當 王主管匯入含「301, checkin, 2026-10-19, 2026-10-19」的今日房態檔（欄位依假設 A9）
      那麼 建立 reservation_ref "excel:2026-10-19:301"、source "excel" 的訂單列
      並且 寫入留痕事件 "checkout_imported"
      並且 當日新入住的住客掃房 301 可正常取得 session（SC-008）

  規則: FR-080 PMS adapter 同步與準備工單

    @happy
    場景: VIP 訂單自動開準備工單
      當 模擬 PMS adapter 同步進房 305 的訂單「PMS-88321」（attrs 含 vip:true）
      那麼 系統自動開出 type "amenity"、source "system" 的準備工單
      並且 reservations_cache 的該列 source 為 "pms"

  規則: PMS 恢復後對帳——同住宿只留一筆有效

    @boundary
    場景: 匯入列被真訂單取代
      假設 房 301 存在 excel 匯入列與其後同步進來、住期重疊的 PMS 真訂單
      當 PMS 同步對帳執行
      那麼 excel 列被標上 superseded_at
      並且 掃碼、排房、準備工單等「當前訂單」查詢不再回傳該列
