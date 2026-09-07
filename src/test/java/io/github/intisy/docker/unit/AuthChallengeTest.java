package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.AuthChallenge;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
public class AuthChallengeTest {
    private static final String DOCKER_HUB_HEADER =
            "Bearer realm=\"https://auth.docker.io/token\",service=\"registry.docker.io\"";

    @Test
    public void parsesRealmAndService() {
        AuthChallenge challenge = AuthChallenge.parse(DOCKER_HUB_HEADER);
        assertEquals("https://auth.docker.io/token", challenge.realm());
        assertEquals("registry.docker.io", challenge.service());
    }

    @Test
    public void buildsTheTokenUrlWithServiceAndScope() {
        AuthChallenge challenge = AuthChallenge.parse(DOCKER_HUB_HEADER);
        assertEquals("https://auth.docker.io/token?service=registry.docker.io"
                        + "&scope=repository%3Alibrary%2Feclipse-temurin%3Apull",
                challenge.tokenUrl("repository:library/eclipse-temurin:pull"));
    }

    @Test
    public void headerWithAnEmbeddedScopeIsKept() {
        AuthChallenge challenge = AuthChallenge.parse(
                "Bearer realm=\"https://auth.example.com/token\",service=\"reg\",scope=\"repository:a/b:pull\"");
        assertEquals("repository:a/b:pull", challenge.scope());
    }

    @Test
    public void bearerHeaderWithoutRealmIsNotAChallenge() {
        assertNull(AuthChallenge.parse("Bearer service=\"registry.docker.io\""));
    }

    /**
     * A registry that answers Basic, or no challenge at all, must read as "no bearer flow here"
     * rather than as a malformed bearer challenge. The in-cluster registry is unauthenticated and
     * sends no challenge at all, so this is the path it takes.
     */
    @Test
    public void nonBearerHeaderIsNotAChallenge() {
        assertNull(AuthChallenge.parse("Basic realm=\"registry\""));
        assertNull(AuthChallenge.parse(null));
    }
}
