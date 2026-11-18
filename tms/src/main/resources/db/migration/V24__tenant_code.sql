-- V24：飯店識別代碼 tenants.code。
--   這是對外公開、放在發給飯店的網址裡的那一段（例如 athena），櫃檯畫面在登入前用它查飯店名稱。
--   id 是內部流水號，可以被逐一猜出，不對外；對外辨認飯店一律用 code。
--   代碼必有值、全系統唯一，寫入後不改：網址與書籤已經發出去，改了舊網址就全部失效。
--
-- 順序固定為先加可為空的欄位、替既有飯店補代碼、確認沒有漏掉的、最後才加限制：
-- 既有的列一開始沒有代碼，限制先加會直接失敗。
-- 整支在同一個交易裡執行，中途失敗時欄位也不會留下，補好對照後可以原樣重跑。

-- 1) 先加可為空的欄位 ----------------------------------------------------
ALTER TABLE tenants ADD COLUMN code text;

-- 2) 替認得的飯店補代碼 --------------------------------------------------
-- 代碼由營運逐家指定，不從飯店名稱自動產生。這裡只認得三家：
-- 開發種子資料的兩家，以及後端測試自己建立的那一家。
UPDATE tenants SET code = 'demo-hotel'   WHERE name = '示範飯店';
UPDATE tenants SET code = 'demo-hotel-2' WHERE name = '示範二店';
UPDATE tenants SET code = 'test-hotel'   WHERE name = '測試飯店';

-- 3) 還有沒補到的飯店就停下來 --------------------------------------------
-- 不替認不得的飯店編代碼：編出來的代碼會進到發給飯店的網址，之後不能改。
-- 訊息列出是哪幾家，營運指定代碼並補進上面的對照之後再跑一次。
DO $$
DECLARE
    missing_hotels text;
BEGIN
    SELECT string_agg(format('id=%s name=%s', id, name), '、' ORDER BY id)
      INTO missing_hotels
      FROM tenants
     WHERE code IS NULL;

    IF missing_hotels IS NOT NULL THEN
        RAISE EXCEPTION '以下飯店還沒有指定識別代碼（tenants.code），請營運逐家指定後再執行：%', missing_hotels;
    END IF;
END
$$;

-- 4) 加上必有值、唯一、格式與保留字的限制 --------------------------------
-- 飯店是人工開設的，所以格式與保留字由資料庫把關，不只靠程式檢查。
ALTER TABLE tenants ALTER COLUMN code SET NOT NULL;

ALTER TABLE tenants ADD CONSTRAINT uq_tenants_code UNIQUE (code);

-- 格式：長度 3 到 32；只能用小寫英文字母、數字與連字號；第一個字是英文字母；
-- 不以連字號結尾、不連用兩個連字號。這一段會放進網址的路徑或子網域，只收兩邊都安全的字元。
-- 只收小寫，比對時一字不差，就不會出現看起來一樣、其實是另一家的兩個代碼。
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_code_format
    CHECK (char_length(code) BETWEEN 3 AND 32
           AND code ~ '^[a-z][a-z0-9]*(-[a-z0-9]+)*$');

-- 保留字：會和前端路徑、後端路徑或常見子網域撞名的字不能當代碼。
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_code_reserved
    CHECK (code NOT IN (
        'api', 'public', 'login', 'logout', 'password', 'www', 'admin', 'app', 'desk', 'staff',
        'guest', 'board', 'triage', 'static', 'assets', 'auth', 'health', 'actuator', 'status', 'docs',
        'help', 'support', 'mail', 'cdn', 'dev', 'test', 'staging', 'tms', 'index', 'change-password'
    ));
