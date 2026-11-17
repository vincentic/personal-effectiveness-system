package com.example.pms.support;

import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 个人效能系统视图工具：枚举中文标签/徽章、日期时间格式化与读时计算纯函数。
 * 计算函数集中在此，便于单元测试（checklist_done / block_progress / week_end / avg_score）。
 */
public final class Views {

    private Views() {
    }

    private static final Map<String, String> ENERGY = Map.of(
            "low", "低", "medium", "中", "high", "高");
    private static final Map<String, String> DOMAIN = Map.of(
            "work", "工作 / 项目", "study", "学习 / 技能",
            "body", "身体 / 精力", "relations", "关系 / 生活");
    private static final Map<String, String> DOMAIN_STATUS = Map.of(
            "smooth", "顺利", "blocked", "受阻", "paused", "暂停");
    private static final Map<String, String> BUCKET = Map.of(
            "todo", "待办", "doing", "进行中", "done", "已完成", "remove", "删除/委托/延后");
    private static final Map<String, String> WORTH = Map.of(
            "yes", "是", "adjust", "调整", "stop", "停止");
    private static final Map<String, String> MODULE = Map.of(
            "direction", "方向清晰度", "taskload", "任务负荷", "blockquality", "组块质量",
            "timebox", "时间盒现实度", "sleepenergy", "睡眠 / 体能 / 情绪", "reviewquality", "复盘质量");

    public static String energyLabel(Object code) { return ENERGY.getOrDefault(str(code), str(code)); }
    public static String domainLabel(Object code) { return DOMAIN.getOrDefault(str(code), str(code)); }
    public static String domainStatusLabel(Object code) { return DOMAIN_STATUS.getOrDefault(str(code), str(code)); }
    public static String bucketLabel(Object code) { return BUCKET.getOrDefault(str(code), str(code)); }
    public static String worthLabel(Object code) { return WORTH.getOrDefault(str(code), str(code)); }
    public static String moduleLabel(Object code) { return MODULE.getOrDefault(str(code), str(code)); }

    public static String domainStatusBadge(Object code) {
        return switch (str(code)) {
            case "smooth" -> "bg-success";
            case "blocked" -> "bg-danger";
            case "paused" -> "bg-secondary";
            default -> "bg-light text-dark";
        };
    }

    public static String energyBadge(Object code) {
        return switch (str(code)) {
            case "high" -> "bg-danger";
            case "medium" -> "bg-info text-dark";
            default -> "bg-secondary";
        };
    }

    public static String bucketBadge(Object code) {
        return switch (str(code)) {
            case "doing" -> "bg-primary";
            case "done" -> "bg-success";
            case "remove" -> "bg-dark";
            default -> "bg-secondary";
        };
    }

    public static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    public static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        return value instanceof Number n && n.intValue() != 0;
    }

    /** 表单日期字符串 → LocalDate；空串表示 NULL；非法格式抛 DateTimeParseException。 */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    /** 表单月份字符串 → YearMonth；非法格式抛 DateTimeParseException。 */
    public static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return YearMonth.parse(value.trim());
    }

    /** 枚举白名单校验。 */
    public static boolean in(String value, String... allowed) {
        if (value == null) return false;
        for (String a : allowed) {
            if (a.equals(value)) return true;
        }
        return false;
    }

    /** 兼容 MySQL 驱动返回的 LocalDateTime / Timestamp / Date / String，输出 yyyy-MM-dd。 */
    public static String date(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime dt) return dt.toLocalDate().toString();
        if (value instanceof LocalDate d) return d.toString();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate().toString();
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        String s = value.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** 兼容 Timestamp / LocalDateTime / String，输出 yyyy-MM-dd HH:mm:ss。 */
    public static String datetime(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString().replace('T', ' ');
        if (value instanceof LocalDateTime dt) return dt.toString().replace('T', ' ');
        String s = value.toString();
        return s.length() >= 19 ? s.substring(0, 19).replace('T', ' ') : s;
    }

    /** TIME 列 → HH:mm；兼容 java.sql.Time / LocalTime / String。 */
    public static String time(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Time t) return t.toLocalTime().toString();
        if (value instanceof LocalTime t) return t.toString();
        String s = value.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }

    public static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        if (value instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 先转义再替换换行，供 th:utext 安全输出。 */
    public static String nl2br(Object value) {
        if (value == null) return "";
        return HtmlUtils.htmlEscape(value.toString())
                .replace("\r\n", "\n")
                .replace("\n", "<br>");
    }

    // ------------------------------- 读时计算纯函数（供 repo / 测试使用）

    /** 时间盒完成进度（0–100），整数四舍五入；分母为 0 时返回 0。 */
    public static int blockProgress(int done, int total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    /** 13 项执行清单中已勾选的数量。 */
    public static int checklistDone(boolean... flags) {
        int n = 0;
        for (boolean f : flags) {
            if (f) n++;
        }
        return n;
    }

    /** 周结束日 = week_start + 6 天；无效日期返回空串。 */
    public static String weekEnd(String weekStart) {
        if (weekStart == null || weekStart.isBlank()) return "";
        try {
            return LocalDate.parse(weekStart.trim()).plusDays(6).toString();
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    /** 系统健康检查平均分（非空分数，1 位小数）；无分数返回 null。 */
    public static Double avgScore(List<Integer> scores) {
        if (scores == null) return null;
        double sum = 0;
        int count = 0;
        for (Integer score : scores) {
            if (score != null) {
                sum += score;
                count++;
            }
        }
        return count == 0 ? null : Math.round(sum / count * 10) / 10.0;
    }
}
