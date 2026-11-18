-- 讓推播送不出去時不會擋住同一張單的事件，櫃檯看板才收得到每一張單的即時更新。
-- relay 依排隊鍵一筆一筆按順序送，同一把鍵只要有一筆重試用完、停在 failed，後面的訊息就不再送。
-- 推播的排隊鍵一律以 push: 開頭，和單的事件分開排隊；這支遷移讓資料庫裡既有的推播也照這個規則排隊。
UPDATE outbox
   SET aggregate_key = 'push:' || aggregate_key
 WHERE kind = 'push'
   AND aggregate_key NOT LIKE 'push:%';
