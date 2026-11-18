package com.athena.tms.dto;

// client 是從哪一個畫面登入，選填；員工 App 不帶，櫃檯 Web 帶 frontdesk_web。
// hotelCode 是要登入哪一家飯店的識別代碼，選填；櫃檯 Web 帶網址上的那一家，員工 App 目前不帶。
public record StaffLoginRequest(String loginId, String password, String client, String hotelCode) {}
