package com.autopilot.config;

import com.autopilot.entity.User;
import com.autopilot.repository.UserRepository;
import com.autopilot.service.auth.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name  = oauthUser.getAttribute("name");
        String sub   = oauthUser.getAttribute("sub");   // Google's unique user ID

        // Upsert user — find by Google sub or email
        User user = userRepository.findByProviderAndProviderId("GOOGLE", sub)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .orElseGet(() -> {
                            User u = new User();
                            u.setName(name);
                            u.setEmail(email);
                            u.setProvider("GOOGLE");
                            u.setProviderId(sub);
                            return userRepository.save(u);
                        }));

        // Ensure provider info is set (existing LOCAL user signed in with Google)
        if (!"GOOGLE".equals(user.getProvider())) {
            user.setProvider("GOOGLE");
            user.setProviderId(sub);
            userRepository.save(user);
        }

        String token = jwtService.generateToken(user);

        // Redirect to the Next.js callback page with the JWT in the query string
        String redirectUrl = "http://localhost:3001/auth/callback?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }
}
