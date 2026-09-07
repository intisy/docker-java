package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.RegistryBody;
import io.github.intisy.docker.registry.RegistryHttp;
import io.github.intisy.docker.registry.RegistryResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records every call and answers from a queue keyed by "METHOD url". Nothing here touches a
 * socket, which is what lets the registry tests run in CI.
 */
public final class FakeRegistryHttp implements RegistryHttp {
    private final List<String> calls = new ArrayList<String>();
    private final Map<String, List<RegistryResponse>> answers =
            new LinkedHashMap<String, List<RegistryResponse>>();
    private final Map<String, byte[]> sentBodies = new LinkedHashMap<String, byte[]>();

    public List<String> calls() {
        return new ArrayList<String>(calls);
    }

    public byte[] bodySentTo(String methodAndUrl) {
        return sentBodies.get(methodAndUrl);
    }

    public void answer(String methodAndUrl, RegistryResponse response) {
        List<RegistryResponse> queued = answers.get(methodAndUrl);
        if (queued == null) {
            queued = new ArrayList<RegistryResponse>();
            answers.put(methodAndUrl, queued);
        }
        queued.add(response);
    }

    public void answerText(String methodAndUrl, int status, String body, String... headerPairs) {
        Map<String, String> headers = new HashMap<String, String>();
        for (int i = 0; i + 1 < headerPairs.length; i += 2) {
            headers.put(headerPairs[i], headerPairs[i + 1]);
        }
        answer(methodAndUrl, new RegistryResponse(status, headers, body.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public RegistryResponse send(String method, String url, Map<String, String> headers, RegistryBody body)
            throws IOException {
        String key = method + " " + url;
        calls.add(key);
        if (body != null && body.bytes() != null) {
            sentBodies.put(key, body.bytes());
        }
        List<RegistryResponse> queued = answers.get(key);
        if (queued == null || queued.isEmpty()) {
            throw new IOException("no fake answer queued for " + key + "; queued keys are " + answers.keySet());
        }
        return queued.size() == 1 ? queued.get(0) : queued.remove(0);
    }
}
