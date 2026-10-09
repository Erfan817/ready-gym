package com.readygym.business;

/** Standalone entrypoint. Secrets are process environment only. */
public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        String url = required("READY_GYM_JDBC_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:") && !url.startsWith("jdbc:postgresql://localhost:"))
            throw new IllegalArgumentException("JDBC must target local PostgreSQL");
        int port = Integer.parseInt(System.getenv().getOrDefault("READY_GYM_BUSINESS_PORT","8766"));
        var server = new BusinessServer(url, required("READY_GYM_DB_USER"), required("READY_GYM_DB_PASSWORD"), required("READY_GYM_INTERNAL_TOKEN"), port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("Ready Gym business listening 127.0.0.1:" + server.port());
    }
    private static String required(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("missing environment: " + key);
        return v;
    }
}
