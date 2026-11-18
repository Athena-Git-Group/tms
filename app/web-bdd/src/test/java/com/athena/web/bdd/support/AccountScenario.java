package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.microsoft.playwright.Page;
import java.util.HashMap;
import java.util.Map;

/**
 * 讓「帳號與登入」的 Given、When、Then 三個步驟類別共用同一份場景資料。
 *
 * <p>前面的步驟給了臨時密碼、開了第二個分頁，後面的步驟要拿同一個密碼登入、在指定的分頁上操作與檢查，
 * 所以這些值不能只放在單一個步驟類別裡。三個類別用建構子拿到的是同一個物件；每個場景是全新的一個，
 * 不會帶到下一個場景。
 */
public class AccountScenario {

    private final Map<String, String> temporaryPasswords = new HashMap<>();

    private final Map<String, String> loginIds = new HashMap<>();

    private Page originalTab;

    private Page newTab;

    /** 鍵是員工姓名；同一位員工再給一次臨時密碼時，以後給的為準。 */
    public void rememberTemporaryPassword(String staffName, String password) {
        temporaryPasswords.put(staffName, password);
    }

    /** 場景裡沒有給過這位員工臨時密碼時直接失敗，讓步驟順序寫錯的場景一眼看得出來。 */
    public String temporaryPassword(String staffName) {
        String password = temporaryPasswords.get(staffName);
        if (password == null) {
            throw new IllegalStateException("場景裡沒有給過「" + staffName + "」臨時密碼");
        }
        return password;
    }

    /** 鍵是員工姓名；場景裡把這位員工的員工編號改掉時記下新的。 */
    public void rememberLoginId(String staffName, String loginId) {
        loginIds.put(staffName, loginId);
    }

    /** 預設帳號換上這個場景改過的員工編號；沒改過時原樣回傳。 */
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
