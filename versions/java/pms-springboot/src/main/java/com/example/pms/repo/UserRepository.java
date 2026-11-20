package com.example.pms.repo;

import com.example.pms.domain.User;
import com.example.pms.support.Views;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<User> mapper = (rs, n) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getInt("is_staff") != 0,
            Views.date(rs.getTimestamp("created_at")));

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        return jdbc.query("SELECT * FROM users WHERE username = ?", mapper, username).stream().findFirst();
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", mapper, id).stream().findFirst();
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY id", mapper);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    public boolean existsByUsername(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username) > 0;
    }

    public void create(String username, String passwordHash, String displayName, boolean staff) {
        jdbc.update("INSERT INTO users (username, password_hash, display_name, is_staff) VALUES (?, ?, ?, ?)",
                username, passwordHash, displayName == null ? "" : displayName, staff ? 1 : 0);
    }

}
