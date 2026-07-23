package dev.incusspawn.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.incusspawn.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Persists real Codex OAuth tokens on the host.
 * Tokens are stored in ~/.config/incus-spawn/codex-tokens.json and never
 * enter the container — the MITM proxy swaps placeholders for real values
 * on each request.
 */
public class CodexTokenStore {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FILE_NAME = "codex-tokens.json";

    private final Path storePath;
    private volatile Tokens cached;
    private volatile long lastRefreshEpochMs;

    public CodexTokenStore() {
        this(Environment.configDir().resolve(FILE_NAME));
    }

    CodexTokenStore(Path storePath) {
        this.storePath = storePath;
        this.cached = loadFromDisk();
        this.lastRefreshEpochMs = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tokens {
        @JsonProperty private String accessToken = "";
        @JsonProperty private String refreshToken = "";
        @JsonProperty private String idToken = "";
        @JsonProperty private String accountId = "";
        @JsonProperty private boolean fedramp;
        @JsonProperty private String lastRefresh = "";

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String v) { accessToken = v == null ? "" : v; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String v) { refreshToken = v == null ? "" : v; }
        public String getIdToken() { return idToken; }
        public void setIdToken(String v) { idToken = v == null ? "" : v; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String v) { accountId = v == null ? "" : v; }
        public boolean isFedramp() { return fedramp; }
        public void setFedramp(boolean v) { fedramp = v; }
        public String getLastRefresh() { return lastRefresh; }
        public void setLastRefresh(String v) { lastRefresh = v == null ? "" : v; }

        public boolean hasTokens() {
            return !accessToken.isBlank() && !refreshToken.isBlank();
        }
    }

    public synchronized Tokens get() {
        return cached;
    }

    public boolean hasTokens() {
        var t = cached;
        return t != null && t.hasTokens();
    }

    /**
     * Store new tokens and persist to disk atomically.
     * Also records the time of the last refresh for serialization purposes.
     */
    public synchronized void save(Tokens tokens) {
        tokens.setLastRefresh(Instant.now().toString());
        this.cached = tokens;
        this.lastRefreshEpochMs = System.currentTimeMillis();
        writeToDisk(tokens);
    }

    /**
     * Returns true if a refresh was completed within the last {@code windowMs}
     * milliseconds — used to coalesce concurrent refresh requests.
     */
    public boolean recentlyRefreshed(long windowMs) {
        return System.currentTimeMillis() - lastRefreshEpochMs < windowMs;
    }

    private Tokens loadFromDisk() {
        if (!Files.exists(storePath)) return null;
        try {
            return JSON.readValue(storePath.toFile(), Tokens.class);
        } catch (IOException e) {
            System.err.println("Warning: failed to read " + storePath + ": " + e.getMessage());
            return null;
        }
    }

    private void writeToDisk(Tokens tokens) {
        try {
            Files.createDirectories(storePath.getParent());
            var tmp = storePath.resolveSibling(FILE_NAME + ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), tokens);
            tmp.toFile().setReadable(false, false);
            tmp.toFile().setReadable(true, true);
            tmp.toFile().setWritable(false, false);
            tmp.toFile().setWritable(true, true);
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: failed to write " + storePath + ": " + e.getMessage());
        }
    }
}
