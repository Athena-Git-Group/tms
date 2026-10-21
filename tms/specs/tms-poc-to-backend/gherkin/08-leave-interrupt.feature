# language: zh-TW
@leave
功能: 請假連鎖、中斷交接與班末掃描
  作為主管，我希望人不在時單不卡：請假自動退單、進行中的附交代退池、
  班末自動清掉掛名單。（spec 故事 8）

  背景:
    假設 「小美」名下有 301 的 accepted 單與 502 的 doing 單（SOP 勾了床鋪與浴室）

  規則: FR-070 請假連鎖——已接未開始自動退、進行中標中斷

    @happy
    場景: 登記請假回傳受影響清單
      當 王主管登記「小美」今日 08:00–17:00 請假
      那麼 301 的單自動由 "accepted" 退回 "pool"（事件 "returned_to_pool"）
      並且 502 的單轉為 "interrupted"（系統不自動退，因為不知道做到哪）
      並且 回應列出 requeued 與 interrupted 兩組任務

    @happy
    場景: 主管附交代後退池、優先通知同樓層
      假設 502 的單已 "interrupted"
      當 王主管以交代「浴室洗好了，床還沒鋪」執行退池
      那麼 任務轉為 "pool" 且交代跟著單走、接手的人看得到
      並且 推播優先送同樓層的人

  規則: 班末掃描——認領了沒開始的單不過夜

    @boundary
    場景: 班次結束自動退池
      假設 「阿芳」名下有一筆 accepted 且從未按開始的單、照租戶班次 17:00 結束
      當 班末掃描排程執行
      那麼 該單退回 "pool" 且事件 "returned_to_pool" 原因為班末退池

  規則: FR-071 主管操作守衛——改派、延後、取消

    @error @boundary
    場景大綱: 非法操作被擋
      假設 任務 state 為 <state>
      當 王主管執行 <動作>
      那麼 回應為 <結果>

      例子:
        | state     | 動作              | 結果 |
        | doing     | reassign          | 409 |
        | accepted  | undefer           | 409 |
        | inspected | cancel            | 409 |
        | pool      | cancel（未附原因） | 422 |

    @happy
    場景大綱: 合法操作
      假設 任務 state 為 <state>
      當 王主管執行 <動作>
      那麼 任務轉為 <新狀態>

      例子:
        | state    | 動作                       | 新狀態    |
        | pool     | reassign 給阿芳            | accepted  |
        | pool     | defer                      | deferred  |
        | deferred | undefer                    | pool      |
        | pool     | cancel（原因 manager_void） | cancelled |
