package com.quipux.playlist;

import java.util.stream.Stream;

final class SpecialNames {

    private SpecialNames() {
    }

    static Stream<String> all() {
        return Stream.of(
                "Mis favoritas", "Canción número 1", "ñandú", "a;b", "a+b", "a&b", "a?b", "a#b", "100%", "%2F",
                "a=b,c", " con espacios ", "...", "🎵 musica");
    }
}
