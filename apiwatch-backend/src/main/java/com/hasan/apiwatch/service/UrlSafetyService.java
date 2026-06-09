package com.hasan.apiwatch.service;

import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.exception.UnsafeTargetException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UrlSafetyService {

    private final boolean blockPrivateTargets;
    private final Set<String> allowlist;

    public UrlSafetyService(
            @Value("${apiwatch.security.block-private-targets:true}") boolean blockPrivateTargets,
            @Value("${apiwatch.security.private-target-allowlist:}") String allowlist
    ) {
        this.blockPrivateTargets = blockPrivateTargets;
        this.allowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String validateConfiguration(String value) {
        URI uri = parse(value, true);
        if (!blockPrivateTargets || isAllowlisted(uri.getHost())) {
            return uri.toString();
        }
        try {
            assertAddressesAllowed(uri.getHost(), true);
        } catch (UnknownHostException ignored) {
            // DNS failures are valid monitoring scenarios and are classified at check time.
        }
        return uri.toString();
    }

    public void assertRequestAllowed(String value) throws UnknownHostException {
        URI uri = parse(value, false);
        if (!blockPrivateTargets || isAllowlisted(uri.getHost())) {
            return;
        }
        assertAddressesAllowed(uri.getHost(), false);
    }

    private URI parse(String value, boolean configuration) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw unsafe(configuration, "URL must be an HTTP or HTTPS target without user info");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw unsafe(configuration, "URL is invalid");
        }
    }

    private void assertAddressesAllowed(String host, boolean configuration)
            throws UnknownHostException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivateOrInternal(address)) {
                throw unsafe(
                        configuration,
                        "Target resolves to a private or internal network address"
                );
            }
        }
    }

    private RuntimeException unsafe(boolean configuration, String message) {
        return configuration
                ? new BadRequestException(message)
                : new UnsafeTargetException(message);
    }

    private boolean isAllowlisted(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return allowlist.stream().anyMatch(entry ->
                normalized.equals(entry)
                        || (entry.startsWith("*.")
                        && normalized.endsWith(entry.substring(1))
                        && normalized.length() > entry.length() - 1)
        );
    }

    boolean isPrivateOrInternal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19));
    }
}
