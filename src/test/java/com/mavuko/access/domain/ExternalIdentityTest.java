package com.mavuko.access.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ExternalIdentityTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void requiresBothOpaqueIdentityComponents(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentity(invalid, "User"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentity("https://issuer.example/", invalid));
    }

    @Test
    void preservesExactIdentityWithoutEmailLinkingOrNormalization() {
        var identity = new ExternalIdentity("https://Issuer.example/", " Subject ");
        assertEquals("https://Issuer.example/", identity.issuer());
        assertEquals(" Subject ", identity.subject());
        assertNotEquals(identity, new ExternalIdentity("https://Issuer.example/", " subject "));
    }
}
