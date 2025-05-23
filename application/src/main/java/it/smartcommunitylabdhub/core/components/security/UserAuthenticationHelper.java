package it.smartcommunitylabdhub.core.components.security;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserAuthenticationHelper {

    public static UserAuthentication<?> getUserAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            return null;
        }

        if (auth instanceof UserAuthentication) {
            return (UserAuthentication<?>) auth;
        }

        //workaround: inflate basic auth tokens
        //TODO use authManager to produce proper authentication
        //NOTE: this flow does not propagate ROLE_ADMIN authority
        if (auth instanceof UsernamePasswordAuthenticationToken) {
            UserAuthentication<UsernamePasswordAuthenticationToken> user = new UserAuthentication<>(
                (UsernamePasswordAuthenticationToken) auth,
                auth.getName(),
                auth.getAuthorities()
            );

            //update context
            SecurityContextHolder.getContext().setAuthentication(user);

            return user;
        }

        return null;
    }

    private UserAuthenticationHelper() {}
}
