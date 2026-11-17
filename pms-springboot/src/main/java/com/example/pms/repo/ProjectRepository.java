package com.example.pms.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProjectRepository {

    /** 进度读时计算，不落库，对应 Django 的 Project.progress。 */
    private static final String SELECT = """
            SELECT p.*, u.username AS owner_name,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id) AS task_count,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.status = 'done') AS done_task_count
            FROM projects p JOIN users u ON u.id = p.owner_id
            """;

    private final JdbcTemplate jdbc;

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findAll(String status, String keyword) {
        String sql = SELECT + """
                WHERE (? = '' OR p.status = ?) AND (? = '' OR p.name LIKE CONCAT('%', ?, '%'))
                ORDER BY p.created_at DESC, p.id DESC
                """;
        return jdbc.queryForList(sql, status, status, keyword, keyword);
    }

    public Optional<Map<String, Object>> findById(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + " WHERE p.id = ?", id);
        return rows.stream().findFirst();
    }

    public List<Map<String, Object>> findByOwner(long ownerId, int limit) {
        return jdbc.queryForList(SELECT + " WHERE p.owner_id = ? ORDER BY p.created_at DESC, p.id DESC LIMIT ?",
                ownerId, limit);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM projects", Long.class);
    }

    @Transactional
    public long create(String name, String description, String status, LocalDate startDate,
                       LocalDate endDate, long ownerId, List<Long> memberIds) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO projects (name, description, status, start_date, end_date, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, status);
            ps.setDate(4, toSqlDate(startDate));
            ps.setDate(5, toSqlDate(endDate));
            ps.setLong(6, ownerId);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        setMembers(id, memberIds);
        return id;
    }

    @Transactional
    public void update(long id, String name, String description, String status, LocalDate startDate,
                       LocalDate endDate, List<Long> memberIds) {
        jdbc.update("""
                UPDATE projects SET name = ?, description = ?, status = ?, start_date = ?, end_date = ?,
                                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, name, description, status, toSqlDate(startDate), toSqlDate(endDate), id);
        setMembers(id, memberIds);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM projects WHERE id = ?", id);
    }

    public List<Long> memberIds(long projectId) {
        return jdbc.queryForList("SELECT user_id FROM project_members WHERE project_id = ? ORDER BY user_id",
                Long.class, projectId);
    }

    public List<String> memberNames(long projectId) {
        return jdbc.queryForList("""
                SELECT u.username FROM project_members pm JOIN users u ON u.id = pm.user_id
                WHERE pm.project_id = ? ORDER BY u.id
                """, String.class, projectId);
    }

    @Transactional
    public void setMembers(long projectId, List<Long> memberIds) {
        jdbc.update("DELETE FROM project_members WHERE project_id = ?", projectId);
        List<Long> ids = memberIds == null ? new ArrayList<>() : memberIds.stream().distinct().toList();
        for (Long userId : ids) {
            if (userId != null) {
                jdbc.update("INSERT IGNORE INTO project_members (project_id, user_id) VALUES (?, ?)",
                        projectId, userId);
            }
        }
    }

    public Map<String, Object> emptyRow() {
        return new LinkedHashMap<>();
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
