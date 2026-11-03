# language: zh-TW
@identity @tenancy
功能: 員工登入、角色授權與零跨店洩漏
  作為平台營運者，我希望伺服器端強制角色×動作與租戶隔離，
  以便多店共用一庫而零跨店洩漏。

  背景:
    假設 系統中存在以下房間:
      | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301    | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305    | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
      | r-t2-101 | 2         | 101     | 1     | standard_double | dirty    | occupied  |
    並且 系統中存在以下員工:
      | id     | tenant_id | name   | login_id | password_hash | role         | dept         |
      | s-hk1  | 1         | 小美   | hk1      | hash-hk1      | housekeeping | housekeeping |
      | s-mt1  | 1         | 阿強   | mt1      | hash-mt1      | engineering  | engineering  |
      | s-fd1  | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk    | frontdesk    |
      | s-mgr  | 1         | 王主管 | mgr1     | hash-mgr1     | manager      |              |
      | s-own  | 1         | 張老闆 | own1     | hash-own1     | owner        |              |
      | s-t2-1 | 2         | 小芳   | fang1    | hash-fang1    | frontdesk    | frontdesk    |

  Rule: 登入換憑證並登記推播裝置

    @happy
    場景: 登入成功順帶登記裝置
      當 「小美」以正確帳密自同一台手機登入兩次
      那麼 取得含 tenant_id/staff_id/role/dept 的憑證，有效期為 12 小時
      並且 staff_devices 對同一裝置去重、僅登記一列，且登入不綁裝置

    @happy
    場景: 主管無部門時憑證不編造 dept
      當 王主管以正確帳密登入
      那麼 憑證 role 為 "manager" 且不含 dept，回應也不含密碼雜湊

    @error @boundary
    場景: 密碼錯誤
      當 以 login_id "hk1" 與錯誤密碼登入
      那麼請求應該失敗，回應為 401
      並且 不簽發任何憑證

    # 三種登入失敗若回應可區分，攻擊者就能藉差異枚舉有效帳號。
    # 缺欄位是客戶端 bug，用 422（請求格式不完整）與憑證錯誤的 401 分流。
    @error @boundary
    場景: 帳號不存在、租戶不存在與密碼錯誤不可區分
      當 分別以 錯誤密碼、不存在的帳號、不存在的租戶 登入
      那麼 三者請求應該都失敗，回應皆為 401 且錯誤碼一致
      並且 缺密碼欄位的登入請求應該失敗，回應為 422

    # 停用立即生效，不等舊憑證的有效期自然到期。
    @error @boundary
    場景: 停用帳號不能登入也不能呼叫
      假設 「阿強」的帳號 active 為 false
      當 他登入並以舊憑證呼叫員工 API
      那麼 兩者請求應該都失敗，回應皆為 401

  Rule: 憑證真偽——無憑證或偽造簽章一律 401

    @error @boundary
    場景: 無憑證、垃圾字串與偽造簽章都被拒
      當 分別以 無憑證、垃圾字串、偽造簽章的 JWT 呼叫員工 API
      那麼 三者請求應該都失敗，回應皆為 401

  Rule: 角色×動作矩陣——前綴外一律 403

    @error @boundary
    場景大綱: 越權呼叫被拒
      當 <角色> 呼叫 <動作>
      那麼請求應該失敗，回應為 403

      例子:
        | 角色      | 動作         |
        | 房務 小美 | 主管查房提交 |
        | 老闆帳號  | 任務認領     |
        | 櫃檯      | 房間 QR 輪替 |

    @happy
    場景: 老闆只讀儀表板放行
      當 老闆帳號讀取儀表板
      那麼 回應為 200

    @boundary
    場景大綱: 角色×前綴矩陣逐格驗證——放行格
      當 <角色> 以 <方法> 呼叫 <路徑>
      那麼 回應為 200

      例子:
        | 角色 | 方法 | 路徑         |
        | 房務 | GET  | /staff/probe |
        | 房務 | POST | /staff/probe |
        | 工務 | GET  | /staff/probe |
        | 工務 | POST | /staff/probe |
        | 櫃檯 | GET  | /staff/probe |
        | 櫃檯 | GET  | /desk/probe  |
        | 櫃檯 | POST | /desk/probe  |
        | 主管 | GET  | /staff/probe |
        | 主管 | POST | /staff/probe |
        | 主管 | GET  | /desk/probe  |
        | 主管 | POST | /desk/probe  |
        | 主管 | GET  | /admin/probe |
        | 主管 | POST | /admin/probe |
        | 老闆 | GET  | /owner/probe |

    @boundary
    場景大綱: 角色×前綴矩陣逐格驗證——擋下格
      當 <角色> 以 <方法> 呼叫 <路徑>
      那麼請求應該失敗，回應為 403

      例子:
        | 角色 | 方法 | 路徑              |
        | 房務 | GET  | /desk/probe       |
        | 房務 | POST | /desk/probe       |
        | 房務 | GET  | /admin/probe      |
        | 房務 | POST | /admin/probe      |
        | 房務 | GET  | /owner/probe      |
        | 房務 | GET  | /guest/context    |
        | 工務 | POST | /admin/probe      |
        | 櫃檯 | POST | /staff/probe      |
        | 櫃檯 | GET  | /admin/probe      |
        | 櫃檯 | POST | /admin/probe      |
        | 櫃檯 | GET  | /owner/probe      |
        | 主管 | GET  | /owner/probe      |
        | 主管 | GET  | /guest/context    |
        | 老闆 | POST | /owner/probe      |
        | 老闆 | GET  | /staff/probe      |
        | 老闆 | POST | /staff/probe      |
        | 老闆 | GET  | /desk/probe       |
        | 老闆 | GET  | /admin/probe      |
        | 主管 | GET  | /outside/anything |

  Rule: 住客 session——驗過即綁房與訂單

    @happy
    場景: 有效 session 綁房與訂單、但進不了員工門
      假設 系統中存在以下訂單:
        | id      | tenant_id | reservation_ref | room_id | checkin_at           | checkout_at          | source |
        | res-305 | 1         | PMS-88321       | r-305   | 2026-10-18T15:00:00Z | 2026-10-20T12:00:00Z | pms    |
      並且 系統中存在以下住客session:
        | id     | tenant_id | session_token | room_id | reservation_id | expires_at           | revoked_at |
        | gs-305 | 1         | sess-305      | r-305   | res-305        | 2026-10-20T12:00:00Z |            |
      # 住客工作階段（session）已把房號與訂單綁進上下文，下游 API 不用再查資料庫。
      # 住客工作階段是驗證過的真實身分、只是權限不夠做員工的事，
      # 所以回 403（權限不足）而不是 401（沒有身分）。
      當 以 session "sess-305" 讀取住客上下文
      那麼 上下文帶房 305 與該訂單的編號
      並且 以該 session 呼叫住客寫入動作回 200
      並且 以該 session 呼叫員工 API 的請求應該失敗，回應為 403

    @error @boundary
    場景: 格式正確但查無此列的 session 是 401
      當 以一枚格式正確但資料庫查無此列的 session token 呼叫住客 API
      那麼請求應該失敗，回應為 401

  Rule: 跨租戶一律 404——當作不存在

    @happy
    場景: 自家房間 id 查得到
      當 王主管以自家房間 "r-301" 的 id 查詢房間
      那麼 回應為 200

    @error @boundary
    場景: 拿別店的房間 id 讀取 404
      當 王主管以租戶 2 房間 "r-t2-101" 的 id 查詢房間
      那麼請求應該失敗，回應為 404 且原因是 "not_found"

    @error @boundary
    場景: 別店拿本店的房間 id 讀取同樣 404
      當 租戶 2 的小芳以租戶 1 房間 "r-301" 的 id 查詢房間
      那麼請求應該失敗，回應為 404 且原因是 "not_found"
