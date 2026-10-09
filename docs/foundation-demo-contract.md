# Agent Foundation Demo v0.4.0 — shared implementation contract

Basis: `docs/agent-as-foundation.md` at private repository commit `ef6be2bc87b31e757cd90e6b9b25806673a2d2a1`. This is a narrow runnable Android demo of its central loop, not all future capabilities.

## Acceptance path

Explicit data/provider consent → real DeepSeek conversational intake → validated profile summary → explicit profile confirmation → Java persists profile → bounded real DeepSeek tools (`check_entitlement`, `get_profile`, `search_exercises`, `propose_plan`) → inline plan draft → explicit confirm → Java persists version1 → natural language schedule/equipment change → `propose_revision` → inline before/after differences → explicit confirm → Java persists version2, keeps version1 until then. Exercise rows open exact existing GIF/detail dialog. Risk interrupts before provider/quota; no revision/confirmation can bypass retained risk.

Demo plan templates remain conservative self-authored, NOT professionally reviewed. AI chooses/intends tools, Java deterministically validates/schedules from six existing stable exercise IDs. Do not call this a professionally approved prescription or a free-form AI plan. Model failure may return clearly labelled `rule_fallback` only for a confirmed complete safe profile. Failure during an unclear modification must not adopt inferred parameters or alter active version.

## Ownership / no overwrite

- Business worker: ONLY `business-java/` and `docs/foundation-business.md`; no Android/Python edits; no root git commit/push.
- Android worker: ONLY new foundation Android Kotlin/test files, `CoachClient.kt`, `CoachChatScreen.kt`, top-level `ReadyGymApp` routing in `TrainingPlanScreen.kt`, Android build version; no core/planner/media edits. Sole Android Gradle build owner until first-green handoff.
- Parent: `agent-python/` integration/real provider, deployment, final integration/tests/review/delivery. Secret configuration OUTSIDE repository.

## Java internal HTTP (loopback only)

Internal auth `X-Ready-Gym-Internal` random server-side secret, never APK/provider/tool arguments/logs. JSON strict bounded bodies, trusted errors not echoed bodies. PostgreSQL JDBC; Java is the only business writer. Separate schema and unprivileged local role, no public DB. For fast tests use isolated in-memory JDBC database if needed, real PostgreSQL persistence also must be exercised before done.

All POST payloads include `session_id` (ASCII `[A-Za-z0-9_-]{1,128}`). Routes:

- `POST /internal/profile/confirm`: `{session_id,profile_revision:int,profile:{...existing validated intake fields...},confirmed:true}`. Check complete supported adult profile, `current_discomfort=none`, `medical_restrictions=none`, supported experience; identical retry idempotent; revision change invalidates pending proposals. Persist ONLY on this route.
- `POST /internal/safety`: `{session_id,route:"stop_and_seek_care"|"professional_review"}`. Monotonic protective latch, may persist automatically as safety evidence, not a training action. Stronger route never lowered by profile/plan/new none or restarts. Snapshot shows guard.
- `POST /internal/tools/get_profile`: `{session_id}` returns confirmed profile/revision, active/pending plan, safety route. Do not leak secrets.
- `POST /internal/tools/check_entitlement`: `{session_id}` returns bounded demo eligibility (no real membership/payment claim).
- `POST /internal/tools/search_exercises`: `{session_id,muscle?:"quadriceps"|"glutes"|"chest"|"back"|"core",equipment?:[bodyweight,dumbbell,barbell,machine]}` returns eligible existing six actions only; IDs exact; no upstream incorrect instructions. Equipment filter may only narrow user's confirmed availability.
- `POST /internal/tools/propose_plan`: `{session_id}` uses confirmed profile. No caller-defined sets/reps/loads. Return plan JSON below; memory-only pending proposals do not write business rows. Repeated call can replace pending, not active.
- `POST /internal/tools/propose_revision`: `{session_id,expected_revision:int,days_per_week?:2|3|4,minutes_per_session?:20|30|45|60,unavailable_days?:[1..7],equipment?:[bodyweight,dumbbell,barbell,machine]}` requires active plan/revision and at least one explicit changed value. Changed schedule must accommodate days, else rejected (no invention). Java validates values and preserves active. Only server-proven parameters forwarded from user's text, model can't redefine safety/profile/sets.
- `POST /internal/plan/confirm`: `{session_id,draft_id,expected_revision,confirmed:true}` verifies exact current pending id/revision, latest safety/profile version; persist current version & immutable history transactionally, then clear pending. Identical retry idempotent, stale differing confirmation rejected.
- `POST /internal/tools/get_training_history`: `{session_id}` returns actual persisted plan versions; no fabricated performed sets/training records.
- `DELETE /internal/sessions/{session_id}` deletes owned demo business data and drafts; called only after explicit user deletion. Internal API GET readback to prove absence.
- `GET /internal/sessions/{session_id}` snapshot or actual404.

Errors `{error:{code,message}}`; 401 unauthorized,404 absent,409 stale/blocked/unsupported,422 invalid. Highest risk in errors/snapshot when available.

## Canonical plan JSON

```
{
 "id":"ascii-safe-random-id", "revision":1, "status":"draft|confirmed",
 "based_on_revision":null,
 "title":"一周基础抗阻计划", "days_per_week":3,"minutes_per_session":30,
 "training_days":[1,3,5],"unavailable_days":[],"equipment":["dumbbell"],
 "source":"agent_rule_tool|rule_fallback", "review_status":"not_professionally_reviewed",
 "week":[{"day_of_week":1,"kind":"training|rest","exercises":[
  {"slot_id":"d1-s1","exercise_id":"rg-v1-bodyweight-squat","sets":2,
   "reps_min":8,"reps_max":12,"rest_seconds":60,"load_kg":null}
 ]}],
 "changes":[{"field":"days_per_week","before":"3","after":"4"}],
 "notices":["未经专业审核，不是医疗处方；不推算负重"]
}
```

Exactly7 ordered distinct weekdays; exercise IDs existing six; training count matches frequency; no exercise on rest days. Timed core exercises use `seconds:20` with `reps_min/reps_max:null`; Android retains and renders seconds rather than inventing repetition counts. Four days alternate lower and upper templates with existing core rule semantics, budget conservative (include warmup/cooldown/transition). Schedule no unavailable day; do not force unsafe density, disclose adjacent-day split. Each training day at least one action; reps and rest deterministically bounded. Constraints changed requiring unrepresentable back coverage return notice, not fake substitute.

状态：窄范围架构Demo实施；验证说明见foundation-demo-runbook/verification-v0.4.0。旧intake入口对非Foundation会话兼容；Foundation启用后旧messages/confirm明确409 foundation_required，不能混用绕过风险/草稿撤销。

## Python/Android public additions (old intake backwards compatible)

- Existing `/api/intake/sessions`, `/messages`, GET, `/confirm`, DELETE stay valid; extra optional fields allowed by old parsers.
- Foundation profile confirmation: `POST /api/intake/sessions/{id}/foundation/confirm-profile` same `{confirmed:true,expected_revision}`. Validate current intake, call Java profile confirmation, then bounded Agent tool loop prompted by actual user confirmation to propose plan. Return normal base `IntakeResponse` + fields below.
- Foundation later message: `POST /api/intake/sessions/{id}/foundation/messages` `{message:string<=1000}`. After confirmed profile, Python retains safe provider chat in session memory; registry ONLY read/proposal tools, NEVER confirm/write/delete/exec/URL tools. Before model use raw safety/correction guards; no automatic paid retries.
- Foundation plan confirmation: `POST .../foundation/confirm-plan` `{confirmed:true,draft_id,expected_revision}`. Explicit endpoint cannot be selected by tools. Java confirm then GET exact snapshot; same id/revision/content required. No model call needed on confirm.
- Foundation snapshot `GET .../foundation` for readback. Foundation deletion must also remove Java state (old DELETE may be expanded compatibly).
- Public demo auth is separate from per-session bearer: runtime-provided `X-Ready-Gym-Demo`, NOT compiled into APK; service URL may be compiled (no secret) or entered. Provider key only dedicated private600 env.

Extra response fields (optional for legacy):
`foundation_enabled:true`, `plan_proposal:Plan|null`, `active_plan:Plan|null`, `tool_trace:[{name,status:"ok|rejected"}]`, `agent_source:"deepseek_tool_calls|rule_fallback|server_policy"`, `agent_notice:string|null`.
Base `response_source` can be `deepseek_tool_calls`, `rule_fallback`, `server_policy`. Status remains collecting/ready/blocked. Always include bounded safety_route/clarification_fields. `assistant_text` for decision-bearing plan/status computed from verified tool results, not arbitrary model instructions. Agent narration may be shown only if it cannot invent a confirmed action/unsafe advice; prefer deterministic summary of verified tool outcomes.

Android `CoachReply` extra properties default null/empty. `CoachClient` new optional `supportsFoundation=false`, `confirmPlan` default unsupported; existing clients/tests unchanged. Foundation-capable HTTP implementation selects above confirm-profile/message paths after confirmed profile. Profile confirmation keeps chat (does not transition to standalone rule form) when foundation supported. Inline draft/active cards, before-after diff, pending-confirm buttons and exact existing ExerciseDemo/details. Confirmation blocked busy/unsent-input/new risk; repeat clicks idempotent/gated. Retain highest Activity risk and legacy offline flow tests.

## Deliberate demo scope

No anatomical SVG, log_set, RAG/pgvector, commerce, production account, automagic progression or medical safety certification in this slice. `get_training_history` = saved PLAN version history, clearly labelled, not workouts performed. PostgreSQL business persistence is real; conversation/token lifespan remains bounded and device process-loss session recovery is not a production account feature. A runnable bounded demo is the deliverable, not just tests or a plan.
