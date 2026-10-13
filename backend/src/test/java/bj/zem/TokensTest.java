package bj.zem;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokensTest {
    @Test void hashingMatchesSha256KnownVectorAndDoesNotStoreRawToken() {
        assertThat(Tokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Tokens.hash("ABC")).isNotEqualTo(Tokens.hash("abc"));
    }
    @Test void sessionTokensCarry256RandomBitsAndAreUrlSafe() {
        String first=Tokens.random(),second=Tokens.random();
        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
    }
}
