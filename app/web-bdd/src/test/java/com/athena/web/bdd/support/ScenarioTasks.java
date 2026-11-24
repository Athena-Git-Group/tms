package com.athena.web.bdd.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ScenarioTasks {

    public record Entry(String roomNo, String title, UUID taskId, String situation) {
    }

    private final List<Entry> entries = new ArrayList<>();

    private final Map<String, UUID> reservationByRoom = new LinkedHashMap<>();

    public void add(Entry entry) {
        Objects.requireNonNull(entry.roomNo(), "房號必填");
        Objects.requireNonNull(entry.taskId(), "task id 必填");
        for (Entry existing : entries) {
            if (existing.roomNo().equals(entry.roomNo()) && Objects.equals(existing.title(), entry.title())) {
                throw new IllegalArgumentException("「" + entry.roomNo() + "」已經有一張標題是「" + entry.title()
                        + "」的單，同房同標題的兩張單之後分不出來");
            }
        }
        entries.add(entry);
    }

    public UUID idOf(String roomNo) {
        return entryOf(roomNo).taskId();
    }

    public UUID idOf(String roomNo, String title) {
        return entryOf(roomNo, title).taskId();
    }

    public Entry entryOf(String roomNo) {
        List<Entry> inRoom = entriesIn(roomNo);
        if (inRoom.isEmpty()) {
            throw new IllegalArgumentException("這個場景沒有準備「" + roomNo + "」的單");
        }
        if (inRoom.size() > 1) {
            throw new IllegalArgumentException("「" + roomNo + "」有 " + inRoom.size() + " 張單，要用標題分辨："
                    + inRoom.stream().map(Entry::title).toList());
        }
        return inRoom.get(0);
    }

    public Entry entryOf(String roomNo, String title) {
        return entriesIn(roomNo).stream()
                .filter(entry -> Objects.equals(entry.title(), title))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("「" + roomNo + "」沒有標題是「" + title
                        + "」的單，這間房有：" + entriesIn(roomNo).stream().map(Entry::title).toList()));
    }

    public List<Entry> entriesIn(String roomNo) {
        return entries.stream().filter(entry -> entry.roomNo().equals(roomNo)).toList();
    }

    public Optional<Entry> entryOf(UUID taskId) {
        return entries.stream().filter(entry -> entry.taskId().equals(taskId)).findFirst();
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public Optional<Entry> firstIn(String situation) {
        return entries.stream().filter(entry -> situation.equals(entry.situation())).findFirst();
    }

    public Optional<UUID> reservationOf(String roomNo) {
        return Optional.ofNullable(reservationByRoom.get(roomNo));
    }

    public void rememberReservation(String roomNo, UUID reservationId) {
        reservationByRoom.put(roomNo, reservationId);
    }
}
