-- V14：repair_tickets 補急件判定需要的三個欄位：is_urgent（目前是否仍視為急件）、
-- urgent_rule（開單當下系統判定命中的規則說明）、urgent_downgrade_reason（主管將急件
-- 降級為一般件時附的原因）。急件改由系統依飯店設定的規則判定，房務人員不能自行指定；
-- 判定結果與降級動作都要同時寫回既有的 tasks.urgent，待認領池的排序索引直接用
-- tasks.urgent 排序、不需要 JOIN 本表。本檔只建立欄位骨架，規則判定與降級的應用
-- 邏輯留給之後的業務邏輯實作。
--
-- 既有資料列（若有）沒有急件判定資料可回填，三欄一律用預設值／NULL 落地，不另外
-- 跑 UPDATE 灌業務值，沿用 V5 的既有慣例。
--
-- task_events.type 不需要新增 CHECK：V1 建表時刻意不對 type 設 CHECK（「加值不改
-- 表」），本輪新增的列舉值 urgent_downgraded 純粹是文件記載的擴充，沒有對應的資料庫
-- 結構變動。
ALTER TABLE repair_tickets
    ADD COLUMN is_urgent boolean NOT NULL DEFAULT false, -- 目前是否仍視為急件；開單時由系統判定寫入，主管降級時改為 false
    ADD COLUMN urgent_rule text, -- 開單當下系統判定命中的規則說明；判定為一般件時為 NULL，降級後仍保留原判定理由供追溯
    ADD COLUMN urgent_downgrade_reason text; -- 主管降級為一般件時附的原因；未降級為 NULL
