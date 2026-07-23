package com.codesense.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Rewrites an invalid/forged X-User-Id header to "absent" before it reaches
 * any controller - see {@link UserIdentityService} for why. Controllers and
 * services don't change at all: {@code @RequestHeader(required = false)}
 * already treats a missing header as null, which is exactly what this
 * degrades an invalid one to.
 */
@Component
public class UserIdentityFilter extends OncePerRequestFilter {

    static final String HEADER = "X-User-Id";

    private final UserIdentityService identityService;

    public UserIdentityFilter(UserIdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw == null || identityService.validateOrNull(raw) != null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HeaderStrippingWrapper(request), response);
    }

    private static final class HeaderStrippingWrapper extends HttpServletRequestWrapper {
        HeaderStrippingWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames()).stream()
                    .filter(n -> !HEADER.equalsIgnoreCase(n))
                    .toList();
            return Collections.enumeration(names);
        }
    }
}
