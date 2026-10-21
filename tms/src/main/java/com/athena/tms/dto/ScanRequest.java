package com.athena.tms.dto;

/**
 * 掃碼請求體。token 走 body 而不是 Authorization header：QR token 不是
 * 認證憑證（session token 才是），塞進 Bearer 會撞上 auth filter 的格式分流
 * （一個點＝住客 session）而被誤判 401。
 */
public record ScanRequest(String token) {
}
