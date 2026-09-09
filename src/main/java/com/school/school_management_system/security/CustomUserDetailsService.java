package com.school.school_management_system.security;

import com.school.school_management_system.entity.User;
import com.school.school_management_system.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalised = email == null ? "" : email.trim();
        User user = userRepository.findByEmailIgnoreCase(normalised)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + normalised));
        return new CustomUserDetails(user);
    }
}
