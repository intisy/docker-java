package io.github.intisy.docker.registry;

import java.io.IOException;
import java.util.Map;

/**
 * The one seam between the registry client and the network. Implemented for real by
 * {@link UrlRegistryHttp} and by a recording fake in the tests.
 *
 * @author Finn Birich
 */
public interface RegistryHttp {
    RegistryResponse send(String method, String url, Map<String, String> headers, RegistryBody body)
            throws IOException;
}
