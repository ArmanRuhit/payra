package com.payra.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Shared hashing policy for small secrets: OTP code, PINs, refresh tokens.
 * 
 * <p>BCrypt, cost 12 - deliberately slow. A 6-digit code has only 1,000,000
 * possibilities; the work factor is what makes an offline brute force against
 * a leaked hash impractical one attempt caps and TTLs have done their part.
 * 
 */
@Configuration 
public class PasswordEncoderConfig {
    
    @Bean 
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
