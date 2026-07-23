package dev.incusspawn.proxy;

import dev.incusspawn.tool.CodexSetup;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class MitmProxyCodexTest {

    @Test
    void extractExpFromValidJwt() {
        var jwt = CodexSetup.mintAccessTokenWithExp(1700000000L);
        assertEquals(1700000000L, MitmProxy.extractExpFromJwt(jwt));
    }

    @Test
    void extractExpFromPlaceholderIdToken() {
        var jwt = CodexSetup.mintPlaceholderIdToken();
        assertEquals(4102444800L, MitmProxy.extractExpFromJwt(jwt));
    }

    @Test
    void extractExpFromNullReturnsFallback() {
        var result = MitmProxy.extractExpFromJwt(null);
        var now = System.currentTimeMillis() / 1000;
        assertTrue(result >= now && result <= now + 3601);
    }

    @Test
    void extractExpFromBlankReturnsFallback() {
        var result = MitmProxy.extractExpFromJwt("");
        var now = System.currentTimeMillis() / 1000;
        assertTrue(result >= now && result <= now + 3601);
    }

    @Test
    void extractExpFromInvalidJwtReturnsFallback() {
        var result = MitmProxy.extractExpFromJwt("not.a.jwt");
        var now = System.currentTimeMillis() / 1000;
        assertTrue(result >= now && result <= now + 3601);
    }

    @Test
    void extractExpFromJwtWithoutExpReturnsFallback() {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var header = enc.encodeToString("{\"alg\":\"none\"}".getBytes());
        var payload = enc.encodeToString("{\"sub\":\"test\"}".getBytes());
        var jwt = header + "." + payload + ".sig";

        var result = MitmProxy.extractExpFromJwt(jwt);
        var now = System.currentTimeMillis() / 1000;
        assertTrue(result >= now && result <= now + 3601);
    }

    @Test
    void extractExpFromRealWorldStyleJwt() {
        var enc = Base64.getUrlEncoder().withoutPadding();
        var header = enc.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        var payload = enc.encodeToString("{\"exp\":1800000000,\"sub\":\"user123\"}".getBytes());
        var jwt = header + "." + payload + ".realsig";

        assertEquals(1800000000L, MitmProxy.extractExpFromJwt(jwt));
    }
}
