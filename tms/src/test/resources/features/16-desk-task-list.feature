# language: zh-TW
@desk
功能: 櫃檯任務清單依日期、負責人與狀態篩選
  作為櫃檯，我希望打開任務清單只看到今天的單，並能看某位房務手上進行中與已完成的單，
  以便清單不會隨營運天數無限變長，也能快速回答「她做到哪了」。

  背景:
    假設 系統中存在以下員工:
      | id    | tenant_id | name   | login_id | password_hash | role         | dept         |
      | s-fd1 | 1         | 林櫃檯 | fd1      | hash-fd1      | frontdesk    | frontdesk    |
      | s-hk1 | 1         | 小美   | hk1      | hash-hk1      | housekeeping | housekeeping |
      | s-hk2 | 1         | 小華   | hk2      | hash-hk2      | housekeeping | housekeeping |
    並且 系統中存在以下房間:
      | id       | tenant_id | room_no | floor | room_type       | hk_state | occupancy |
      | r-301    | 1         | 301     | 3     | standard_double | dirty    | occupied  |
      | r-305    | 1         | 305     | 3     | standard_double | cleaned  | occupied  |
      | r-t2-101 | 2         | 101     | 1     | standard_double | dirty    | vacant    |

  Rule: 沒帶日期就只看今天，工作日的算法與員工首頁的今日任務相同

    @happy
    場景: 預設只回今天的單
      假設 系統中存在以下任務:
        | id        | tenant_id | type     | source | room_id | dept         | state    | title        | assignee_id | work_date  |
        | t-today   | 1         | cleaning | system | r-305   | housekeeping | accepted | 今天打掃 305 | s-hk1       | 2026-01-01 |
        | t-yday    | 1         | cleaning | system | r-301   | housekeeping | accepted | 昨天打掃 301 | s-hk1       | 2026-01-01 |
        | t-open    | 1         | amenity  | desk   | r-305   | housekeeping | pool     | 加毛巾       |             |            |
        | t-done-yd | 1         | amenity  | desk   | r-305   | housekeeping | done     | 昨天補牙刷   | s-hk2       |            |
      並且 任務 "t-today" 的工作日為今天
      並且 任務 "t-yday" 的工作日為昨天
      並且 任務 "t-done-yd" 於昨天完成
      當 櫃檯查詢任務清單
      那麼 清單只有標題「今天打掃 305」、「加毛巾」

    @happy
    場景: 查昨天看得到昨天完成的單，看不到今天手上的單
      假設 系統中存在以下任務:
        | id        | tenant_id | type     | source | room_id | dept         | state    | title        | assignee_id | work_date  |
        | t-today   | 1         | cleaning | system | r-305   | housekeeping | accepted | 今天打掃 305 | s-hk1       | 2026-01-01 |
        | t-yday    | 1         | cleaning | system | r-301   | housekeeping | accepted | 昨天打掃 301 | s-hk1       | 2026-01-01 |
        | t-open    | 1         | amenity  | desk   | r-305   | housekeeping | pool     | 加毛巾       |             |            |
        | t-done-yd | 1         | amenity  | desk   | r-305   | housekeeping | done     | 昨天補牙刷   | s-hk2       |            |
      並且 任務 "t-today" 的工作日為今天
      並且 任務 "t-yday" 的工作日為昨天
      並且 任務 "t-done-yd" 於昨天完成
      當 櫃檯查詢昨天的任務清單
      那麼 清單只有標題「昨天打掃 301」、「昨天補牙刷」

  Rule: 可以只看某位房務手上特定狀態的單

    @happy
    場景: 依負責人與狀態篩出進行中與已完成
      假設 系統中存在以下任務:
        | id   | tenant_id | type    | source | room_id | dept         | state    | title  | assignee_id |
        | t-a1 | 1         | amenity | desk   | r-305   | housekeeping | accepted | 礦泉水 | s-hk1       |
        | t-a3 | 1         | amenity | desk   | r-305   | housekeeping | done     | 補牙刷 | s-hk1       |
        | t-a2 | 1         | amenity | desk   | r-301   | housekeeping | doing    | 換床單 | s-hk1       |
        | t-b1 | 1         | amenity | desk   | r-301   | housekeeping | doing    | 加枕頭 | s-hk2       |
      當 櫃檯查詢員工 "s-hk1" 狀態為 "accepted,doing" 的任務清單
      那麼 清單只有標題「礦泉水」、「換床單」
      當 櫃檯查詢員工 "s-hk1" 狀態為 "done,inspected,closed" 的任務清單
      那麼 清單只有標題「補牙刷」

  Rule: 別家飯店的單永遠不會出現

    @security
    場景: 另一個租戶今天的單不在清單裡
      假設 系統中存在以下任務:
        | id      | tenant_id | type  | source | room_id  | dept         | state | title    |
        | t-mine  | 1         | other | system | r-305    | housekeeping | pool  | 本店的單 |
        | t-other | 2         | other | system | r-t2-101 | housekeeping | pool  | 別店的單 |
      當 櫃檯查詢任務清單
      那麼 清單只有標題「本店的單」

  Rule: 查詢參數不合法一律 400，日期範圍最多 31 天

    @error @boundary
    場景大綱: 不合法的查詢參數被拒
      當 櫃檯以查詢參數 "<參數>" 查詢任務清單
      那麼請求應該失敗，回應為 400 且原因是 "validation_failed"

      例子:
        | 參數                             | 說明             |
        | from=2026-01-01&to=2026-02-02    | 相差 32 天       |
        | from=2026-02-02&to=2026-01-01    | 起日晚於迄日     |
        | state=finished                   | 不存在的狀態     |
        | state=doing&state=paused         | 其中一個狀態不存在 |
        | assigneeId=not-a-uuid            | 負責人不是 UUID  |
        | from=2026-13-01                  | 日期格式不正確   |

    @boundary
    場景: 剛好相差 31 天可以查
      當 櫃檯以查詢參數 "from=2026-01-01&to=2026-02-01" 查詢任務清單
      那麼 回應為 200
