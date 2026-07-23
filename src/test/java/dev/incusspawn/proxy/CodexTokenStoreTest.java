package dev.incusspawn.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodexTokenStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundtrip() {
        var store = new CodexTokenStore(tempDir.resolve("tokens.json"));
        var tokens = new CodexTokenStore.Tokens();
        tokens.setAccessToken("at-real");
        tokens.setRefreshToken("rt-real");
        tokens.setIdToken("id-real");
        tokens.setAccountId("acct-123");
        tokens.setFedramp(false);

        store.save(tokens);

        var reloaded = new CodexTokenStore(tempDir.resolve("tokens.json"));
        var loaded = reloaded.get();
        assertNotNull(loaded);
        assertEquals("at-real", loaded.getAccessToken());
        assertEquals("rt-real", loaded.getRefreshToken());
        assertEquals("id-real", loaded.getIdToken());
        assertEquals("acct-123", loaded.getAccountId());
        assertFalse(loaded.isFedramp());
        assertFalse(loaded.getLastRefresh().isBlank());
    }

    @Test
    void hasTokensReturnsFalseWhenEmpty() {
        var store = new CodexTokenStore(tempDir.resolve("nonexistent.json"));
        assertFalse(store.hasTokens());
    }

    @Test
    void hasTokensReturnsTrueAfterSave() {
        var store = new CodexTokenStore(tempDir.resolve("tokens.json"));
        var tokens = new CodexTokenStore.Tokens();
        tokens.setAccessToken("at");
        tokens.setRefreshToken("rt");
        store.save(tokens);

        assertTrue(store.hasTokens());
    }

    @Test
    void recentlyRefreshedReturnsTrueImmediatelyAfterSave() {
        var store = new CodexTokenStore(tempDir.resolve("tokens.json"));
        var tokens = new CodexTokenStore.Tokens();
        tokens.setAccessToken("at");
        tokens.setRefreshToken("rt");
        store.save(tokens);

        assertTrue(store.recentlyRefreshed(30_000));
    }

    @Test
    void recentlyRefreshedReturnsFalseBeforeAnySave() {
        var store = new CodexTokenStore(tempDir.resolve("tokens.json"));
        assertFalse(store.recentlyRefreshed(30_000));
    }

    @Test
    void fileIsCreatedOnSave() {
        var path = tempDir.resolve("tokens.json");
        var store = new CodexTokenStore(path);
        var tokens = new CodexTokenStore.Tokens();
        tokens.setAccessToken("at");
        tokens.setRefreshToken("rt");
        store.save(tokens);

        assertTrue(Files.exists(path));
    }

    @Test
    void nullValuesDefaultToEmptyStrings() {
        var tokens = new CodexTokenStore.Tokens();
        tokens.setAccessToken(null);
        tokens.setRefreshToken(null);
        tokens.setIdToken(null);
        tokens.setAccountId(null);
        tokens.setLastRefresh(null);

        assertEquals("", tokens.getAccessToken());
        assertEquals("", tokens.getRefreshToken());
        assertEquals("", tokens.getIdToken());
        assertEquals("", tokens.getAccountId());
        assertEquals("", tokens.getLastRefresh());
        assertFalse(tokens.hasTokens());
    }

    @Test
    void getReturnsNullWhenNoFileExists() {
        var store = new CodexTokenStore(tempDir.resolve("missing.json"));
        assertNull(store.get());
    }

    @Test
    void saveOverwritesPreviousTokens() {
        var store = new CodexTokenStore(tempDir.resolve("tokens.json"));

        var first = new CodexTokenStore.Tokens();
        first.setAccessToken("at-1");
        first.setRefreshToken("rt-1");
        store.save(first);

        var second = new CodexTokenStore.Tokens();
        second.setAccessToken("at-2");
        second.setRefreshToken("rt-2");
        store.save(second);

        var loaded = store.get();
        assertEquals("at-2", loaded.getAccessToken());
        assertEquals("rt-2", loaded.getRefreshToken());
    }
}
