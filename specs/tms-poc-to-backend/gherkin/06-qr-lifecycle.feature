# language: zh-TW
@qr
功能: 房間 QR 簽發與輪替
  作為主管，我希望列印與輪替房間 QR，以便住客入口安全可控。（spec 故事 6）

  規則: FR-052（E-02）掃碼入口 IP 限流與失敗計數

    @error @boundary
    場景: 連續失敗觸發限流與告警
      假設 同一 IP 在 10 分鐘內已有 10 次掃碼失敗（門檻取租戶設定，預設 10/10 分鐘）
      當 該 IP 再次掃碼
      那麼 請求被擋並產生告警

  規則: FR-050 token 規格與一房一枚

    @error @boundary
    場景: 同房第二枚現行 token 被唯一約束擋下
      假設 房 305 已有一枚 status "active" 的 token
      當 任何路徑嘗試為房 305 插入第二枚 active token
      那麼 寫入被 partial unique (tenant_id, room_id) WHERE status='active' 拒絕

    @happy
    場景: 簽發的 token 不可預測
      當 為房 305 簽發 QR token
      那麼 token 為至少 128 bit 隨機（base62 約 22 字元）且不含房號資訊

  規則: FR-051（E-18）輪替連坐——整房住客身分全失效

    @boundary
    場景: 輪替前掃過的 session 也立即失效
      假設 住客已用房 305 的舊 token 掃碼取得 session
      當 王主管對房 305 執行「重產此房 QR」
      那麼 同一筆交易內：舊 token 標 "revoked"、新 token version +1 生效
      並且 該房所有未撤銷 guest session 都被寫上 revoked_at
      並且 該住客的下一個請求收到 401

    @boundary
    場景: 被撿走的舊貼紙從此無效
      假設 房 305 已完成輪替
      當 任何人以舊 token 掃碼
      那麼 回應語意為「請洽櫃檯」、不簽 session、不建單
