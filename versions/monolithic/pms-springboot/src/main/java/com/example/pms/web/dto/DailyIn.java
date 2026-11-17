package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** 每日执行清单写入体，字段 snake_case 与 schema 一致。 */
public record DailyIn(
        @JsonProperty("plan_date") String planDate,
        @JsonProperty("sleep_hours") BigDecimal sleepHours,
        @JsonProperty("energy_level") String energyLevel,
        @JsonProperty("mit") String mit,
        @JsonProperty("pre_know_output") boolean preKnowOutput,
        @JsonProperty("pre_materials_ready") boolean preMaterialsReady,
        @JsonProperty("pre_phone_handled") boolean prePhoneHandled,
        @JsonProperty("during_one_block") boolean duringOneBlock,
        @JsonProperty("during_shrink_step") boolean duringShrinkStep,
        @JsonProperty("during_record_distraction") boolean duringRecordDistraction,
        @JsonProperty("post_visible_output") boolean postVisibleOutput,
        @JsonProperty("post_recall") boolean postRecall,
        @JsonProperty("post_next_step") boolean postNextStep,
        @JsonProperty("life_water_food") boolean lifeWaterFood,
        @JsonProperty("life_walk_exercise") boolean lifeWalkExercise,
        @JsonProperty("life_clean_tidy") boolean lifeCleanTidy,
        @JsonProperty("life_social_outdoor") boolean lifeSocialOutdoor,
        @JsonProperty("review_accomplished") String reviewAccomplished,
        @JsonProperty("review_resistance") String reviewResistance,
        @JsonProperty("review_tomorrow_start") String reviewTomorrowStart,
        @JsonProperty("review_gratitude") String reviewGratitude,
        @JsonProperty("blocks") List<BlockIn> blocks
) {
    public List<BlockIn> safeBlocks() { return blocks == null ? List.of() : blocks; }
}
