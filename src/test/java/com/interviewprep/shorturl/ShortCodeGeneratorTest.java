package com.interviewprep.shorturl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesUrlSafeCodesWithinMaxLength() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.generate())
                    .hasSize(ShortCodeGenerator.CODE_LENGTH)
                    .hasSizeLessThanOrEqualTo(ShortUrl.SHORT_CODE_MAX_LENGTH)
                    .matches("[A-Za-z0-9]+");
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        // Birthday bound for 1,000 draws from 62^7 values: chance of any duplicate is about 1 in 7 million.
        assertThat(codes).hasSize(1_000);
    }
}
