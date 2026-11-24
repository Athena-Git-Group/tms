package com.athena.tms.bdd.support;

import com.athena.tms.repair.RepairTicketNumbers;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.jdbc.core.JdbcTemplate;

/** 讓測試資料指定下一張工務單拿到的單號；沒有指定時照後端的計數器取號，計數器本身不被改動。 */
public class ScriptedRepairTicketNumbers extends RepairTicketNumbers {

    private final Deque<String> scripted = new ArrayDeque<>();

    public ScriptedRepairTicketNumbers(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public synchronized void handOutNext(String ticketNo) {
        scripted.addLast(ticketNo);
    }

    public synchronized void forgetUnused() {
        scripted.clear();
    }

    @Override
    public synchronized String next(long tenantId) {
        String ticketNo = scripted.pollFirst();
        return ticketNo != null ? ticketNo : super.next(tenantId);
    }
}
