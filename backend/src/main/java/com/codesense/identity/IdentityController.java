package com.codesense.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstraps a no-login client's identity: the frontend calls this once
 * (caching the result in localStorage) instead of minting its own id, so
 * every X-User-Id the backend ever sees is one it actually issued - see
 * {@link UserIdentityService}.
 */
@RestController
public class IdentityController {

    private final UserIdentityService identityService;

    public IdentityController(UserIdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/api/identity")
    public IdentityResponse issue() {
        return new IdentityResponse(identityService.issue());
    }
}
