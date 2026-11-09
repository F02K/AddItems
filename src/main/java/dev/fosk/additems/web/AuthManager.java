package dev.fosk.additems.web;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time login tokens (created by /additems login) are exchanged for a session cookie.
 * There are no passwords: whoever may run the command may open the dashboard.
 */
public final class AuthManager {

    private static final long TOKEN_TTL_MS = 10 * 60 * 1000L;
    private static final long SESSION_TTL_MS = 12 * 60 * 60 * 1000L;

    private record Entry(String user, long expiresAt) {}

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public String createLoginToken(String user) {
        purge();
        String token = randomHex(24);
        tokens.put(token, new Entry(user, System.currentTimeMillis() + TOKEN_TTL_MS));
        return token;
    }

    /** Consumes a login token. Returns a new session id, or null if the token is unknown or expired. */
    public String redeem(String token) {
        if (token == null) return null;
        Entry entry = tokens.remove(token);
        if (entry == null || entry.expiresAt < System.currentTimeMillis()) return null;
        String session = randomHex(32);
        sessions.put(session, new Entry(entry.user, System.currentTimeMillis() + SESSION_TTL_MS));
        return session;
    }

    /** Returns the user name of a valid session, or null. */
    public String userFor(String session) {
        if (session == null) return null;
        Entry entry = sessions.get(session);
        if (entry == null) return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            sessions.remove(session);
            return null;
        }
        return entry.user;
    }

    public void logout(String session) {
        if (session != null) sessions.remove(session);
    }

    private void purge() {
        long now = System.currentTimeMillis();
        tokens.values().removeIf(e -> e.expiresAt < now);
        sessions.values().removeIf(e -> e.expiresAt < now);
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return HexFormat.of().formatHex(data);
    }
}
