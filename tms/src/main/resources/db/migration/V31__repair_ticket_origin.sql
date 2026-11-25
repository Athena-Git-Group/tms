ALTER TABLE repair_tickets
    ADD COLUMN origin text NOT NULL DEFAULT 'reported'
        CONSTRAINT chk_repair_tickets_origin CHECK (origin IN ('reported', 'scheduled'));
