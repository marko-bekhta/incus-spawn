package dev.incusspawn.tool;

import dev.incusspawn.config.EnvEntry;
import dev.incusspawn.config.SpawnConfig;
import dev.incusspawn.incus.Container;

import java.util.Base64;
import java.util.List;
import java.util.Map;

public class CodexSetup implements ToolSetup {

    public static final String PLACEHOLDER_API_KEY = "sk-codex-placeholder";
    public static final String PLACEHOLDER_REFRESH_TOKEN = "codex-rt-placeholder";
    public static final String PLACEHOLDER_ACCOUNT_ID = "isx-placeholder";

    static final String SYSTEM_CONFIG_PATH = "/etc/codex/config.toml";
    static final String USER_CONFIG_PATH = "/home/agentuser/.codex/config.toml";
    static final String AUTH_JSON_PATH = "/home/agentuser/.codex/auth.json";

    private static final String CA_CERT_PATH =
            "/etc/pki/ca-trust/source/anchors/incus-spawn-mitm.crt";

    @Override
    public String name() {
        return "codex";
    }

    @Override
    public List<String> packages() {
        return List.of("nodejs");
    }

    @Override
    public List<ToolDef.ActionEntry> actions() {
        var a = new ToolDef.ActionEntry();
        a.setLabel("Codex");
        a.setType("shell");
        a.setCommand("codex");
        a.setAutoReturn(true);
        return List.of(a);
    }

    @Override
    public Map<String, ToolDef.ParameterDef> parameters() {
        var params = new java.util.LinkedHashMap<String, ToolDef.ParameterDef>();

        var model = new ToolDef.ParameterDef();
        model.setType("string");
        model.setDescription("OpenAI model ID (e.g. o3, gpt-4o, codex-mini)");
        model.setOptional(true);
        model.setReconfigurable(true);
        params.put("model", model);

        return params;
    }

    @Override
    public List<EnvEntry> envEntries(Map<String, String> resolvedParams) {
        var codex = SpawnConfig.load().getCodex();
        var entries = new java.util.ArrayList<EnvEntry>();
        entries.add(EnvEntry.set("CODEX_CA_CERTIFICATE", CA_CERT_PATH));
        if (codex.isOauthMode()) {
            // OAuth mode: auth.json holds placeholder tokens, no API key env var needed
        } else {
            entries.add(EnvEntry.set("OPENAI_API_KEY", PLACEHOLDER_API_KEY));
        }
        return entries;
    }

    @Override
    public void install(Container c, Map<String, String> resolvedParams) {
        installBinary(c);
        configureSettings(c, resolvedParams);
    }

    @Override
    public void reconfigure(Container c, Map<String, String> resolvedParams) {
        configureSettings(c, resolvedParams);
    }

    private void installBinary(Container c) {
        System.out.println("Installing Codex CLI...");
        c.exec("npm", "install", "-g", "@openai/codex")
                .assertSuccess("Failed to npm install Codex");
    }

    private void configureSettings(Container c, Map<String, String> params) {
        var codex = SpawnConfig.load().getCodex();
        System.out.println("Configuring Codex for container use...");

        var systemConfig = """
                approval_policy = "never"
                sandbox_mode = "danger-full-access"
                """;
        c.sh("mkdir -p /etc/codex");
        c.writeFile(SYSTEM_CONFIG_PATH, systemConfig);

        var userConfig = new StringBuilder();
        var model = params.get("model");
        if (model != null && !model.isBlank()) {
            userConfig.append("model = \"").append(model).append("\"\n");
        }
        c.sh("mkdir -p /home/agentuser/.codex");
        c.writeFile(USER_CONFIG_PATH, userConfig.toString());

        if (codex.isOauthMode()) {
            var idToken = mintPlaceholderIdToken();
            var accessToken = mintPlaceholderAccessToken();
            var authJson = buildAuthJson(idToken, accessToken);
            c.writeFile(AUTH_JSON_PATH, authJson);
        }

        c.chown("/home/agentuser/.codex", "agentuser:agentuser");
    }

    static String buildAuthJson(String idToken, String accessToken) {
        return """
                {
                  "auth_mode": "chatgpt",
                  "tokens": {
                    "id_token": "%s",
                    "access_token": "%s",
                    "refresh_token": "%s",
                    "account_id": "%s"
                  }
                }
                """.formatted(idToken, accessToken, PLACEHOLDER_REFRESH_TOKEN, PLACEHOLDER_ACCOUNT_ID);
    }

    /**
     * Mint a minimal fake id_token JWT with placeholder identity claims.
     * Codex parses this for plan type and account info but never verifies the signature.
     */
    public static String mintPlaceholderIdToken() {
        var payload = """
                {"email":"placeholder@isx",\
                "https://api.openai.com/auth":{\
                "chatgpt_plan_type":"plus",\
                "chatgpt_user_id":"isx-placeholder",\
                "chatgpt_account_id":"isx-placeholder",\
                "chatgpt_account_is_fedramp":false},\
                "exp":4102444800}""";
        return mintJwt(payload);
    }

    /**
     * Mint a fake access_token JWT with a far-future expiry.
     * The proxy replaces this with the real token on every request.
     * Codex reads the exp claim to decide when to proactively refresh.
     */
    static String mintPlaceholderAccessToken() {
        return mintAccessTokenWithExp(4102444800L);
    }

    /**
     * Mint a fake access_token JWT with a specific exp value.
     * Used by the proxy to match the real token's expiry in placeholder responses.
     */
    public static String mintAccessTokenWithExp(long exp) {
        return mintJwt("{\"exp\":" + exp + "}");
    }

    static String mintJwt(String payloadJson) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        var payload = enc.encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".placeholder";
    }
}
