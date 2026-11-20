-- =============================================================
-- 个人效能系统 · 共享数据库结构（v2）
-- MySQL 8.0+ / utf8mb4；全部语句幂等，应用启动时会自动执行。
--
-- 依据《个人效能系统 · 可手写打印 版本 1.0》三张清单设计：
--   第 1 页 每日执行清单  -> daily_plans + time_blocks
--   第 2 页 每周复盘清单  -> weekly_reviews + weekly_domain_reviews + weekly_task_flows
--   第 3 页 每月重构清单  -> monthly_reviews + system_health_checks
--
-- 设计原则：
--   * 主表一人一天 / 一人一周 / 一人一月各一条（唯一键约束）；
--   * 可重复的清单项（时间盒组块、领域回顾、任务流、健康检查）拆成子表；
--   * 一次性的勾选框与问答保留为字段，便于快速表单渲染与统计。
-- 建库语句：
--   CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(150) NOT NULL,
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt($2a/$2b)，各版通用',
  display_name  VARCHAR(150) NOT NULL DEFAULT '',
  is_staff      TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -------------------------------------------------------------
-- 第 1 页：每日执行清单
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_plans (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  user_id       BIGINT        NOT NULL,
  plan_date     DATE          NOT NULL COMMENT '日期',
  sleep_hours   DECIMAL(4,1)  NULL     COMMENT '睡眠小时',
  energy_level  VARCHAR(10)   NOT NULL DEFAULT 'medium' COMMENT '精力 low/medium/high',
  mit           TEXT          NULL     COMMENT '今天最重要的一个成果（MIT）',

  -- 执行前后检查：启动前 / 执行中 / 结束后 各 3 项
  pre_know_output        TINYINT NOT NULL DEFAULT 0 COMMENT '启动前：我知道本轮要交付什么',
  pre_materials_ready    TINYINT NOT NULL DEFAULT 0 COMMENT '启动前：材料已准备',
  pre_phone_handled      TINYINT NOT NULL DEFAULT 0 COMMENT '启动前：手机/通知已处理',
  during_one_block       TINYINT NOT NULL DEFAULT 0 COMMENT '执行中：一次只做一个组块',
  during_shrink_step     TINYINT NOT NULL DEFAULT 0 COMMENT '执行中：卡住时先缩小下一步',
  during_record_distraction TINYINT NOT NULL DEFAULT 0 COMMENT '执行中：必要时记录干扰',
  post_visible_output    TINYINT NOT NULL DEFAULT 0 COMMENT '结束后：有可见产出',
  post_recall            TINYINT NOT NULL DEFAULT 0 COMMENT '结束后：做了主动回忆/复述',
  post_next_step         TINYINT NOT NULL DEFAULT 0 COMMENT '结束后：标记下一步',

  -- 生活维护：不追求满分，保持基本盘
  life_water_food        TINYINT NOT NULL DEFAULT 0 COMMENT '饮水/正常饮食',
  life_walk_exercise     TINYINT NOT NULL DEFAULT 0 COMMENT '走路/运动',
  life_clean_tidy        TINYINT NOT NULL DEFAULT 0 COMMENT '清洁/整理',
  life_social_outdoor    TINYINT NOT NULL DEFAULT 0 COMMENT '真实交流/户外',

  -- 晚间 5 分钟复盘
  review_accomplished    TEXT NULL COMMENT '今天真正完成了什么',
  review_resistance      TEXT NULL COMMENT '今天最大的阻力是什么',
  review_tomorrow_start  TEXT NULL COMMENT '明天最小的启动动作',
  review_gratitude       TEXT NULL COMMENT '今天值得感谢/享受的一件事',

  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_user_date (user_id, plan_date),
  KEY idx_daily_date (plan_date),
  CONSTRAINT fk_daily_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS time_blocks (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  daily_plan_id BIGINT       NOT NULL,
  seq           INT          NOT NULL DEFAULT 0 COMMENT '组块序号 1..n',
  block_name    VARCHAR(200) NOT NULL COMMENT '组块/任务',
  input_desc    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '输入',
  key_action    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '关键动作',
  deliverable   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '可验收输出',
  time_start    TIME         NULL COMMENT '时间盒开始',
  time_end      TIME         NULL COMMENT '时间盒结束',
  is_done       TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tb_plan (daily_plan_id),
  CONSTRAINT fk_tb_plan FOREIGN KEY (daily_plan_id) REFERENCES daily_plans (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -------------------------------------------------------------
-- 第 2 页：每周复盘清单
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weekly_reviews (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  week_start      DATE         NOT NULL COMMENT '本周起始日（周一）',

  -- 下周设计：少而明确
  next_mit_1      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '下周最重要成果 1',
  next_mit_2      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '下周最重要成果 2',
  next_mit_3      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '下周最重要成果 3',
  deep_work_time  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '要保护的深度时间（日期/时段）',
  prep_env_materials TEXT      NULL COMMENT '需要提前准备的环境/材料',
  if_condition    VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'If-Then 计划：如果……',
  then_action     VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'If-Then 计划：那么我就……',

  -- 复盘问题
  q_skilled_block   TINYINT NOT NULL DEFAULT 0 COMMENT '哪个组块本周变得更熟练',
  q_low_value_time  TINYINT NOT NULL DEFAULT 0 COMMENT '哪件事耗时很多但价值很低',
  q_estimate_gap    TINYINT NOT NULL DEFAULT 0 COMMENT '哪个时间盒估算偏差最大？为什么',
  q_rest_connection TINYINT NOT NULL DEFAULT 0 COMMENT '本周有没有休息、快乐和真实连接',
  q_next_adjust     TINYINT NOT NULL DEFAULT 0 COMMENT '下周要继续、调整、停止什么',

  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weekly_user_week (user_id, week_start),
  KEY idx_weekly_week (week_start),
  CONSTRAINT fk_weekly_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS weekly_domain_reviews (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  weekly_review_id  BIGINT      NOT NULL,
  domain            VARCHAR(20) NOT NULL COMMENT 'work/study/body/relations',
  facts             TEXT        NULL COMMENT '本周事实/产出',
  status            VARCHAR(10) NOT NULL DEFAULT 'smooth' COMMENT 'smooth/blocked/paused',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wdr_review_domain (weekly_review_id, domain),
  CONSTRAINT fk_wdr_review FOREIGN KEY (weekly_review_id) REFERENCES weekly_reviews (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS weekly_task_flows (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  weekly_review_id  BIGINT       NOT NULL,
  bucket            VARCHAR(20)  NOT NULL COMMENT 'todo/doing/done/remove（待办/进行中/已完成/删除委托延后）',
  content           VARCHAR(255) NOT NULL DEFAULT '',
  seq               INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_wtf_review (weekly_review_id),
  KEY idx_wtf_bucket (weekly_review_id, bucket),
  CONSTRAINT fk_wtf_review FOREIGN KEY (weekly_review_id) REFERENCES weekly_reviews (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -------------------------------------------------------------
-- 第 3 页：每月重构清单
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS monthly_reviews (
  id                      BIGINT      NOT NULL AUTO_INCREMENT,
  user_id                 BIGINT      NOT NULL,
  month                   CHAR(7)     NOT NULL COMMENT 'YYYY-MM',

  -- 目标与价值检查
  top_achievement         TEXT        NULL COMMENT '本月最重要的成果',
  worth_continue          VARCHAR(10) NOT NULL DEFAULT 'yes' COMMENT '是否仍值得投入 yes/adjust/stop',
  worth_reason            VARCHAR(255) NOT NULL DEFAULT '' COMMENT '原因',
  most_valuable_input     TEXT        NULL COMMENT '本月最有价值的投入',
  biggest_opportunity_cost TEXT       NULL COMMENT '本月最大的机会成本',

  -- 下月只做三项系统改进
  keep_item               TEXT        NULL COMMENT '保留（有效的）',
  cut_item                TEXT        NULL COMMENT '删减（低价值的）',
  experiment_item         TEXT        NULL COMMENT '实验（新的最小改变）',

  -- 季度方向校准（可选）
  q_skill_portfolio       TINYINT NOT NULL DEFAULT 0 COMMENT '技能是否形成可展示作品',
  q_career_alignment      TINYINT NOT NULL DEFAULT 0 COMMENT '工作是否更接近长期方向',
  q_life_autonomy         TINYINT NOT NULL DEFAULT 0 COMMENT '生活是否更有自主性',
  q_recovery_relations    TINYINT NOT NULL DEFAULT 0 COMMENT '是否有足够的恢复与关系投入',
  q_longterm_decision     TINYINT NOT NULL DEFAULT 0 COMMENT '是否应该继续、转向或停止某项长期投资',

  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monthly_user_month (user_id, month),
  KEY idx_monthly_month (month),
  CONSTRAINT fk_monthly_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_health_checks (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  monthly_review_id   BIGINT       NOT NULL,
  module              VARCHAR(30)  NOT NULL COMMENT 'direction/taskload/blockquality/timebox/sleepenergy/reviewquality',
  score               TINYINT      NULL COMMENT '自评 1-5',
  needed_change       TEXT         NULL COMMENT '需要的改变',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shc_review_module (monthly_review_id, module),
  CONSTRAINT fk_shc_review FOREIGN KEY (monthly_review_id) REFERENCES monthly_reviews (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
