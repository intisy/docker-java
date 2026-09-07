package io.github.intisy.docker.registry;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed {@code WWW-Authenticate: Bearer ...} challenge.
 *
 * @author Finn Birich
 */
public final class AuthChallenge {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern PARAMETER = Pattern.compile("([a-zA-Z_]+)=\"([^\"]*)\"");

    private final String realm;
    private final String service;
    private final String scope;

    private AuthChallenge(String realm, String service, String scope) {
        this.realm = realm;
        this.service = service;
        this.scope = scope;
    }

    /**
     * @return null when the header is absent or is not a bearer challenge, which is the normal
     * case for an unauthenticated registry rather than an error.
     */
    public static AuthChallenge parse(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        Map<String, String> parameters = new HashMap<String, String>();
        Matcher matcher = PARAMETER.matcher(header.substring(BEARER_PREFIX.length()));
        while (matcher.find()) {
            parameters.put(matcher.group(1), matcher.group(2));
        }
        if (!parameters.containsKey("realm")) {
            return null;
        }
        return new AuthChallenge(parameters.get("realm"), parameters.get("service"), parameters.get("scope"));
    }

    public String realm() {
        return realm;
    }

    public String service() {
        return service;
    }

    public String scope() {
        return scope;
    }

    public String tokenUrl(String requestedScope) {
        StringBuilder url = new StringBuilder(realm);
        url.append('?');
        if (service != null) {
            url.append("service=").append(encode(service)).append('&');
        }
        url.append("scope=").append(encode(requestedScope));
        return url.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is required by every JRE", impossible);
        }
    }
}
