package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.AppUser;
import com.hasan.apiwatch.enums.UserRole;
import com.hasan.apiwatch.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService, ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String viewerUsername;
    private final String viewerPassword;

    public AppUserDetailsService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${apiwatch.auth.admin.username}") String adminUsername,
            @Value("${apiwatch.auth.admin.password}") String adminPassword,
            @Value("${apiwatch.auth.viewer.username}") String viewerUsername,
            @Value("${apiwatch.auth.viewer.password}") String viewerPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.viewerUsername = viewerUsername;
        this.viewerPassword = viewerPassword;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User was not found"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser(adminUsername, adminPassword, UserRole.ADMIN);
        seedUser(viewerUsername, viewerPassword, UserRole.VIEWER);
    }

    private void seedUser(String username, String password, UserRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseGet(AppUser::new);
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }
}
