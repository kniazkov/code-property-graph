package com.kniazkov.cpg.parser.cpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParseExceptionTest {

    @Test
    void carriesMessageAndOffset() {
        ParseException exception = new ParseException("bad literal", 7);

        assertEquals("bad literal", exception.getMessage());
        assertEquals(7, exception.offset());
    }
}
