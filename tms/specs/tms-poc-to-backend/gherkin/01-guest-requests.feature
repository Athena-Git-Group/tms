# language: zh-TW
@guest
功能: 住客掃碼提需求、看進度、評分
  作為住客，我希望掃房間 QR 就能提需求、看進度、完成後評分，
  以便不用打電話也不用安裝 App。（spec 故事 1；判定值溯源 spec §5.2 範例資料）

  背景:
    假設 租戶 1「安棲商旅」已存在下列房間
      | room_no | floor | room_type       | hk_state | occupancy |
      | 301     | 3     | standard_double | dirty    | occupied  |
      | 305     | 3     | standard_double | cleaned  | occupied  |
      | 502     | 5     | suite           | dirty    | vacant    |
    並且 房 305 有當前訂單「PMS-88321」（住期 10-18 至 10-20，attrs 含 vip:true）
    並且 房 305 與房 502 各有一枚現行 QR token

  規則: FR-001 掃碼簽發——空房與作廢 token 擋在門外（E-03）

    @error @boundary
    場景: 空房掃碼不簽 session、不建單
      假設 房 502 查無當前有效訂單
      當 以房 502 的現行 token 呼叫掃碼
      那麼 回應為 409 且錯誤碼為 "room_vacant"
      並且 系統未簽發任何 guest session 也未建立任何任務

    @error @boundary
    場景: 已作廢 token 掃碼被擋
      假設 房 305 的舊版 token 已標記 revoked
      當 以該舊版 token 呼叫掃碼
      那麼 回應為 409 且畫面語意為「請洽櫃檯」

    @happy
    場景: 在住房掃碼換 session 並取得資訊頁
      當 以房 305 的現行 token 呼叫掃碼
      那麼 簽發綁定房 305 與訂單「PMS-88321」的 guest session（expires_at = 退房時間）
      並且 回應含資訊頁與需求選單

  規則: E-17 住客 session 驗證——未過期且未撤銷缺一不可

    @error @boundary
    場景大綱: 無效 session 一律 401
      假設 房 305 的 guest session <狀態>
      當 以該 session 查詢本次住宿的需求
      那麼 回應為 401

      例子:
        | 狀態                      |
        | 已標記 revoked_at（被撤銷） |
        | expires_at 已過（退房）     |

  規則: FR-002 資訊頁前台直接提供、不進任務流

    @happy
    場景: 重新整理資訊頁不產生任務
      當 以有效 session 讀取資訊頁
      那麼 回應內容來自租戶設定 guest_info_pages
      並且 任務總數不變

  規則: FR-003 建單分流——選單直入部門池、自由文字過規則引擎

    @boundary
    場景: 自由文字猜不準時停在待判讀
      當 住客以自由文字「房間有怪聲」送出需求（規則引擎命中 0 個部門）
      那麼 建立任務 state 為 "triage" 且 dept 為 "frontdesk"

    @happy
    場景大綱: 建單路徑
      當 住客送出 <輸入>
      那麼 建立任務 type 為 <type>、dept 為 <dept>、state 為 <state>
      並且 事件 "created" 的 payload 帶齊建立快照（種類、來源、房號、訂單、標題）

      例子:
        | 輸入                       | type    | dept         | state |
        | 選單項「毛巾×2」            | amenity | housekeeping | pool  |
        | 自由文字「冷氣不冷」（命中工務） | repair  | engineering  | pool  |

    @happy
    場景: 規則引擎命中留痕可解釋
      當 自由文字「冷氣不冷」被規則引擎命中工務
      那麼 事件 payload 記錄命中的 rule_id

  規則: FR-004（E-01）同房限流——10 分鐘 3 筆，第 4 筆 429

    @error @boundary
    場景: 第 4 筆被限流
      假設 房 305 在 10 分鐘內已成功建立 3 筆需求
      當 再送出第 4 筆需求
      那麼 回應為 429
      並且 第 3 筆需求仍為成功建立

  規則: FR-005 進度清單以 session 綁的訂單過濾、不按房號撈

    @boundary
    場景: 新住客看不到前一位住客的需求
      假設 房 305 的前一筆訂單底下有歷史需求「毛巾×2」
      並且 新住客以新訂單掃碼取得新 session
      當 新住客查詢本次住宿的需求
      那麼 清單不含前一筆訂單的任何需求

  規則: FR-006（E-04）收回只限還沒人認領

    @error @boundary
    場景: 已被認領的需求不能收回
      假設 「毛巾×2」任務已被認領（state 為 "accepted"）
      當 住客對該任務呼叫收回
      那麼 回應為 409 且錯誤碼為 "state_conflict"

    @happy
    場景大綱: 未認領可收回且不進統計
      假設 任務 state 為 <state>
      當 住客呼叫收回
      那麼 任務轉為 "cancelled" 且事件 "cancelled" 的原因為 "guest_withdrew"

      例子:
        | state  |
        | triage |
        | pool   |

  規則: FR-007（E-05）評分 1–5、僅完成後、一次性

    @error @boundary
    場景大綱: 非法評分被拒
      假設 任務 state 為 <state> 且 <前置>
      當 住客對該任務評 <分數> 星
      那麼 回應為 <結果>

      例子:
        | state | 前置       | 分數 | 結果 |
        | done  | 尚未評分    | 0   | 422  |
        | done  | 尚未評分    | 6   | 422  |
        | doing | 尚未評分    | 4   | 409  |
        | done  | 已評過 4 星 | 5   | 409  |

    @happy
    場景大綱: 合法評分成功
      假設 任務 state 為 <state> 且尚未評分
      當 住客評 <分數> 星
      那麼 任務 rating 為 <分數> 且事件 "rated" 帶分數

      例子:
        | state     | 分數 |
        | done      | 1   |
        | inspected | 5   |

  規則: FR-008 住客即時更新——SSE 只推自己房的任務

    @happy
    場景: 任務完成時住客畫面自動更新
      假設 住客以有效 session 開啟事件串流
      當 其任務由員工標記完成
      那麼 住客在 2 秒內收到該任務的變更通知（SC-002）
      並且 串流不包含其他房或其他訂單的任務
