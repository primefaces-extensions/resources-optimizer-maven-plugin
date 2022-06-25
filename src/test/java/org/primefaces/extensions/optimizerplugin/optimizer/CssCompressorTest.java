package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CssCompressorTest {

    public static  List<Path> provideCssFiles() throws IOException {
        return Files.walk(Paths.get("target/test-classes/"))
                    .filter(p -> p.toString().endsWith(".css"))
                    .collect(Collectors.toList());
    }

    @ParameterizedTest
    @MethodSource("provideCssFiles")
    public void compress(Path cssFile) throws IOException {
        // Arrange
        Reader reader = new InputStreamReader(Files.newInputStream(cssFile));
        CssCompressor compressor = new CssCompressor(reader);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Act
        try (OutputStreamWriter out = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            compressor.compress(out, 10000);
            out.flush();
        }

        // Assert
        byte[] expectedFileBytes = Files.readAllBytes(Paths.get("target/test-classes/" +cssFile.getFileName() + ".min"));
        String expected = new String(expectedFileBytes, StandardCharsets.UTF_8);
        String actual = outputStream.toString(StandardCharsets.UTF_8);

        assertEquals(expected, actual, "The content in the strings should match");
    }


}