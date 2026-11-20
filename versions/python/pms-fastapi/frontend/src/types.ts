export interface User {
  id: number;
  username: string;
  display_name: string;
  is_staff: boolean;
}

export interface Block {
  seq: number;
  block_name: string;
  input_desc: string;
  key_action: string;
  deliverable: string;
  time_start: string | null;
  time_end: string | null;
  is_done: boolean;
}

export interface DailyInput {
  plan_date: string;
  sleep_hours: number | null;
  energy_level: "low" | "medium" | "high";
  mit: string;
  pre_know_output: boolean;
  pre_materials_ready: boolean;
  pre_phone_handled: boolean;
  during_one_block: boolean;
  during_shrink_step: boolean;
  during_record_distraction: boolean;
  post_visible_output: boolean;
  post_recall: boolean;
  post_next_step: boolean;
  life_water_food: boolean;
  life_walk_exercise: boolean;
  life_clean_tidy: boolean;
  life_social_outdoor: boolean;
  review_accomplished: string;
  review_resistance: string;
  review_tomorrow_start: string;
  review_gratitude: string;
  blocks: Block[];
}

export interface DailyRecord extends DailyInput {
  id: number;
  blocks_total: number;
  blocks_done: number;
  block_progress: number;
  checklist_total: number;
  checklist_done: number;
}

export interface Domain {
  domain: "work" | "study" | "body" | "relations";
  facts: string;
  status: "smooth" | "blocked" | "paused";
}

export interface Flow {
  bucket: "todo" | "doing" | "done" | "remove";
  content: string;
  seq: number;
}

export interface WeeklyInput {
  week_start: string;
  next_mit_1: string;
  next_mit_2: string;
  next_mit_3: string;
  deep_work_time: string;
  prep_env_materials: string;
  if_condition: string;
  then_action: string;
  q_skilled_block: boolean;
  q_low_value_time: boolean;
  q_estimate_gap: boolean;
  q_rest_connection: boolean;
  q_next_adjust: boolean;
  domains: Domain[];
  flows: Flow[];
}

export interface WeeklyRecord extends WeeklyInput {
  id: number;
  week_end: string;
}

export interface HealthCheck {
  module:
    | "direction"
    | "taskload"
    | "blockquality"
    | "timebox"
    | "sleepenergy"
    | "reviewquality";
  score: number | null;
  needed_change: string;
}

export interface MonthlyInput {
  month: string;
  top_achievement: string;
  worth_continue: "yes" | "adjust" | "stop";
  worth_reason: string;
  most_valuable_input: string;
  biggest_opportunity_cost: string;
  keep_item: string;
  cut_item: string;
  experiment_item: string;
  q_skill_portfolio: boolean;
  q_career_alignment: boolean;
  q_life_autonomy: boolean;
  q_recovery_relations: boolean;
  q_longterm_decision: boolean;
  health_checks: HealthCheck[];
}

export interface MonthlyRecord extends MonthlyInput {
  id: number;
  avg_score: number | null;
}

export interface Dashboard {
  daily_count: number;
  weekly_count: number;
  monthly_count: number;
  today_plan: DailyRecord | null;
  week_review: WeeklyRecord | null;
  month_review: MonthlyRecord | null;
  recent_daily: DailyRecord[];
  recent_weekly: WeeklyRecord[];
  recent_monthly: MonthlyRecord[];
  block_total: number;
  block_done: number;
}
