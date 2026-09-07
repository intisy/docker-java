package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.UrlRegistryHttp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
public class UrlRegistryHttpTest {

    @Test
    public void scopeIsDerivedFromAManifestPath() {
        assertEquals("repository:library/eclipse-temurin:pull", UrlRegistryHttp.scopeFor(
                "https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jre"));
    }

    @Test
    public void scopeIsDerivedFromABlobPath() {
        assertEquals("repository:library/eclipse-temurin:pull", UrlRegistryHttp.scopeFor(
                "https://registry-1.docker.io/v2/library/eclipse-temurin/blobs/sha256:aa"));
    }

    @Test
    public void tokenFieldIsReadFromEitherName() {
        assertEquals("abc", UrlRegistryHttp.jsonStringField("{\"token\":\"abc\"}", "token"));
        assertEquals("xyz", UrlRegistryHttp.jsonStringField("{\"access_token\":\"xyz\"}", "access_token"));
        assertNull(UrlRegistryHttp.jsonStringField("{\"expires_in\":300}", "token"));
    }
}
