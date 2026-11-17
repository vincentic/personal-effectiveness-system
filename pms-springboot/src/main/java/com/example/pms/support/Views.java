package com.example.pms.support;

import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 把数据库行加工成模板可直接渲染的视图模型（中文标签、徽章颜色、进度、日期）。 */
public final class Views {

    private Views() {
    }

    private static final Map<String, String> PROJECT_STATUS = Map.of(
            "active", "进行中", "paused", "已暂停", "done", "已完成");
    private static final Map<String, String> TASK_STATUS = Map.of(
            "todo", "待处理", "doing", "进行中", "done", "已完成");
    private static final Map<String, String> PRIORITY = Map.of(
            "low", "低", "medium", "中", "high", "高");

    public static String projectStatusLabel(Object code) {
        return PROJECT_STATUS.getOrDefault(str(code), str(code));
    }

    public static String taskStatusLabel(Object code) {
        return TASK_STATUS.getOrDefault(str(code), str(code));
    }

    public static String priorityLabel(Object code) {
        return PRIORITY.getOrDefault(str(code), str(code));
    }

    public static String projectStatusBadge(Object code) {
        return switch (str(code)) {
            case "active" -> "bg-primary";
            case "paused" -> "bg-warning text-dark";
            case "done" -> "bg-success";
            default -> "bg-secondary";
        };
    }

    public static String priorityBadge(Object code) {
        return switch (str(code)) {
            case "high" -> "bg-danger";
            case "medium" -> "bg-info text-dark";
            default -> "bg-secondary";
        };
    }

    public static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    /** 表单日期字符串 → LocalDate，空串表示 NULL。 */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    /** 枚举白名单校验，防止表单提交非法状态值。 */
    public static boolean in(String value, String... allowed) {
        for (String a : allowed) {
            if (a.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 兼容 MySQL 驱动返回的 LocalDateTime / Timestamp / Date / String。 */
    public static String date(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dt) {
            return dt.toLocalDate().toString();
        }
        if (value instanceof LocalDate d) {
            return d.toString();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        String s = value.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    public static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    public static int progress(Object done, Object total) {
        long t = number(total);
        return t == 0 ? 0 : (int) Math.round(number(done) * 100.0 / t);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 先转义再替换换行，供 th:utext 安全输出。 */
    public static String nl2br(Object value) {
        if (value == null) {
            return "";
        }
        return HtmlUtils.htmlEscape(value.toString())
                .replace("\r\n", "\n")
                .replace("\n", "<br>");
    }

    public static Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> v = new LinkedHashMap<>(row);
        v.put("ownerName", str(row.get("owner_name")));
        v.put("statusLabel", projectStatusLabel(row.get("status")));
        v.put("statusBadge", projectStatusBadge(row.get("status")));
        v.put("taskCount", number(row.get("task_count")));
        v.put("doneTaskCount", number(row.get("done_task_count")));
        v.put("progress", progress(row.get("done_task_count"), row.get("task_count")));
        v.put("createdDate", date(row.get("created_at")));
        v.put("startDate", date(row.get("start_date")));
        v.put("endDate", date(row.get("end_date")));
        v.put("descriptionHtml", nl2br(row.get("description")));
        return v;
    }

    public static Map<String, Object> task(Map<String, Object> row) {
        Map<String, Object> v = new LinkedHashMap<>(row);
        v.put("assigneeName", str(row.get("assignee_name")));
        v.put("statusLabel", taskStatusLabel(row.get("status")));
        v.put("priorityLabel", priorityLabel(row.get("priority")));
        v.put("priorityBadge", priorityBadge(row.get("priority")));
        v.put("dueDate", date(row.get("due_date")));
        v.put("descriptionShort", truncate(str(row.get("description")), 50));
        return v;
    }
}
