# language: zh-TW
@guest
功能: 住客掃碼提需求、看進度、評分
  作為住客，我希望掃房間 QR 就能提需求、看進度、完成後評分，
  以便不用打電話也不用安裝 App。

  背景:
    假設 系統中存在以下房間:
      | id    | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301 | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305 | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
      | r-502 | 1         | 502     | 5     | suite           | dirty    | vacant    |
    並且 系統中存在以下訂單:
      | id      | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source | attrs        |
      | res-305 | 1         | PMS-88321       | r-305   | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    | {"vip":true} |
    並且 系統中存在以下房間QR:
      | id    | tenant_id | room_id | token      | version | status |
      | q-305 | 1         | r-305   | tok-305-v2 | 2       | active |
      | q-502 | 1         | r-502   | tok-502-v1 | 1       | active |
    並且 系統中存在以下住客session:
      | id     | tenant_id | session_token | room_id | reservation_id | expires_at           | revoked_at |
      | gs-305 | 1         | sess-305      | r-305   | res-305        | 2026-10-20T12:00:00Z |            |

  Rule: 掃碼簽發——空房與作廢 token 擋在門外

    @error @boundary
    場景: 空房掃碼不簽 session、不建單
      假設 房 502 查無當前有效訂單
      當 以 token "tok-502-v1" 呼叫掃碼
      那麼請求應該失敗，回應為 409 且原因是 "room_vacant"
      並且 系統未簽發任何 guest session 也未建立任何任務

    # 「token 不存在」與「已作廢」必須同碼同訊息——可區分的錯誤＝枚舉提示
    @error @boundary
    場景: 已作廢 token 掃碼被擋、與不存在的 token 不可區分
      假設 系統中存在以下房間QR:
        | id       | tenant_id | room_id | token      | version | status  | revoked_at           |
        | q-305-v1 | 1         | r-305   | tok-305-v1 | 1       | revoked | 2026-10-18T10:00:00Z |
      當 以 token "tok-305-v1" 呼叫掃碼
      那麼請求應該失敗，回應為 409 且原因是 "room_vacant"
      並且 回應訊息含「請洽櫃檯」
      並且 以不存在的 token 掃碼得到同一個錯誤碼

    @happy
    場景: 在住房掃碼換 session 並取得資訊頁
      當 以 token "tok-305-v2" 呼叫掃碼
      那麼 簽發的 guest session 的資料應為:
        | room_id | reservation_id | expires_at           |
        | r-305   | res-305        | 2026-10-20T12:00:00Z |
      並且 回應含資訊頁與需求選單

    # 住客請求沒有登入身分，只能靠 QR token 判斷是哪個房間。
    @happy
    場景: 跨租戶解析——別店的 QR 一樣掃得開
      假設 系統中存在以下房間:
        | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
        | r-t2-101 | 2         | 101     | 1     | standard_double | cleaned  | occupied  |
      並且 系統中存在以下訂單:
        | id         | tenant_id | reservation_ref | room_id  | checkin_at           | checkout_at          | source |
        | res-t2-101 | 2         | PMS-20077       | r-t2-101 | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      並且 系統中存在以下房間QR:
        | id      | tenant_id | room_id  | token         | version | status |
        | q-t2-101 | 2        | r-t2-101 | tok-t2-101-v1 | 1       | active |
      當 以 token "tok-t2-101-v1" 呼叫掃碼
      那麼 簽發的 session token 以租戶 2 為前綴

  Rule: 住客 session 驗證——未過期且未撤銷缺一不可

    @error @boundary
    場景大綱: 無效 session 一律 401
      假設 系統中存在以下住客session:
        | id   | tenant_id | session_token   | room_id | reservation_id | expires_at   | revoked_at   |
        | <id> | 1         | <session_token> | r-305   | res-305        | <expires_at> | <revoked_at> |
      當 以 session "<session_token>" 查詢本次住宿的需求
      那麼請求應該失敗，回應為 401

      例子:
        | id         | session_token | expires_at           | revoked_at           |
        | gs-revoked | sess-revoked  | 2026-10-20T12:00:00Z | 2026-10-19T08:00:00Z |
        | gs-expired | sess-expired  | 2026-10-18T12:00:00Z |                      |

  Rule: 資訊頁前台直接提供、不進任務流

    @happy
    場景: 重新整理資訊頁不產生任務
      當 以 session "sess-305" 讀取資訊頁
      那麼 回應內容來自租戶設定 guest_info_pages
      並且 任務總數不變

  Rule: 建單分流——選單直入部門池、自由文字過規則引擎

    # 「房間有怪聲」不屬於任何部門規則，規則引擎回 0 個部門，需要櫃檯人為判斷。
    @boundary
    場景: 自由文字猜不準時停在待判讀
      當 住客以 session "sess-305" 用自由文字「房間有怪聲」送出需求
      那麼 建立任務 state 為 "triage" 且 dept 為 "frontdesk"

    # 「冷氣好吵」同時命中 r-aircon（工務）與 r-noise（櫃檯）＝部門歧義，不猜
    @boundary
    場景: 自由文字命中多個部門時不猜、停在待判讀
      當 住客以 session "sess-305" 用自由文字「冷氣好吵」送出需求
      那麼 建立任務 state 為 "triage" 且 dept 為 "frontdesk"

    @happy
    場景大綱: 建單路徑
      當 住客以 session "sess-305" 送出 <輸入>
      那麼 建立任務 type 為 <type>、dept 為 <dept>、state 為 <state>
      並且 事件 "created" 的快照之種類為 <type>、來源為 "guest"、房號為 "305"、訂單為 "PMS-88321"、標題為 "<title>"

      # 自由文字「冷氣不冷」命中工務的規則，直接入工務待認領。
      例子:
        | 輸入                 | type    | dept         | state | title    |
        | 選單項「加毛巾」     | amenity | housekeeping | pool  | 加毛巾   |
        | 自由文字「冷氣不冷」 | repair  | engineering  | pool  | 冷氣不冷 |

    @happy
    場景: 規則引擎命中留痕可解釋
      當 住客以 session "sess-305" 用自由文字「冷氣不冷」送出需求
      那麼 事件 payload 的 note 記錄命中的 rule_id "r-aircon" 與原文、語言

  Rule: 同房限流——10 分鐘內 3 筆，第 4 筆被拒

    @error @boundary
    場景: 第 4 筆被限流
      假設 住客已以 session "sess-305" 在 10 分鐘內成功建立 3 筆需求
      當 再以 session "sess-305" 送出第 4 筆需求
      那麼請求應該失敗，回應為 429 且原因是 "rate_limited"
      並且 第 3 筆需求仍為成功建立

  Rule: 進度清單以 session 綁的訂單過濾、不按房號撈

    @boundary
    場景: 新住客看不到前一位住客的需求
      假設 系統中存在以下訂單:
        | id          | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source |
        | res-305-old | 1         | PMS-77001       | r-305   | 2026-10-10T15:00:00Z | 2026-10-12T12:00:00Z | pms    |
      並且 系統中存在以下任務:
        | id        | tenant_id | type    | source | room_id | reservation_id | dept         | state | title      |
        | t-305-old | 1         | amenity | guest  | r-305   | res-305-old    | housekeeping | done  | 舊客毛巾×2 |
      並且 新住客以 session "sess-305" 送出需求 選單項「礦泉水」
      當 新住客以 session "sess-305" 查詢本次住宿的需求
      那麼 清單含自己的「礦泉水」但不含前一筆訂單的任何需求

  Rule: 收回只限還沒人認領

    @error @boundary
    場景: 已被認領的需求不能收回
      假設 系統中存在以下員工:
        | id    | tenant_id | name | login_id | password_hash | role         | dept         |
        | s-hk1 | 1         | 小美 | hk1      | hash-hk1      | housekeeping | housekeeping |
      並且 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | reservation_id | dept         | state    | title  | assignee_id |
        | t-305-1 | 1         | amenity | guest  | r-305   | res-305        | housekeeping | accepted | 加毛巾 | s-hk1       |
      當 住客以 session "sess-305" 對任務 "t-305-1" 呼叫收回
      那麼請求應該失敗，回應為 409 且原因是 "state_conflict"

    @happy
    場景大綱: 未認領可收回且不進統計
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | reservation_id | dept         | state   | title  |
        | t-305-2 | 1         | amenity | guest  | r-305   | res-305        | housekeeping | <state> | 加毛巾 |
      當 住客以 session "sess-305" 對任務 "t-305-2" 呼叫收回
      那麼 任務 "t-305-2" 的資料應為:
        | state     |
        | cancelled |
      並且 事件 "cancelled" 的原因為 "guest_withdrew"

      例子:
        | state  |
        | triage |
        | pool   |

  Rule: 評分 1–5、僅完成後、一次性

    @error @boundary
    場景大綱: 非法評分被拒
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | reservation_id | dept         | state   | title  | rating   |
        | t-305-3 | 1         | amenity | guest  | r-305   | res-305        | housekeeping | <state> | 加毛巾 | <rating> |
      當 住客以 session "sess-305" 對任務 "t-305-3" 評 <分數> 星
      那麼請求應該失敗，回應為 <狀態碼> 且原因是 "<原因>"

      例子:
        | state | rating | 分數 | 狀態碼 | 原因              |
        | done  |        | 0    | 422    | validation_failed |
        | done  |        | 6    | 422    | validation_failed |
        | doing |        | 4    | 409    | state_conflict    |
        | done  | 4      | 5    | 409    | state_conflict    |

    @happy
    場景大綱: 合法評分成功
      假設 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | reservation_id | dept         | state   | title  | rating |
        | t-305-4 | 1         | amenity | guest  | r-305   | res-305        | housekeeping | <state> | 加毛巾 |        |
      當 住客以 session "sess-305" 對任務 "t-305-4" 評 <分數> 星
      那麼 回應的任務 rating 為 <分數> 且事件 "rated" 帶分數

      例子:
        | state     | 分數 |
        | done      | 1    |
        | inspected | 5    |

  Rule: 掃碼入口 IP 限流與失敗計數

    # 第 11 發連「有效 token」都要被擋：限流看的是行為不是這一發的對錯
    # 失敗門檻取自租戶設定，預設為 10 分鐘內 10 次。
    # 限流按單一 IP 計數，不做全域熔斷。
    @error @boundary @scan-alert
    場景: 連續失敗觸發限流與告警
      假設 同一 IP 在 10 分鐘內已有 10 次掃碼失敗
      當 該 IP 以 token "tok-305-v2" 再次掃碼
      那麼請求應該失敗，回應為 429 且原因是 "rate_limited"
      並且 產生含該 IP 的掃碼限流告警，以 WARN 日誌記錄
      並且 別的 IP 以 token "tok-305-v2" 掃碼仍成功

    # 門檻是租戶可調的，不是寫死 10
    @boundary
    場景: 失敗門檻取自租戶設定
      假設 系統中存在以下租戶設定:
        | tenant_id | key                 | value |
        | 1         | scan_fail_threshold | 3     |
      並且 同一 IP 已有 3 次掃碼失敗
      當 該 IP 再以不存在的 token 掃碼
      那麼請求應該失敗，回應為 429 且原因是 "rate_limited"

  Rule: 櫃檯替住客開 session——飯店取自登入、權杖由伺服器產生，兩種帶法都先看權杖前綴的飯店

    # 權杖格式是「飯店編號.隨機字串」；伺服器先從前綴取出飯店，只在那家飯店的資料裡查一次。
    @happy
    場景大綱: 櫃檯或主管替住客開 session，拿到的權杖用兩種帶法都能建單
      假設 系統中存在以下員工:
        | id    | tenant_id | name   | login_id | password_hash | role         | dept      |
        | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk    | frontdesk |
        | s-mgr | 1         | 王主管 | mgr1     | hash-mgr1     | manager      |           |
        | s-dm1 | 1         | 陳值班 | dm1      | hash-dm1      | duty_manager |           |
      當 "<員工>" 替房 "r-305" 開住客 session "s-new"
      那麼 回應為 200
      並且 回應的住客權杖以 "1." 開頭
      當 住客以 <帶法> 帶 session "s-new" 送出選單項「加毛巾」
      那麼 建立任務 type 為 amenity、dept 為 housekeeping、state 為 pool
      並且 事件 "created" 的快照之種類為 amenity、來源為 "guest"、房號為 "305"、訂單為 "PMS-88321"、標題為 "加毛巾"

      例子:
        | 員工   | 帶法            |
        | 林櫃檯 | X-Guest-Session |
        | 林櫃檯 | Authorization   |
        | 王主管 | X-Guest-Session |
        | 陳值班 | Authorization   |

    # 到期時間由後端決定，和住客自己掃房間 QR 開的 session 同一條規則：綁定訂單的退房時刻。
    @happy
    場景: 開出來的住客 session 在訂單退房時刻到期，請求帶的到期時間不採用
      假設 系統中存在以下員工:
        | id    | tenant_id | name   | login_id | password_hash | role      | dept      |
        | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk | frontdesk |
      當 "林櫃檯" 替房 "r-305" 開住客 session "s-new"，請求另外帶到期時間 "2026-12-31T00:00:00Z"
      那麼 回應與資料庫裡住客 session "s-new" 的到期時間都是 "2026-10-20T12:00:00Z"

    @error @boundary
    場景: 訂單已經過了退房時刻就不開住客 session
      假設 系統中存在以下訂單:
        | id           | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source |
        | res-305-prev | 1         | PMS-88001       | r-305   | 2026-10-15T15:00:00Z | 2026-10-17T12:00:00Z | pms    |
      並且 系統中存在以下員工:
        | id    | tenant_id | name   | login_id | password_hash | role      | dept      |
        | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk | frontdesk |
      當 "林櫃檯" 替房 "r-305"、訂單 "res-305-prev" 開住客 session "s-ended"
      那麼請求應該失敗，回應為 409 且原因是 "reservation_ended"
      並且 系統未簽發任何 guest session 也未建立任何任務

    @error @boundary
    場景: 沒登入不能開住客 session
      當 未登入就替房 "r-305" 開住客 session
      那麼請求應該失敗，回應為 401

    @error @boundary
    場景: 房務不能開住客 session
      假設 系統中存在以下員工:
        | id    | tenant_id | name | login_id | password_hash | role         | dept         |
        | s-hk1 | 1         | 小美 | hk1      | hash-hk1      | housekeeping | housekeeping |
      當 "小美" 替房 "r-305" 開住客 session "s-hk"
      那麼請求應該失敗，回應為 403

    # 別家飯店的房間與訂單一律當作查無資料，回應碼不能透露別家飯店有沒有這筆資料。
    @error @boundary
    場景大綱: 只能替自己飯店的房間與訂單開住客 session
      假設 系統中存在以下房間:
        | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
        | r-t2-101 | 2         | 101     | 1     | standard_double | cleaned  | occupied  |
      並且 系統中存在以下訂單:
        | id         | tenant_id | reservation_ref | room_id  | checkin_at           | checkout_at          | source |
        | res-t2-101 | 2         | PMS-20077       | r-t2-101 | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      並且 系統中存在以下員工:
        | id    | tenant_id | name   | login_id | password_hash | role      | dept      |
        | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk | frontdesk |
      當 "林櫃檯" 替房 "<房間>"、訂單 "<訂單>" 開住客 session "s-other"
      那麼請求應該失敗，回應為 404
      並且 系統未簽發任何 guest session 也未建立任何任務

      例子:
        | 房間     | 訂單       |
        | r-t2-101 | res-t2-101 |
        | r-305    | res-t2-101 |

    @error @boundary
    場景: 別家飯店的住客 session 動不到本店的需求
      假設 系統中存在以下房間:
        | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
        | r-t2-101 | 2         | 101     | 1     | standard_double | cleaned  | occupied  |
      並且 系統中存在以下訂單:
        | id         | tenant_id | reservation_ref | room_id  | checkin_at           | checkout_at          | source |
        | res-t2-101 | 2         | PMS-20077       | r-t2-101 | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      並且 系統中存在以下員工:
        | id     | tenant_id | name | login_id | password_hash | role      | dept      |
        | s-t2-1 | 2         | 小芳 | fang1    | hash-fang1    | frontdesk | frontdesk |
      並且 "小芳" 替房 "r-t2-101" 開住客 session "s-t2-101"
      並且 系統中存在以下任務:
        | id      | tenant_id | type    | source | room_id | reservation_id | dept         | state | title  |
        | t-305-9 | 1         | amenity | guest  | r-305   | res-305        | housekeeping | pool  | 加毛巾 |
      當 住客以 X-Guest-Session 帶 session "s-t2-101" 收回任務 "t-305-9"
      那麼請求應該失敗，回應為 404
      並且 任務 "t-305-9" 的資料應為:
        | state |
        | pool  |

    @error @boundary
    場景大綱: 沒有飯店前綴或前綴被竄改的權杖被拒
      當 住客以 <帶法> 帶 <權杖> 送出選單項「加毛巾」
      那麼請求應該失敗，回應為 401

      例子:
        | 帶法            | 權杖                                |
        | X-Guest-Session | 沒有飯店前綴的權杖                  |
        | Authorization   | 沒有飯店前綴的權杖                  |
        | X-Guest-Session | session "sess-305" 前綴改成飯店 2 的權杖 |
        | Authorization   | session "sess-305" 前綴改成飯店 2 的權杖 |

    @error @boundary
    場景大綱: 已過期或已撤銷的住客 session 被拒
      假設 系統中存在以下住客session:
        | id     | tenant_id | session_token | room_id | reservation_id | expires_at   | revoked_at   |
        | gs-old | 1         | sess-old      | r-305   | res-305        | <expires_at> | <revoked_at> |
      當 住客以 <帶法> 帶 session "sess-old" 送出選單項「加毛巾」
      那麼請求應該失敗，回應為 401

      例子:
        | 帶法            | expires_at           | revoked_at           |
        | X-Guest-Session | 2026-10-19T08:00:00Z |                      |
        | Authorization   | 2026-10-19T08:00:00Z |                      |
        | X-Guest-Session | 2026-10-20T12:00:00Z | 2026-10-19T08:00:00Z |
        | Authorization   | 2026-10-20T12:00:00Z | 2026-10-19T08:00:00Z |
