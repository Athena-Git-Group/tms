# language: zh-TW
@qr
功能: 房間 QR 簽發與輪替
  作為主管，我希望列印與輪替房間 QR，以便住客入口安全可控。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name   | login_id | password_hash | role    | dept |
      | s-mgr | 1         | 王主管 | mgr1     | hash-mgr1     | manager |      |
    並且 系統中存在以下房間:
      | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301    | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-502    | 1         | 502     | 5     | suite           | dirty    | vacant    |
      | r-t2-101 | 2         | 101     | 1     | standard_double | dirty    | occupied  |

  Rule: token 規格與一房一枚

    @happy
    場景: 簽發的 token 不可預測、再列印不重簽
      假設 房 301 尚無任何 QR token
      當 王主管列印房 301 的 QR
      那麼 token 為 base62 22 字元、隨機強度至少 128 bit，且不含房號、首簽 version 為 1
      並且 再列印回同一枚 token
      並且 列印查無房號 "999" 的請求應該失敗，回應為 404
      並且 列印別店的房號 "101" 的請求應該失敗，回應為 404

    @error @boundary
    場景: 同房第二枚現行 token 被唯一約束擋下
      假設 系統中存在以下房間QR:
        | id    | tenant_id | room_id | token      | version | status |
        | q-502 | 1         | r-502   | tok-502-v1 | 1       | active |
      當 直接為房 502 插入第二枚 active token
      那麼 寫入被 partial unique 約束以 23505 拒絕

  Rule: 輪替連坐——整房住客身分全失效

    @boundary
    場景: 輪替前掃過的 session 也立即失效
      假設 系統中存在以下房間QR:
        | id    | tenant_id | room_id | token      | version | status |
        | q-301 | 1         | r-301   | tok-301-v1 | 1       | active |
      並且 系統中存在以下訂單:
        | id      | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source |
        | res-301 | 1         | PMS-88111       | r-301   | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      並且 系統中存在以下住客session:
        | id     | tenant_id | session_token | room_id | reservation_id | expires_at           | revoked_at |
        | gs-301 | 1         | sess-301      | r-301   | res-301        | 2026-10-20T12:00:00Z |            |
      當 王主管對房 301 執行「重產此房 QR」
      那麼 舊 token 標 "revoked"、新 token version +1 生效
      並且 該房所有未撤銷 guest session 都被寫上 revoked_at、回應的 revokedSessions 為 1
      並且 該住客的下一個請求應該失敗，回應為 401

    @boundary
    場景: 被撿走的舊貼紙從此無效
      假設 系統中存在以下房間QR:
        | id        | tenant_id | room_id | token      | version | status  | revoked_at           |
        | q-301-old | 1         | r-301   | tok-301-v1 | 1       | revoked | 2026-10-19T08:00:00Z |
        | q-301-new | 1         | r-301   | tok-301-v2 | 2       | active  |                      |
      當 任何人以 token "tok-301-v1" 掃碼
      那麼請求應該失敗，回應為 409 且原因是 "room_vacant"
      並且 回應訊息含「請洽櫃檯」
      並且 系統未簽發任何 guest session 也未建立任何任務
