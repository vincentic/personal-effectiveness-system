package com.example.pms;

import com.example.pms.support.Views;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v2 个人效能系统视图工具的纯单元测试，不依赖数据库与 Spring 容器。 */
class ViewsTest {

    @Test
    void labelsAndBadges() {
        assertEquals("高", Views.energyLabel("high"));
        assertEquals("工作 / 项目", Views.domainLabel("work"));
        assertEquals("受阻", Views.domainStatusLabel("blocked"));
        assertEquals("已完成", Views.bucketLabel("done"));
        assertEquals("调整", Views.worthLabel("adjust"));
        assertEquals("睡眠 / 体能 / 情绪", Views.moduleLabel("sleepenergy"));
        assertEquals("bg-danger", Views.domainStatusBadge("blocked"));
        assertEquals("bg-success", Views.bucketBadge("done"));
        assertEquals("bg-danger", Views.energyBadge("high"));
        assertEquals("unknown", Views.moduleLabel("unknown"));
    }

    @Test
    void blockProgressIsCalculatedOnRead() {
        assertEquals(0, Views.blockProgress(0, 0));
        assertEquals(50, Views.blockProgress(1, 2));
        assertEquals(33, Views.blockProgress(1, 3));
        assertEquals(100, Views.blockProgress(4, 4));
        assertEquals(67, Views.blockProgress(2, 3));
    }

    @Test
    void checklistDoneCountsAllThirteenFlags() {
        assertEquals(0, Views.checklistDone());
        assertEquals(3, Views.checklistDone(true, false, true, true));
        assertEquals(13, Views.checklistDone(
                true, true, true, true, true, true, true, true, true,
                true, true, true, true));
    }

    @Test
    void weekEndIsStartPlusSixDays() {
        assertEquals("2026-11-23", Views.weekEnd("2026-11-17"));
        assertEquals("", Views.weekEnd(""));
        assertEquals("", Views.weekEnd(null));
        assertEquals("", Views.weekEnd("not-a-date"));
    }

    @Test
    void avgScoreRoundsToOneDecimalAndSkipsNulls() {
        assertNull(Views.avgScore(null));
        assertNull(Views.avgScore(List.of()));
        assertNull(Views.avgScore(java.util.Arrays.asList(null, null)));
        assertEquals(3.0, Views.avgScore(List.of(3)));
        assertEquals(3.5, Views.avgScore(List.of(3, 4)));
        assertEquals(3.3, Views.avgScore(java.util.Arrays.asList(3, 3, 4, null)));
        assertEquals(4.0, Views.avgScore(List.of(4, 4, 4)));
    }

    @Test
    void dateMonthAndTimeParsing() {
        assertEquals("", Views.date(null));
        assertEquals("2026-11-17", Views.date(LocalDate.of(2026, 11, 17)));
        assertEquals("2026-11-17", Views.date(LocalDateTime.of(2026, 11, 17, 9, 30)));
        assertEquals(LocalDate.of(2026, 11, 17), Views.parseDate("2026-11-17"));
        assertNull(Views.parseDate(""));
        assertNull(Views.parseDate("  "));
        assertEquals("2026-11", Views.parseMonth("2026-11").toString());
        assertFalse(Views.bool("x"));
        assertTrue(Views.bool(1));
        assertFalse(Views.bool(0));
    }

    @Test
    void enumWhitelistAndEscaping() {
        assertTrue(Views.in("high", "low", "medium", "high"));
        assertFalse(Views.in("deleted", "work", "study", "body", "relations"));
        assertEquals("a&lt;b<br>c", Views.nl2br("a<b\nc"));
        assertEquals("中文…", Views.truncate("中文内容很长", 2));
    }
}
