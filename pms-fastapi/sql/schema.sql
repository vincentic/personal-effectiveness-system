-- =============================================================
-- PMS 共享数据库结构（Spring Boot 版 / Gin 版共用同一套表）
-- MySQL 8.0+ / utf8mb4；全部语句幂等，应用启动时会自动执行。
-- 与原 Django 版相互独立：原版使用 auth_user / projects_project 等表，
-- 本套使用 users / projects / project_members / tasks，可在同一个库中并存。
-- 建库语句见各版本 README：
--   CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(150) NOT NULL,
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt($2a/$2b)，两版通用',
  display_name  VARCHAR(150) NOT NULL DEFAULT '',
  is_staff      TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description TEXT,
  status      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active / paused / done',
  start_date  DATE         NULL,
  end_date    DATE         NULL,
  owner_id    BIGINT       NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_projects_owner (owner_id),
  KEY idx_projects_status (status),
  CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_members (
  project_id BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  PRIMARY KEY (project_id, user_id),
  KEY idx_pm_user (user_id),
  CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_pm_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tasks (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  project_id   BIGINT       NOT NULL,
  title        VARCHAR(200) NOT NULL,
  description  TEXT,
  assignee_id  BIGINT       NULL,
  status       VARCHAR(20)  NOT NULL DEFAULT 'todo'    COMMENT 'todo / doing / done',
  priority     VARCHAR(20)  NOT NULL DEFAULT 'medium'  COMMENT 'low / medium / high',
  due_date     DATE         NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tasks_project (project_id),
  KEY idx_tasks_assignee (assignee_id),
  KEY idx_tasks_status (status),
  CONSTRAINT fk_tasks_project  FOREIGN KEY (project_id)  REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
