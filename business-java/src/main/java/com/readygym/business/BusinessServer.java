package com.readygym.business;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.security.MessageDigest;
import java.util.Set;
import com.fasterxml.jackson.core.*;

public final class BusinessServer implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(4000).maxNumberLength(50).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final HttpServer server;
    private final String jdbcUrl, user, password;
    private final byte[] token;
    private final java.util.Map<String, ObjectNode> pending = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> draftProfiles = new java.util.HashMap<>();
    public BusinessServer(String jdbcUrl, String user, String password, String internalToken, int port) throws Exception {
        this.jdbcUrl = jdbcUrl; this.user = user; this.password = password;
        if (internalToken == null || internalToken.length() < 32) throw new IllegalArgumentException("internal auth must have at least 32 characters");
        token = internalToken.getBytes(StandardCharsets.UTF_8);
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS ready_gym_business");
            s.execute("CREATE TABLE IF NOT EXISTS ready_gym_business.sessions (session_id VARCHAR(128) PRIMARY KEY, profile_revision INTEGER NOT NULL, profile_json TEXT, safety_route VARCHAR(40), active_revision INTEGER NOT NULL DEFAULT 0, touched_at BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ready_gym_business.plan_versions (session_id VARCHAR(128) NOT NULL REFERENCES ready_gym_business.sessions(session_id) ON DELETE CASCADE, revision INTEGER NOT NULL, draft_id VARCHAR(128) NOT NULL, profile_revision INTEGER NOT NULL, plan_json TEXT NOT NULL, confirmed_at BIGINT NOT NULL, PRIMARY KEY(session_id, revision), UNIQUE(session_id,draft_id))");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/", this::handle);
        server.start();
    }
    Connection connection() throws SQLException { return DriverManager.getConnection(jdbcUrl, user, password); }
    public int port() { return server.getAddress().getPort(); }
    private synchronized void handle(HttpExchange e) throws IOException {
        int status = 200;
        JsonNode result;
        try { result = dispatch(e); }
        catch (Problem p) {
            status = p.status; result = error(p.code, p.getMessage());
            if (p.safetyRoute != null) ((ObjectNode) result.path("error")).put("safety_route", p.safetyRoute);
        }
        catch (Exception ex) { status = 500; result = error("internal_error", "业务服务暂不可用"); }
        byte[] bytes = JSON.writeValueAsBytes(result);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        e.sendResponseHeaders(status, bytes.length);
        try (var out = e.getResponseBody()) { out.write(bytes); }
        e.close();
    }
    private JsonNode dispatch(HttpExchange e) throws Exception {
        var header = e.getRequestHeaders().get("X-Ready-Gym-Internal");
        if (header == null || header.size() != 1 || !MessageDigest.isEqual(token, header.get(0).getBytes(StandardCharsets.UTF_8)))
            throw new Problem(401, "unauthorized", "内部认证失败");
        String path = e.getRequestURI().getPath();
        if (e.getRequestMethod().equals("GET") && path.equals("/internal/health")) {
            try (var c = connection(); var s = c.createStatement(); var r = s.executeQuery("SELECT 1")) {
                r.next(); return JSON.createObjectNode().put("status","ok").put("database", c.getMetaData().getDatabaseProductName());
            }
        }
        if (e.getRequestMethod().equals("GET") && path.startsWith("/internal/sessions/")) {
            return snapshot(validId(path.substring("/internal/sessions/".length())));
        }
        if (e.getRequestMethod().equals("DELETE") && path.startsWith("/internal/sessions/")) {
            String sid=validId(path.substring("/internal/sessions/".length()));
            try (var c=connection(); var s=c.prepareStatement("DELETE FROM ready_gym_business.sessions WHERE session_id=?")) {
                s.setString(1,sid); s.executeUpdate();
            }
            pending.remove(sid); draftProfiles.remove(sid);
            return JSON.createObjectNode().put("session_id",sid).put("deleted",true);
        }
        if (!e.getRequestMethod().equals("POST")) throw new Problem(404, "not_found", "接口不存在");
        JsonNode body;
        byte[] bytes = e.getRequestBody().readNBytes(32769);
        if (bytes.length > 32768) throw invalid();
        try { body = JSON.readTree(bytes); } catch (Exception ex) { throw invalid(); }
        if (body == null || !body.isObject()) throw invalid();
        String sid = validId(body.path("session_id").isTextual() ? body.get("session_id").textValue() : "");
        if (path.equals("/internal/safety")) {
            fields(body,Set.of("session_id","route"));
            choice(body,"route",Set.of("professional_review","stop_and_seek_care"));
            String route=body.get("route").textValue();
            try (var c=connection()) {
                c.setAutoCommit(false);
                try {
                    boolean exists;
                    try (var q=c.prepareStatement("SELECT safety_route FROM ready_gym_business.sessions WHERE session_id=? FOR UPDATE")) {
                        q.setString(1,sid); try (var r=q.executeQuery()) {
                            exists=r.next();
                            if (exists && "stop_and_seek_care".equals(r.getString(1))) route="stop_and_seek_care";
                        }
                    }
                    String sql=exists ? "UPDATE ready_gym_business.sessions SET safety_route=?,touched_at=? WHERE session_id=?" :
                        "INSERT INTO ready_gym_business.sessions(safety_route,touched_at,session_id,profile_revision) VALUES(?,?,?,0)";
                    try (var s=c.prepareStatement(sql)) {
                        s.setString(1,route); s.setLong(2,System.currentTimeMillis()); s.setString(3,sid); s.executeUpdate();
                    }
                    c.commit();
                } catch (Exception ex) { c.rollback(); throw ex; }
            }
            pending.remove(sid); draftProfiles.remove(sid); return snapshot(sid);
        }
        if (path.equals("/internal/profile/confirm")) {
            fields(body, Set.of("session_id", "profile_revision", "profile", "confirmed"));
            if (!body.path("confirmed").isBoolean() || !body.get("confirmed").booleanValue()) throw new Problem(422, "confirmation_required", "需要明确确认");
            int rev = integer(body, "profile_revision", 0, Integer.MAX_VALUE);
            JsonNode profile = body.path("profile");
            validateProfile(profile);
            try (var c = connection()) {
                c.setAutoCommit(false);
                try {
                    boolean exists;
                    try (var q = c.prepareStatement("SELECT profile_revision,profile_json,safety_route FROM ready_gym_business.sessions WHERE session_id=? FOR UPDATE")) {
                        q.setString(1, sid);
                        try (var r = q.executeQuery()) {
                            exists = r.next();
                            if (exists && r.getString("safety_route") != null) throw new Problem(409, "safety_blocked", "安全分流已锁定",r.getString("safety_route"));
                            if (exists && rev <= r.getInt(1)) {
                                if (rev == r.getInt(1) && profile.equals(JSON.readTree(r.getString(2)))) { c.commit(); return snapshot(sid); }
                                throw new Problem(409, "stale_revision", "档案版本已变化，请重新确认");
                            }
                        }
                    }
                    String sql = exists ? "UPDATE ready_gym_business.sessions SET profile_revision=?,profile_json=?,touched_at=? WHERE session_id=?" :
                        "INSERT INTO ready_gym_business.sessions(profile_revision,profile_json,touched_at,session_id) VALUES(?,?,?,?)";
                    try (var s = c.prepareStatement(sql)) {
                        s.setInt(1, rev); s.setString(2, profile.toString()); s.setLong(3, System.currentTimeMillis()); s.setString(4,sid); s.executeUpdate();
                    }
                    c.commit();
                    pending.remove(sid);
                    draftProfiles.remove(sid);
                } catch (Exception ex) { c.rollback(); throw ex; }
            }
            return snapshot(sid);
        }
        if (path.equals("/internal/tools/get_profile")) { fields(body, Set.of("session_id")); return snapshot(sid); }
        if (path.equals("/internal/tools/search_exercises")) {
            fields(body,Set.of("session_id","muscle","equipment"));
            var state=snapshot(sid);
            if (!state.path("safety_route").isNull()) throw new Problem(409,"safety_blocked","安全分流已锁定",state.path("safety_route").asText());
            validateProfile(state.path("profile"));
            if (body.has("muscle")) choice(body,"muscle",Set.of("quadriceps","glutes","chest","back","core"));
            Set<String> available=new java.util.LinkedHashSet<>(equipment(state.path("profile").path("equipment")));
            available.add("bodyweight");
            Set<String> filter=body.has("equipment") ? equipment(body.get("equipment")) : available;
            if (!available.containsAll(filter)) throw new Problem(409,"unsupported_equipment","器械筛选不能扩展已确认的可用器械");
            String[] muscles={"quadriceps","chest","glutes","core","back","back"};
            String[] required={"bodyweight","bodyweight","bodyweight","bodyweight","dumbbell","barbell"};
            String[] names={"徒手深蹲","跪姿俯卧撑","臀桥","死虫式","哑铃划船","杠铃划船"};
            var result=JSON.createObjectNode().put("source","existing_six_exercises"); var exercises=result.putArray("exercises");
            for (int i=0;i<Planner.IDS.length;i++) {
                if (!filter.contains(required[i]) || (body.has("muscle") && !body.get("muscle").asText().equals(muscles[i]))) continue;
                exercises.addObject().put("exercise_id",Planner.IDS[i]).put("name",names[i]).put("muscle",muscles[i]).put("equipment",required[i]);
            }
            return result;
        }
        if (path.equals("/internal/tools/check_entitlement")) {
            fields(body,Set.of("session_id"));
            var state=snapshot(sid);
            boolean eligible=state.path("safety_route").isNull() && !state.path("profile").isNull();
            var result=JSON.createObjectNode().put("eligible",eligible).put("scope","bounded_demo")
                .put("membership_verified",false).put("payment_verified",false)
                .put("notice","仅限本演示的规则计划资格，不代表会员、支付、医疗或专业审核");
            result.set("safety_route",state.path("safety_route")); return result;
        }
        if (path.equals("/internal/plan/confirm")) {
            fields(body,Set.of("session_id","draft_id","expected_revision","confirmed"));
            if (!body.path("confirmed").isBoolean() || !body.get("confirmed").booleanValue()) throw new Problem(422,"confirmation_required","需要明确确认此草稿");
            String id = validId(body.path("draft_id").isTextual() ? body.get("draft_id").textValue() : "");
            int expected = integer(body,"expected_revision",1,Integer.MAX_VALUE);
            ObjectNode plan;
            try (var c = connection()) {
                c.setAutoCommit(false);
                try {
                    int profileRev, activeRev;
                    try (var q = c.prepareStatement("SELECT profile_revision,active_revision,safety_route FROM ready_gym_business.sessions WHERE session_id=? FOR UPDATE")) {
                        q.setString(1,sid); try (var r = q.executeQuery()) {
                            if (!r.next()) throw new Problem(404,"not_found","会话不存在");
                            if (r.getString("safety_route") != null) throw new Problem(409,"safety_blocked","安全分流已锁定",r.getString("safety_route"));
                            profileRev=r.getInt(1); activeRev=r.getInt(2);
                        }
                    }
                    JsonNode existing = persistedPlan(c,sid,activeRev);
                    if (existing != null && expected==activeRev && existing.path("id").asText().equals(id)) { c.commit(); return existing; }
                    var draft = pending.get(sid);
                    if (draft==null || !draft.path("id").asText().equals(id) || expected!=draft.path("revision").asInt() || expected!=activeRev+1 ||
                        !java.util.Objects.equals(draftProfiles.get(sid),profileRev)) throw new Problem(409,"stale_revision","草稿或档案版本已变化，请重新预览");
                    plan=draft.deepCopy().put("status","confirmed");
                    try (var s = c.prepareStatement("INSERT INTO ready_gym_business.plan_versions(session_id,revision,draft_id,profile_revision,plan_json,confirmed_at) VALUES(?,?,?,?,?,?)")) {
                        s.setString(1,sid); s.setInt(2,expected); s.setString(3,id); s.setInt(4,profileRev); s.setString(5,plan.toString()); s.setLong(6,System.currentTimeMillis()); s.executeUpdate();
                    }
                    try (var s = c.prepareStatement("UPDATE ready_gym_business.sessions SET active_revision=? WHERE session_id=?")) { s.setInt(1,expected); s.setString(2,sid); s.executeUpdate(); }
                    c.commit();
                    pending.remove(sid); draftProfiles.remove(sid);
                } catch (Exception ex) { c.rollback(); throw ex; }
            }
            return plan;
        }
        if (path.equals("/internal/tools/get_training_history")) {
            fields(body,Set.of("session_id")); snapshot(sid);
            var result = JSON.createObjectNode().put("kind","saved_plan_versions_not_workouts"); var versions = result.putArray("versions");
            try (var c = connection(); var s = c.prepareStatement("SELECT plan_json FROM ready_gym_business.plan_versions WHERE session_id=? ORDER BY revision")) {
                s.setString(1,sid); try (var r = s.executeQuery()) { while (r.next()) versions.add(JSON.readTree(r.getString(1))); }
            }
            return result;
        }
        if (path.equals("/internal/tools/propose_revision")) {
            fields(body,Set.of("session_id","expected_revision","days_per_week","minutes_per_session","unavailable_days","equipment"));
            var state=snapshot(sid);
            if (!state.path("safety_route").isNull()) throw new Problem(409,"safety_blocked","安全分流已锁定",state.path("safety_route").asText());
            var active=state.path("active_plan");
            int expected=integer(body,"expected_revision",1,Integer.MAX_VALUE-1);
            if (active.isNull() || active.path("revision").asInt()!=expected) throw new Problem(409,"stale_revision","计划版本已变化，请重新预览");
            ObjectNode updated=(ObjectNode)state.path("profile").deepCopy();
            var changes=JSON.createArrayNode();
            for (String field:new String[]{"days_per_week","minutes_per_session","equipment","unavailable_days"}) {
                if (!body.has(field)) { if (active.has(field)) updated.set(field,active.get(field).deepCopy()); continue; }
                if (field.equals("days_per_week") && !Set.of(2,3,4).contains(integer(body,field,2,4))) throw invalid();
                if (field.equals("minutes_per_session") && !Set.of(20,30,45,60).contains(integer(body,field,20,60))) throw invalid();
                if (field.equals("equipment")) equipment(body.get(field));
                if (field.equals("unavailable_days")) unavailable(body.get(field));
                updated.set(field,body.get(field).deepCopy());
                if (!body.get(field).equals(active.path(field))) changes.addObject().put("field",field).put("before",active.path(field).toString()).put("after",body.get(field).toString());
            }
            if (changes.isEmpty()) throw new Problem(422,"change_required","需要至少一个明确变化");
            Set<Integer> unavailable=updated.has("unavailable_days") ? unavailable(updated.get("unavailable_days")) : Set.of();
            var draft=Planner.generate(updated,unavailable).put("revision",expected+1).put("based_on_revision",expected);
            draft.set("changes",changes);
            pending.put(sid,draft); draftProfiles.put(sid,state.path("profile_revision").asInt()); return draft;
        }
        if (path.equals("/internal/proposal/cancel")) {
            fields(body, Set.of("session_id"));
            snapshot(sid);
            pending.remove(sid); draftProfiles.remove(sid);
            return snapshot(sid);
        }
        if (path.equals("/internal/tools/propose_plan")) {
            fields(body, Set.of("session_id", "source"));
            if (body.has("source")) choice(body,"source",Set.of("agent_rule_tool","rule_fallback"));
            var state = snapshot(sid);
            if (!state.path("safety_route").isNull()) throw new Problem(409,"safety_blocked","安全分流已锁定",state.path("safety_route").asText());
            validateProfile(state.path("profile"));
            if (!state.path("active_plan").isNull()) throw new Problem(409,"active_plan_exists","已有计划请使用修改提案");
            var draft = Planner.generate(state.path("profile"));
            if (body.has("source")) draft.put("source",body.get("source").textValue());
            pending.put(sid,draft); draftProfiles.put(sid,state.path("profile_revision").asInt()); return draft;
        }
        throw new Problem(404, "not_found", "接口不存在");
    }
    private ObjectNode snapshot(String sid) throws Exception {
        try (var c = connection(); var s = c.prepareStatement("SELECT * FROM ready_gym_business.sessions WHERE session_id=?")) {
            s.setString(1, sid);
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new Problem(404, "not_found", "会话不存在");
                var result = JSON.createObjectNode().put("session_id", sid).put("profile_revision", r.getInt("profile_revision"));
                if (r.getString("profile_json")==null) result.putNull("profile");
                else result.set("profile", JSON.readTree(r.getString("profile_json")));
                result.putNull("active_plan").putNull("pending_plan");
                JsonNode active = persistedPlan(c,sid,r.getInt("active_revision"));
                if (active!=null) result.set("active_plan",active);
                if (pending.containsKey(sid)) result.set("pending_plan",pending.get(sid).deepCopy());
                result.set("plan_proposal",result.get("pending_plan").deepCopy());
                if (r.getString("safety_route") == null) result.putNull("safety_route");
                else result.put("safety_route", r.getString("safety_route"));
                return result;
            }
        }
    }
    private JsonNode persistedPlan(Connection c,String sid,int revision) throws Exception {
        try (var s = c.prepareStatement("SELECT plan_json FROM ready_gym_business.plan_versions WHERE session_id=? AND revision=?")) {
            s.setString(1,sid); s.setInt(2,revision); try (var r = s.executeQuery()) { return r.next() ? JSON.readTree(r.getString(1)) : null; }
        }
    }
    static ObjectNode error(String code, String message) {
        var r = JSON.createObjectNode(); r.putObject("error").put("code", code).put("message", message); return r;
    }
    static Problem invalid() { return new Problem(422, "invalid_request", "请求字段或格式无效"); }
    static String validId(String id) { if (!id.matches("[A-Za-z0-9_-]{1,128}")) throw invalid(); return id; }
    static void fields(JsonNode body, Set<String> allowed) {
        body.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw invalid(); });
    }
    static int integer(JsonNode b, String key, int low, int high) {
        var n = b.path(key); if (!n.isIntegralNumber() || !n.canConvertToInt() || n.intValue() < low || n.intValue() > high) throw invalid(); return n.intValue();
    }
    static void choice(JsonNode b, String key, Set<String> allowed) {
        if (!b.path(key).isTextual() || !allowed.contains(b.get(key).textValue())) throw invalid();
    }
    static void validateProfile(JsonNode p) {
        if (!p.isObject()) throw invalid();
        fields(p, Set.of("age","height_cm","weight_kg","gender","goal","location","equipment","days_per_week","minutes_per_session","experience","current_discomfort","medical_restrictions"));
        integer(p,"age",18,120);
        for (String f : new String[]{"height_cm","weight_kg"}) {
            double n = p.path(f).doubleValue();
            if (!p.path(f).isNumber() || !Double.isFinite(n) || n < (f.equals("height_cm") ? 100 : 30) || n > (f.equals("height_cm") ? 250 : 300)) throw invalid();
        }
        choice(p,"goal",Set.of("muscle","fat_loss","health")); choice(p,"location",Set.of("home","gym"));
        if (p.hasNonNull("gender")) choice(p,"gender",Set.of("female","male","other","undisclosed"));
        choice(p,"experience",Set.of("beginner","returning"));
        if (!p.path("current_discomfort").isTextual() || !p.path("medical_restrictions").isTextual() ||
            !p.get("current_discomfort").textValue().equals("none") || !p.get("medical_restrictions").textValue().equals("none"))
            throw new Problem(409,"safety_blocked","当前不适或医学限制不允许普通模板");
        if (!Set.of(2,3,4).contains(integer(p,"days_per_week",2,4)) || !Set.of(20,30,45,60).contains(integer(p,"minutes_per_session",20,60))) throw invalid();
        equipment(p.path("equipment"));
    }
    static Set<Integer> unavailable(JsonNode n) {
        if (!n.isArray() || n.size()>7) throw invalid();
        Set<Integer> result=new java.util.TreeSet<>();
        for (var x:n) if (!x.isIntegralNumber() || !x.canConvertToInt() || x.intValue()<1 || x.intValue()>7 || !result.add(x.intValue())) throw invalid();
        return result;
    }
    static Set<String> equipment(JsonNode n) {
        if (!n.isArray() || n.isEmpty() || n.size() > 4) throw invalid();
        Set<String> eq = new java.util.LinkedHashSet<>();
        for (var item : n) if (!item.isTextual() || !Set.of("bodyweight","dumbbell","barbell","machine").contains(item.textValue()) || !eq.add(item.textValue())) throw invalid();
        return eq;
    }
    static final class Problem extends RuntimeException {
        final int status; final String code, safetyRoute;
        Problem(int status, String code, String message) { this(status,code,message,null); }
        Problem(int status, String code, String message, String safetyRoute) {
            super(message); this.status=status; this.code=code; this.safetyRoute=safetyRoute;
        }
    }
    @Override public void close() { server.stop(0); }
}
