package com.example.ainews.web;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** テンプレートから ${@fmt.ago(...)} のように呼ぶ表示用ヘルパー */
@Component("fmt")
public class ViewHelper {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TOKYO);

    public String ago(Instant t) {
        if (t == null) {
            return "";
        }
        long minutes = Math.max(0, Duration.between(t, Instant.now()).toMinutes());
        if (minutes < 1) {
            return "たった今";
        }
        if (minutes < 60) {
            return minutes + "分前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "時間前";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "日前";
        }
        return DATE_TIME.format(t);
    }

    public String dateTime(Instant t) {
        return t == null ? "" : DATE_TIME.format(t);
    }
}
