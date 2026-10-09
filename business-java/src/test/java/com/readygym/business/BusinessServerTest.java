package com.readygym.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BusinessServerTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String TOKEN = "test-internal-secret-with-32-bytes-or-more";
    static final String PROFILE = "{\"age\":21,\"height_cm\":175,\"weight_kg\":70,\"goal\":\"muscle\",\"location\":\"gym\",\"equipment\":[\"dumbbell\"],\"days_per_week\":3,\"minutes_per_session\":30,\"experience\":\"beginner\",\"current_discomfort\":\"none\",\"medical_restrictions\":\"none\"}";
    BusinessServer server;
    String url;
    String dbUser = "sa", dbPassword = "";
    final HttpClient http = HttpClient.newHttpClient();
    @BeforeEach void start() throws Exception {
        url = System.getenv("READY_GYM_JDBC_URL");
        if (url == null) url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        else { dbUser = System.getenv("READY_GYM_DB_USER"); dbPassword = System.getenv("READY_GYM_DB_PASSWORD"); }
        server = new BusinessServer(url, dbUser, dbPassword, TOKEN, 0);
        if (url.startsWith("jdbc:postgresql:")) {
            try (var c = DriverManager.getConnection(url,dbUser,dbPassword); var s = c.createStatement()) { s.executeUpdate("DELETE FROM ready_gym_business.sessions WHERE session_id IN ('s1','s2')"); }
        }
    }
    @AfterEach void stop() throws Exception {
        server.close();
        if (url.startsWith("jdbc:postgresql:")) {
            try (var c = DriverManager.getConnection(url,dbUser,dbPassword); var s = c.createStatement()) { s.executeUpdate("DELETE FROM ready_gym_business.sessions WHERE session_id IN ('s1','s2')"); }
        }
    }
    HttpResponse<String> request(String method, String route, String payload, boolean auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + route)).header("Content-Type", "application/json");
        if (auth) b.header("X-Ready-Gym-Internal", TOKEN);
        b.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String route, String data) throws Exception { return request("POST", route, data, true); }
    JsonNode data(HttpResponse<String> r) throws Exception { return JSON.readTree(r.body()); }
    HttpResponse<String> confirmProfile() throws Exception {
        return post("/internal/profile/confirm", "{\"session_id\":\"s1\",\"profile_revision\":1,\"profile\":" + PROFILE + ",\"confirmed\":true}");
    }
    JsonNode firstPlan() throws Exception {
        confirmProfile(); var d = data(post("/internal/tools/propose_plan","{\"session_id\":\"s1\"}"));
        return data(confirmDraft(d));
    }
    HttpResponse<String> confirmDraft(JsonNode d) throws Exception {
        return post("/internal/plan/confirm","{\"session_id\":\"s1\",\"draft_id\":\""+d.path("id").asText()+"\",\"expected_revision\":"+d.path("revision").asInt()+",\"confirmed\":true}");
    }
    @Test void cancellingPendingNeverChangesPersistedActiveAndRejectsStaleApproval() throws Exception {
        var active=firstPlan();
        var pending=data(post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":4}"));
        var cancelled=post("/internal/proposal/cancel","{\"session_id\":\"s1\"}");
        assertEquals(200,cancelled.statusCode());
        assertEquals(active,data(cancelled).path("active_plan"));
        assertTrue(data(cancelled).path("plan_proposal").isNull());
        assertEquals(409,confirmDraft(pending).statusCode());
    }
    @Test void fallbackSourceSurvivesPendingAliasConfirmationAndRestart() throws Exception {
        confirmProfile();
        var response=post("/internal/tools/propose_plan","{\"session_id\":\"s1\",\"source\":\"rule_fallback\"}");
        assertEquals(200,response.statusCode(),response.body());
        var draft=data(response); assertEquals("rule_fallback",draft.path("source").asText());
        var state=data(request("GET","/internal/sessions/s1",null,true));
        assertEquals(draft,state.path("pending_plan")); assertEquals(draft,state.path("plan_proposal"));
        var confirmed=confirmDraft(draft); assertEquals(200,confirmed.statusCode(),confirmed.body());
        assertEquals("rule_fallback",data(confirmed).path("source").asText());
        server.close(); server=new BusinessServer(url,dbUser,dbPassword,TOKEN,0);
        state=data(request("GET","/internal/sessions/s1",null,true));
        assertEquals(data(confirmed),state.path("active_plan")); assertTrue(state.path("plan_proposal").isNull());
        assertEquals(422,post("/internal/tools/propose_plan","{\"session_id\":\"s1\",\"source\":\"medical_reviewed\"}").statusCode());
    }
    @Test void deletionRemovesVersionsAndDraftsAndReadsBackActual404() throws Exception {
        firstPlan();
        var draft=data(post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":2}"));
        assertEquals(401,request("DELETE","/internal/sessions/s1",null,false).statusCode());
        var deleted=request("DELETE","/internal/sessions/s1",null,true);
        assertEquals(200,deleted.statusCode(),deleted.body());
        assertTrue(data(deleted).path("deleted").asBoolean());
        assertEquals(404,request("GET","/internal/sessions/s1",null,true).statusCode());
        assertEquals(404,confirmDraft(draft).statusCode());
        try (var c=DriverManager.getConnection(url,dbUser,dbPassword); var s=c.createStatement(); var r=s.executeQuery("SELECT COUNT(*) FROM ready_gym_business.plan_versions WHERE session_id='s1'")) {
            r.next(); assertEquals(0,r.getInt(1));
        }
        confirmProfile(); assertTrue(data(request("GET","/internal/sessions/s1",null,true)).path("pending_plan").isNull());
    }
    @Test void exerciseSearchOnlyNarrowsConfirmedEquipmentAndStableCatalog() throws Exception {
        confirmProfile();
        var response=post("/internal/tools/search_exercises","{\"session_id\":\"s1\"}");
        assertEquals(200,response.statusCode(),response.body());
        var ids=new java.util.HashSet<String>();
        for (var exercise:data(response).path("exercises")) ids.add(exercise.path("exercise_id").asText());
        assertEquals(java.util.Set.of("rg-v1-bodyweight-squat","rg-v1-knee-push-up","rg-v1-glute-bridge","rg-v1-dead-bug","rg-v1-dumbbell-row"),ids);
        var back=data(post("/internal/tools/search_exercises","{\"session_id\":\"s1\",\"muscle\":\"back\",\"equipment\":[\"dumbbell\"]}"));
        assertEquals(1,back.path("exercises").size()); assertEquals("rg-v1-dumbbell-row",back.path("exercises").get(0).path("exercise_id").asText());
        assertEquals(409,post("/internal/tools/search_exercises","{\"session_id\":\"s1\",\"equipment\":[\"barbell\"]}").statusCode());
        assertEquals(422,post("/internal/tools/search_exercises","{\"session_id\":\"s1\",\"muscle\":\"medical\"}").statusCode());
    }
    @Test void entitlementIsExplicitlyBoundedDemoEligibility() throws Exception {
        assertEquals(200,confirmProfile().statusCode());
        var response=post("/internal/tools/check_entitlement","{\"session_id\":\"s1\"}");
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(data(response).path("eligible").asBoolean());
        assertEquals("bounded_demo",data(response).path("scope").asText());
        assertFalse(data(response).path("membership_verified").asBoolean());
        assertEquals(422,post("/internal/tools/check_entitlement","{\"session_id\":\"s1\",\"paid\":true}").statusCode());
    }
    @Test void durableSafetyLatchBlocksAllWritesAndCannotBeDowngradedAfterRestart() throws Exception {
        firstPlan();
        var draft=data(post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":2}"));
        var blocked=post("/internal/safety","{\"session_id\":\"s1\",\"route\":\"stop_and_seek_care\"}");
        assertEquals(200,blocked.statusCode(),blocked.body());
        assertEquals("stop_and_seek_care",data(blocked).path("safety_route").asText());
        assertTrue(data(blocked).path("pending_plan").isNull());
        assertEquals(409,confirmDraft(draft).statusCode());
        assertEquals(409,confirmProfile().statusCode());
        var rejected=post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":2}");
        assertEquals(409,rejected.statusCode()); assertEquals("stop_and_seek_care",data(rejected).path("error").path("safety_route").asText());
        post("/internal/safety","{\"session_id\":\"s1\",\"route\":\"professional_review\"}");
        server.close(); server=new BusinessServer(url,dbUser,dbPassword,TOKEN,0);
        var state=data(request("GET","/internal/sessions/s1",null,true));
        assertEquals("stop_and_seek_care",state.path("safety_route").asText()); assertEquals(1,state.path("active_plan").path("revision").asInt());
        assertEquals(409,post("/internal/tools/propose_plan","{\"session_id\":\"s1\"}").statusCode());
        var early=post("/internal/safety","{\"session_id\":\"s2\",\"route\":\"professional_review\"}");
        assertEquals(200,early.statusCode(),early.body()); assertTrue(data(early).path("profile").isNull());
    }
    @Test void scheduleRevisionPreservesVersionOneUntilExplicitConfirmation() throws Exception {
        var old = firstPlan();
        var response = post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":4,\"minutes_per_session\":20,\"unavailable_days\":[3],\"equipment\":[\"bodyweight\"]}");
        assertEquals(200,response.statusCode(),response.body());
        var draft = data(response); assertEquals(2,draft.path("revision").asInt()); assertEquals(1,draft.path("based_on_revision").asInt());
        assertFalse(draft.path("changes").isEmpty());
        var state = data(request("GET","/internal/sessions/s1",null,true)); assertEquals(old,state.path("active_plan"));
        int ordinal = 0;
        for (var day : draft.path("week")) if (day.path("kind").asText().equals("training")) {
            assertNotEquals(3,day.path("day_of_week").asInt());
            var seen = new java.util.HashSet<String>(); for (var x : day.path("exercises")) seen.add(x.path("exercise_id").asText());
            if ((ordinal++ % 2)==0) { assertTrue(seen.contains("rg-v1-bodyweight-squat")); assertFalse(seen.contains("rg-v1-knee-push-up")); }
            else { assertTrue(seen.contains("rg-v1-knee-push-up")); assertFalse(seen.contains("rg-v1-bodyweight-squat")); }
            assertTrue(day.path("estimated_seconds").asInt()<=1200);
        }
        assertEquals(4,ordinal); assertEquals(200,confirmDraft(draft).statusCode());
        var history = data(post("/internal/tools/get_training_history","{\"session_id\":\"s1\"}"));
        assertEquals(2,history.path("versions").size()); assertEquals(old,history.path("versions").get(0));
        assertEquals(409,post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":1,\"days_per_week\":2}").statusCode());
        assertEquals(422,post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":2}").statusCode());
        assertEquals(409,post("/internal/tools/propose_revision","{\"session_id\":\"s1\",\"expected_revision\":2,\"days_per_week\":4,\"unavailable_days\":[1,2,3,4]}").statusCode());
    }
    @Test void explicitPlanConfirmationAtomicallySavesAndRetries() throws Exception {
        confirmProfile();
        var draft = data(post("/internal/tools/propose_plan", "{\"session_id\":\"s1\"}"));
        String payload = "{\"session_id\":\"s1\",\"draft_id\":\""+draft.path("id").asText()+"\",\"expected_revision\":1,\"confirmed\":true}";
        var response = post("/internal/plan/confirm", payload);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("confirmed",data(response).path("status").asText());
        assertEquals(200, post("/internal/plan/confirm",payload).statusCode());
        var state = data(request("GET","/internal/sessions/s1",null,true));
        assertEquals(draft.path("id"),state.path("active_plan").path("id"));
        assertTrue(state.path("pending_plan").isNull());
        var history = post("/internal/tools/get_training_history","{\"session_id\":\"s1\"}");
        assertEquals(200, history.statusCode(),history.body());
        assertEquals(1,data(history).path("versions").size());
        assertEquals("saved_plan_versions_not_workouts",data(history).path("kind").asText());
        assertEquals(409,post("/internal/plan/confirm",payload.replace(draft.path("id").asText(),"other-id")).statusCode());
        try (var c = DriverManager.getConnection(url,dbUser,dbPassword); var s = c.createStatement(); var r = s.executeQuery("SELECT active_revision, (SELECT COUNT(*) FROM ready_gym_business.plan_versions WHERE session_id='s1') AS versions FROM ready_gym_business.sessions WHERE session_id='s1'")) {
            r.next(); assertEquals(1,r.getInt(1)); assertEquals(1,r.getInt(2));
        }
    }
    @Test void healthIsAuthenticatedAndReachesDatabase() throws Exception {
        var health = request("GET", "/internal/health", null, true);
        assertEquals(200, health.statusCode(), health.body());
        assertEquals("ok", data(health).path("status").asText());
        assertEquals(401, request("GET", "/internal/health", null, false).statusCode());
    }
    @Test void planDraftUsesOnlyStableRulesAndDoesNotWriteBusinessRows() throws Exception {
        assertEquals(200, confirmProfile().statusCode());
        var response = post("/internal/tools/propose_plan", "{\"session_id\":\"s1\"}");
        assertEquals(200, response.statusCode(), response.body());
        var plan = data(response);
        assertEquals("draft", plan.path("status").asText());
        assertEquals(1, plan.path("revision").asInt());
        assertEquals(7, plan.path("week").size());
        int training = 0;
        var ids = java.util.Set.of("rg-v1-bodyweight-squat","rg-v1-knee-push-up","rg-v1-glute-bridge","rg-v1-dead-bug","rg-v1-dumbbell-row","rg-v1-barbell-row");
        for (var day : plan.path("week")) {
            if (day.path("kind").asText().equals("training")) {
                training++; assertFalse(day.path("exercises").isEmpty());
                for (var x : day.path("exercises")) { assertTrue(ids.contains(x.path("exercise_id").asText())); assertTrue(x.path("load_kg").isNull()); }
                assertTrue(day.path("estimated_seconds").asInt() <= 30 * 60);
            } else assertTrue(day.path("exercises").isEmpty());
        }
        assertEquals(3, training);
        assertTrue(data(request("GET", "/internal/sessions/s1", null, true)).path("active_plan").isNull());
        try (var c = DriverManager.getConnection(url, dbUser, dbPassword); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM ready_gym_business.plan_versions WHERE session_id='s1'")) {
            r.next(); assertEquals(0, r.getInt(1));
        }
    }
    @Test void profileConfirmationValidatesAndRetriesWithoutOverwriting() throws Exception {
        assertEquals(200, confirmProfile().statusCode());
        assertEquals(200, confirmProfile().statusCode(), "identical retry");
        String base = "{\"session_id\":\"s1\",\"profile_revision\":1,\"profile\":";
        assertEquals(409, post("/internal/profile/confirm", base + PROFILE.replace("21", "22") + ",\"confirmed\":true}").statusCode());
        assertEquals(422, post("/internal/profile/confirm", base + PROFILE + ",\"confirmed\":\"true\"}").statusCode());
        for (String bad : new String[] { PROFILE.replace("21", "17"), PROFILE.replace("beginner", "advanced"), PROFILE.replace("\"none\"", "\"pain\""), PROFILE.replace("175", "\"175\"") }) {
            assertTrue(post("/internal/profile/confirm", base.replace("s1", "s2") + bad + ",\"confirmed\":true}").statusCode() >= 400);
        }
        assertEquals(21, data(request("GET", "/internal/sessions/s1", null, true)).path("profile").path("age").asInt());
    }
    @Test void internalRequestsRequireAuthenticationAndStrictJson() throws Exception {
        assertEquals(401, request("POST", "/internal/profile/confirm", "{}", false).statusCode());
        assertEquals(422, post("/internal/profile/confirm", "{\"session_id\":\"s1\",\"session_id\":\"s2\"}").statusCode());
        assertEquals(422, post("/internal/profile/confirm", "{} {}").statusCode());
        assertEquals(422, post("/internal/profile/confirm", "[1]").statusCode());
        assertEquals(422, post("/internal/profile/confirm", "{\"session_id\":\"s1\",\"confirmed\":true,\"sets\":99}").statusCode());
        assertEquals(422, post("/internal/profile/confirm", " ".repeat(32769)).statusCode());
    }
    @Test void explicitlyConfirmedProfilePersistsAndReadsBack() throws Exception {
        var saved = confirmProfile();
        assertEquals(200, saved.statusCode(), saved.body());
        var snapshot = request("GET", "/internal/sessions/s1", null, true);
        assertEquals(200, snapshot.statusCode(), snapshot.body());
        assertEquals(1, data(snapshot).path("profile_revision").asInt());
        assertEquals(21, data(snapshot).path("profile").path("age").asInt());
        try (var c = DriverManager.getConnection(url, dbUser, dbPassword); var st = c.createStatement();
             var r = st.executeQuery("SELECT COUNT(*) FROM ready_gym_business.sessions WHERE session_id='s1'")) {
            r.next(); assertEquals(1, r.getInt(1));
        }
    }
}
