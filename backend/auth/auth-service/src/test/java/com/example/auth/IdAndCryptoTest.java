package com.example.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 纯单测：不加载 Spring / Redis。 */
class IdAndCryptoTest {

    @Test
    void fourteen_digit_id_format() {
        String yyMMdd = "260922";
        long seq = 1L;
        String id = yyMMdd + String.format("%08d", seq);
        assertEquals(14, id.length());
        assertEquals("26092200000001", id);
    }

    @Test
    void bcrypt_matches_admin123_seed() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";
        assertTrue(encoder.matches("admin123", hash));
    }
}
