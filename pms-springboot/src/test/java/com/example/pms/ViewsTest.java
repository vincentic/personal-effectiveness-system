package com.example.pms;

import com.example.pms.support.Views;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 视图模型派生的纯单元测试，不依赖数据库与 Spring 容器。 */
class ViewsTest {

    @Test
    void labelsAndBadges() {
        assertEquals("进行中", Views.projectStatusLabel("active"));
        assertEquals("已完成", Views.taskStatusLabel("done"));
        assertEquals("高", Views.priorityLabel("high"));
        assertEquals("bg-primary", Views.projectStatusBadge("active"));
        assertEquals("bg-danger", Views.priorityBadge("high"));
        assertEquals("bg-secondary", Views.projectStatusBadge("unknown"));
    }

    @Test
    void progressIsCalculatedOnRead() {
        assertEquals(0, Views.progress(0, 0));
        assertEquals(50, Views.progress(1L, 2L));
        assertEquals(33, Views.progress(1L, 3L));
        assertEquals(100, Views.progress(4L, 4L));
    }

    @Test
    void dateAndFormParsing() {
        assertEquals("", Views.date(null));
        assertEquals("2026-11-17", Views.date(LocalDate.of(2026, 11, 17)));
        assertEquals("2026-11-17", Views.date(LocalDateTime.of(2026, 11, 17, 9, 30)));
        assertEquals(LocalDate.of(2026, 11, 17), Views.parseDate("2026-11-17"));
        assertNull(Views.parseDate(""));
        assertNull(Views.parseDate("  "));
    }

    @Test
    void enumWhitelistAndEscaping() {
        assertTrue(Views.in("done", "active", "paused", "done"));
        assertFalse(Views.in("deleted", "active", "paused", "done"));
        assertEquals("a&lt;b<br>c", Views.nl2br("a<b\nc"));
        assertEquals("中文…", Views.truncate("中文内容很长", 2));
    }
}
