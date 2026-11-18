package com.athena.tms.dto.desk;

/** 不用登入就看得到的飯店資料。只放名稱一項，登入前不能多透露別的。 */
public record PublicHotel(String name) {}
