package com.readygym.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Conservative authored rules; no provider or caller supplied prescriptions. */
final class Planner {
    static final String[] IDS = {"rg-v1-bodyweight-squat", "rg-v1-knee-push-up", "rg-v1-glute-bridge", "rg-v1-dead-bug", "rg-v1-dumbbell-row", "rg-v1-barbell-row"};
    static ObjectNode generate(JsonNode profile) {
        return generate(profile,Set.of());
    }
    static ObjectNode generate(JsonNode profile, Set<Integer> unavailable) {
        var j = BusinessServer.JSON;
        int frequency = profile.get("days_per_week").intValue(), minutes = profile.get("minutes_per_session").intValue();
        var plan = j.createObjectNode().put("id", UUID.randomUUID().toString()).put("revision",1).put("status","draft").putNull("based_on_revision")
            .put("title","一周基础抗阻计划").put("days_per_week",frequency).put("minutes_per_session",minutes)
            .put("source","agent_rule_tool").put("review_status","not_professionally_reviewed");
        plan.set("equipment",profile.get("equipment").deepCopy());
        var training = schedule(frequency,unavailable);
        var days = plan.putArray("training_days"); training.forEach(days::add);
        var blocked = plan.putArray("unavailable_days"); unavailable.stream().sorted().forEach(blocked::add); plan.putArray("changes");
        Set<String> equipment = BusinessServer.equipment(profile.get("equipment"));
        List<String> selected = new ArrayList<>(List.of(IDS[0], IDS[1], IDS[2], IDS[3]));
        if (equipment.contains("dumbbell")) selected.add(IDS[4]); else if (equipment.contains("barbell")) selected.add(IDS[5]);
        boolean returning = profile.get("experience").textValue().equals("returning");
        int initialSets = returning ? 1 : profile.get("goal").textValue().equals("muscle") && minutes >= 30 ? 3 : 2;
        var week = plan.putArray("week");
        int ordinal = 0;
        for (int day = 1; day <= 7; day++) {
            var entry = week.addObject().put("day_of_week",day).put("kind",training.contains(day) ? "training" : "rest");
            var exercises = entry.putArray("exercises");
            if (!training.contains(day)) continue;
            List<String> actions = selected;
            if (frequency==4) {
                boolean lower = (ordinal++ % 2)==0;
                actions = selected.stream().filter(id -> id.equals(IDS[3]) || (lower ? id.equals(IDS[0]) || id.equals(IDS[2]) : id.equals(IDS[1]) || id.equals(IDS[4]) || id.equals(IDS[5]))).toList();
            }
            int sets = initialSets, maxReps = returning ? 10 : 12;
            while (estimate(actions,sets,maxReps) > minutes*60 && sets>1) sets--;
            int slot = 0;
            for (var id : actions) {
                var x = exercises.addObject().put("slot_id","d"+day+"-s"+(++slot)).put("exercise_id",id).put("sets",sets).put("rest_seconds",60).putNull("load_kg");
                if (id.equals(IDS[3])) x.putNull("reps_min").putNull("reps_max").put("seconds",20);
                else x.put("reps_min",8).put("reps_max",maxReps);
            }
            entry.put("budget_seconds",minutes*60).put("warm_up_seconds",180).put("cool_down_seconds",120).put("transition_seconds_per_exercise",30)
                .put("estimated_seconds",estimate(actions,sets,maxReps));
        }
        var notices = plan.putArray("notices").add("未经专业审核，不是医疗处方或安全认证；不推算负重，不追求力竭")
            .add("时长包含热身、冷身与动作过渡；仅为估算，不要为赶时间跳过休息")
            .add("徒手动作为器械补充，不表示改写用户器械选择");
        if (selected.size()==4) notices.add("当前器械/动作库没有合适拉类动作，背部训练覆盖不完整；不编造替代动作");
        if (equipment.contains("machine")) notices.add("未确认具体固定器械库存，不默认任何机器存在");
        if (frequency==4) notices.add("四天采用上下肢交替；相邻训练日重点不同，仍应观察恢复");
        return plan;
    }
    private static List<Integer> schedule(int frequency,Set<Integer> unavailable) {
        List<Integer> preferred = frequency==2 ? List.of(1,4) : frequency==4 ? List.of(1,2,4,5) : List.of(1,3,5);
        if (preferred.stream().noneMatch(unavailable::contains)) return preferred;
        List<Integer> best=null;
        int bestScore=Integer.MIN_VALUE;
        for (int mask=0;mask<128;mask++) {
            if (Integer.bitCount(mask)!=frequency) continue;
            List<Integer> candidate=new ArrayList<>();
            for (int day=1;day<=7;day++) if ((mask & (1<<(day-1)))!=0) candidate.add(day);
            if (candidate.stream().anyMatch(unavailable::contains)) continue;
            int minimum=7, sumSquares=0;
            for (int i=0;i<candidate.size();i++) { int gap=(candidate.get((i+1)%candidate.size())-candidate.get(i)+7)%7; minimum=Math.min(minimum,gap); sumSquares+=gap*gap; }
            if (frequency<4 && minimum<2) continue;
            int score=minimum*100-sumSquares;
            if (score>bestScore) { bestScore=score; best=candidate; }
        }
        if (best==null) throw new BusinessServer.Problem(409,"unsupported_schedule","不可用日期无法容纳保守排期；请调整明确约束，不强行密集安排");
        return best;
    }
    private static int estimate(List<String> ids, int sets, int maxReps) {
        int seconds = 300;
        for (var id : ids) seconds += sets * (id.equals(IDS[3]) ? 20 : maxReps*4) + (sets-1)*60 + 30;
        return seconds;
    }
}
