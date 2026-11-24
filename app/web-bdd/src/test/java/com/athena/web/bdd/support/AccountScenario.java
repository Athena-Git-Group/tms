package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.microsoft.playwright.Page;
import java.util.HashMap;
import java.util.Map;

public class AccountScenario {

    private final Map<String, String> temporaryPasswords = new HashMap<>();

    private final Map<String, String> loginIds = new HashMap<>();

    private Page originalTab;

    private Page newTab;

    public void rememberTemporaryPassword(String staffName, String password) {
        temporaryPasswords.put(staffName, password);
    }

    public String temporaryPassword(String staffName) {
        String password = temporaryPasswords.get(staffName);
        if (password == null) {
            throw new IllegalStateException("場景裡沒有給過「" + staffName + "」臨時密碼");
        }
        return password;
    }

    public void rememberLoginId(String staffName, String loginId) {
        loginIds.put(staffName, loginId);
    }

    public CreatedStaff current(CreatedStaff staff) {
        String loginId = loginIds.get(staff.name());
        if (loginId == null) {
            return staff;
        }
        return new CreatedStaff(staff.id(), staff.name(), loginId, staff.password(), staff.role(), staff.dept());
    }

    public void rememberTabs(Page original, Page added) {
        this.originalTab = original;
        this.newTab = added;
    }

    public Page originalTab() {
        if (originalTab == null) {
            throw new IllegalStateException("場景裡還沒有開過「原本的分頁」與「新分頁」");
        }
        return originalTab;
    }

    public Page newTab() {
        if (newTab == null) {
            throw new IllegalStateException("場景裡還沒有開過「原本的分頁」與「新分頁」");
        }
        return newTab;
    }
}
