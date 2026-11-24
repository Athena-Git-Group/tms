package com.athena.bddsupport;

import com.athena.bddsupport.BackendChannel.CreatedStaff;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TaskSituationSpec {

    public enum AcceptedVia {
        PLAN_ASSIGNED,
        SELF_CLAIMED,
        MANAGER_ASSIGNED,
        REASSIGNED
    }

    final String roomNo;
    final String type;
    final String source;

    String title;
    String dept;
    String displayNo;
    UUID reservationId;
    String note;
    String guestText;
    String guestLang;
    String translatedText;
    Integer estMin;
    boolean urgent;

    CreatedStaff assignee;
    CreatedStaff previousAssignee;
    CreatedStaff createdBy;
    CreatedStaff triagedBy;
    CreatedStaff assignedBy;
    CreatedStaff actedBy;
    CreatedStaff returnedBy;
    String poolSource;
    AcceptedVia acceptedVia;
    String triageVia;

    OffsetDateTime at;
    OffsetDateTime createdAt;
    OffsetDateTime triagedAt;
    OffsetDateTime acceptedAt;
    OffsetDateTime startedAt;
    OffsetDateTime doneAt;

    String reason;
    String handoverNote;
    String answer;
    Integer sopCheckedCount;
    String repairCategory;
    String resolutionType;

    private TaskSituationSpec(String roomNo, String type, String source) {
        this.roomNo = roomNo;
        this.type = type;
        this.source = source;
    }

    public static TaskSituationSpec of(String roomNo, String type, String source) {
        return new TaskSituationSpec(roomNo, type, source);
    }

    public TaskSituationSpec title(String title) {
        this.title = title;
        return this;
    }

    public TaskSituationSpec dept(String dept) {
        this.dept = dept;
        return this;
    }

    public TaskSituationSpec displayNo(String displayNo) {
        this.displayNo = displayNo;
        return this;
    }

    public TaskSituationSpec reservationId(UUID reservationId) {
        this.reservationId = reservationId;
        return this;
    }

    public TaskSituationSpec note(String note) {
        this.note = note;
        return this;
    }

    public TaskSituationSpec guestText(String text, String lang, String translated) {
        this.guestText = text;
        this.guestLang = lang;
        this.translatedText = translated;
        return this;
    }

    public TaskSituationSpec estMin(Integer estMin) {
        this.estMin = estMin;
        return this;
    }

    public TaskSituationSpec urgent(boolean urgent) {
        this.urgent = urgent;
        return this;
    }

    public TaskSituationSpec assignee(CreatedStaff assignee) {
        this.assignee = assignee;
        return this;
    }

    public TaskSituationSpec previousAssignee(CreatedStaff previousAssignee) {
        this.previousAssignee = previousAssignee;
        return this;
    }

    public TaskSituationSpec createdBy(CreatedStaff createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public TaskSituationSpec triagedBy(CreatedStaff desk, String via) {
        this.triagedBy = desk;
        this.triageVia = via;
        return this;
    }

    public TaskSituationSpec assignedBy(CreatedStaff assignedBy) {
        this.assignedBy = assignedBy;
        return this;
    }

    public TaskSituationSpec actedBy(CreatedStaff actedBy) {
        this.actedBy = actedBy;
        return this;
    }

    public TaskSituationSpec returnedBy(CreatedStaff returnedBy) {
        this.returnedBy = returnedBy;
        return this;
    }

    public TaskSituationSpec poolSource(String poolSource) {
        this.poolSource = poolSource;
        return this;
    }

    public TaskSituationSpec acceptedVia(AcceptedVia acceptedVia) {
        this.acceptedVia = acceptedVia;
        return this;
    }

    public TaskSituationSpec at(OffsetDateTime at) {
        this.at = at;
        return this;
    }

    public TaskSituationSpec createdAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public TaskSituationSpec triagedAt(OffsetDateTime triagedAt) {
        this.triagedAt = triagedAt;
        return this;
    }

    public TaskSituationSpec acceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
        return this;
    }

    public TaskSituationSpec startedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public TaskSituationSpec doneAt(OffsetDateTime doneAt) {
        this.doneAt = doneAt;
        return this;
    }

    public TaskSituationSpec reason(String reason) {
        this.reason = reason;
        return this;
    }

    public TaskSituationSpec handoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
        return this;
    }

    public TaskSituationSpec answer(String answer) {
        this.answer = answer;
        return this;
    }

    public TaskSituationSpec sopCheckedCount(Integer sopCheckedCount) {
        this.sopCheckedCount = sopCheckedCount;
        return this;
    }

    public TaskSituationSpec repairCategory(String repairCategory) {
        this.repairCategory = repairCategory;
        return this;
    }

    public TaskSituationSpec resolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
        return this;
    }
}
