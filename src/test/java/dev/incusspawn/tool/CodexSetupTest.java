package dev.incusspawn.tool;

import dev.incusspawn.config.SpawnConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodexSetupTest {

    @TempDir
    Path tempDir;

    private String originalUserHome;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void nameIsCodex() {
        assertEquals("codex", new CodexSetup().name());
    }

    @Test
    void packagesIncludesNodejs() {
        assertTrue(new CodexSetup().packages().contains("nodejs"));
    }

    @Test
    void actionsContainsCodexAction() {
        var actions = new CodexSetup().actions();
        assertEquals(1, actions.size());
        assertEquals("Codex", actions.get(0).getLabel());
        assertEquals("codex", actions.get(0).getCommand());
        assertTrue(actions.get(0).isAutoReturn());
    }

    @Test
    void parametersDeclaresOptionalReconfigurableModel() {
        var params = new CodexSetup().parameters();
        assertTrue(params.containsKey("model"));
        var model = params.get("model");
        assertEquals("string", model.getType());
        assertTrue(model.isOptional());
        assertTrue(model.isReconfigurable());
    }

    @Test
    void envEntriesAlwaysIncludesCaCertificate() {
        var entries = new CodexSetup().envEntries(Map.of());
        assertTrue(entries.stream().anyMatch(e ->
                "CODEX_CA_CERTIFICATE".equals(e.getName())));
    }

    @Test
    void envEntriesIncludesApiKeyInApiKeyMode() {
        var config = SpawnConfig.load();
        config.getCodex().setApiKey("sk-test-key");
        config.getCodex().setOauth(false);
        config.save();

        var entries = new CodexSetup().envEntries(Map.of());
        assertTrue(entries.stream().anyMatch(e ->
                "OPENAI_API_KEY".equals(e.getName()) && CodexSetup.PLACEHOLDER_API_KEY.equals(e.getValue())));
    }

    @Test
    void envEntriesOmitsApiKeyInOauthMode() {
        var config = SpawnConfig.load();
        config.getCodex().setOauth(true);
        config.save();

        var entries = new CodexSetup().envEntries(Map.of());
        assertFalse(entries.stream().anyMatch(e ->
                "OPENAI_API_KEY".equals(e.getName())));
    }

    @Test
    void mintPlaceholderIdTokenIsValidJwt() {
        var jwt = CodexSetup.mintPlaceholderIdToken();
        var parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT must have 3 parts");

        var decoder = Base64.getUrlDecoder();
        var header = new String(decoder.decode(parts[0]));
        assertTrue(header.contains("\"alg\":\"none\""));

        var payload = new String(decoder.decode(parts[1]));
        assertTrue(payload.contains("chatgpt_plan_type"));
        assertTrue(payload.contains("isx-placeholder"));
        assertTrue(payload.contains("4102444800"));
    }

    @Test
    void mintAccessTokenWithExpContainsExpClaim() {
        var jwt = CodexSetup.mintAccessTokenWithExp(1700000000L);
        var parts = jwt.split("\\.");
        assertEquals(3, parts.length);

        var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(payload.contains("1700000000"));
    }

    @Test
    void buildAuthJsonContainsAllPlaceholders() {
        var idToken = CodexSetup.mintPlaceholderIdToken();
        var accessToken = CodexSetup.mintPlaceholderAccessToken();
        var json = CodexSetup.buildAuthJson(idToken, accessToken);

        assertTrue(json.contains("\"auth_mode\": \"chatgpt\""));
        assertTrue(json.contains(CodexSetup.PLACEHOLDER_REFRESH_TOKEN));
        assertTrue(json.contains(CodexSetup.PLACEHOLDER_ACCOUNT_ID));
        assertTrue(json.contains(idToken));
        assertTrue(json.contains(accessToken));
    }

    @Test
    void placeholderAccessTokenHasFarFutureExp() {
        var jwt = CodexSetup.mintPlaceholderAccessToken();
        var parts = jwt.split("\\.");
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(payload.contains("4102444800"));
    }
}
