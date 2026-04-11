package com.autopilot.service.auth;

import com.autopilot.dto.AuthResponse;
import com.autopilot.dto.LoginRequest;
import com.autopilot.dto.RegisterRequest;
import com.autopilot.entity.User;
import com.autopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setProvider("LOCAL");
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (user.getPassword() == null) {
            throw new RuntimeException("This account uses Google Sign-In. Please use Google to log in.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getName(), user.getEmail());
    }
}
