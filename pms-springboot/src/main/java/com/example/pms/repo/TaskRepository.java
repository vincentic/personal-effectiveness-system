package com.example.pms.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TaskRepository {

    private static final String SELECT = """
            SELECT t.*, p.name AS project_name, p.owner_id AS owner_id,
                   u.username AS assignee_name
            FROM tasks t JOIN projects p ON p.id = t.project_id
            LEFT JOIN users u ON u.id = t.assignee_id
            """;

    /** 看板顺序：状态 todo→doing→done，优先级高→低，截止日期早→晚。 */
    private static final String ORDER = """
            ORDER BY FIELD(t.status, 'todo', 'doing', 'done'),
                     FIELD(t.priority, 'high', 'medium', 'low'),
                     t.due_date IS NULL, t.due_date, t.id
            """;

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findByProject(long projectId) {
        return jdbc.queryForList(SELECT + " WHERE t.project_id = ? " + ORDER, projectId);
    }

    public Optional<Map<String, Object>> findById(long id) {
        return jdbc.queryForList(SELECT + " WHERE t.id = ?", id).stream().findFirst();
    }

    public List<Map<String, Object>> findByAssignee(long userId, int limit) {
        return jdbc.queryForList(SELECT + " WHERE t.assignee_id = ? AND t.status <> 'done' "
                + ORDER + " LIMIT ?", userId, limit);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT status, COUNT(*) AS c FROM tasks GROUP BY status")) {
            result.put(String.valueOf(row.get("status")), ((Number) row.get("c")).longValue());
        }
        return result;
    }

    public long create(long projectId, String title, String description, Long assigneeId,
                       String status, String priority, LocalDate dueDate) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, due_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, title);
            ps.setString(3, description);
            if (assigneeId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, assigneeId);
            }
            ps.setString(5, status);
            ps.setString(6, priority);
            ps.setDate(7, toSqlDate(dueDate));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, String title, String description, Long assigneeId,
                       String status, String priority, LocalDate dueDate) {
        jdbc.update("""
                UPDATE tasks SET title = ?, description = ?, assignee_id = ?, status = ?, priority = ?,
                                 due_date = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, title, description, assigneeId, status, priority, toSqlDate(dueDate), id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM tasks WHERE id = ?", id);
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
