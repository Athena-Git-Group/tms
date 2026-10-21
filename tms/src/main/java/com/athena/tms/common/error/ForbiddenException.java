package com.athena.tms.common.error;

/**
 * 403：身分有效但動作不被允許（角色×動作矩陣不放行、查房人是原清房者）。
 * 注意：跨租戶不走這裡，一律 NotFoundException，不洩漏存在性。
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(403, "forbidden", message);
    }
}
