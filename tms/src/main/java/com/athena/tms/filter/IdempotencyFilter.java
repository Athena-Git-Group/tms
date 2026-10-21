package com.athena.tms.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import com.athena.tms.service.IdempotencyService;
import com.athena.tms.service.IdempotencyTenantResolver;

/**
 * 冪等 servlet filter：業務 POST 帶 Idempotency-Key，弱網
 * 重送時回放上次結果（狀態碼＋body 一致、handler 不再執行），電梯間按三次
 * 「完成」只完成一次。獨立 servlet filter、不掛進 security chain，
 * 與認證層只透過 IdempotencyTenantResolver 的 request attribute 約定接頭，
 * 兩邊可各自演進。
 *
 * <p>已知縫隙（MVP 取捨）：結果在 handler 交易 commit 之後才落簿，兩者間
 * 當機會讓重送重算——此時 TaskEngine 的條件式更新（前提不符就一列也不改）
 * 會回 409 而不是重做，不掉單、不重複，只是回應碼跟第一次不同；兩個同鍵
 * 請求「同時」衝線也都會執行 handler，同樣由這道條件式更新擋下第二次效果。
 * 真併發去重需要先佔位鎖，MVP 不做。
 */
@Component
@Order(IdempotencyFilter.FILTER_ORDER)
public class IdempotencyFilter extends OncePerRequestFilter {

    /** security chain 掛在 -100，冪等要看得到認證層寫入的 request attribute，排它後面。 */
    public static final int FILTER_ORDER = 0;

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    /** 回放的回應帶此 header，排查「為什麼這請求沒進 handler」時不用翻 DB。 */
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private final IdempotencyService idempotencyService;
    private final IdempotencyTenantResolver tenantResolver;

    public IdempotencyFilter(IdempotencyService idempotencyService,
                             IdempotencyTenantResolver tenantResolver) {
        this.idempotencyService = idempotencyService;
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 只攔 POST：GET/PUT/DELETE 語意本身可重複，包進冪等簿只是白佔 7 天儲存
        if (!HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String opId = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        Long tenantId = tenantResolver.resolveTenantId(request).orElse(null);
        // 沒帶鍵或認不出租戶就放行不做冪等：「必帶鍵」的強制是 API 層的驗證
        // 規則（422），filter 擅自擋會把登入等非業務 POST 一起誤殺
        if (opId == null || opId.isBlank() || tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<IdempotencyService.StoredResult> replay = idempotencyService.findResult(tenantId, opId);
        if (replay.isPresent()) {
            writeReplay(response, replay.get());
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
            // 5xx 不落簿：多半是暫時性故障，回放等於把一次壞運氣釘成永久答案；
            // 4xx 要落——409/422 是確定性結論，重送拿到一樣的拒絕才叫冪等
            if (wrapper.getStatus() < 500) {
                String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                try {
                    idempotencyService.store(tenantId, opId, wrapper.getStatus(), wrapper.getContentType(), body);
                } catch (RuntimeException e) {
                    logger.warn("processed_ops 落簿失敗——這把鍵的重送會重算而不是回放", e);
                }
            }
        } finally {
            // handler 丟例外時 wrapper 裡可能已有 error dispatch 寫入的內容，不搬回去回應就空白
            wrapper.copyBodyToResponse();
        }
    }

    private void writeReplay(HttpServletResponse response, IdempotencyService.StoredResult result)
            throws IOException {
        response.setStatus(result.status());
        if (result.contentType() != null) {
            response.setContentType(result.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        if (!result.body().isEmpty()) {
            // 回放一律寫 bytes，不走 getWriter()：Tomcat Writer 預設 ISO-8859-1，
            // content-type 沒帶 charset 時非 Latin-1 字元會輸出成 '?'（中文 body 毀損）。
            // 存簿側以 UTF-8 解碼，這裡以 UTF-8 編回，wire bytes 與第一次完全一致。
            byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
    }
}
