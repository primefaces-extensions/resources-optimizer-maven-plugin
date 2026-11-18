package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ClosureCompilerOptimizerTest {

	public static List<Path> provideJsFiles() throws IOException {
		return Files.walk(Paths.get("target/test-classes/")).filter(p -> p.toString().endsWith(".js"))
				.collect(Collectors.toList());
	}

	@ParameterizedTest
	@MethodSource("provideJsFiles")
	public void testGzippingFiles(Path jsFile) throws IOException {
		File testFile = jsFile.toFile();
		ClosureCompilerOptimizer optimizer = new ClosureCompilerOptimizer(null);
		File gzipped = optimizer.gzipFile(testFile);

		// Verify
		assertNotNull(gzipped);
		assertTrue(gzipped.exists());
		assertTrue(gzipped.getName().endsWith(".gz"));
		assertTrue(gzipped.length() > 0);
	}

	@Test
	public void testAlreadyGzippedFileIsSkipped(@TempDir Path tempDir) throws IOException {
		Path gzFile = tempDir.resolve("script.js.gz");
		Files.write(gzFile, new byte[] { 1, 2, 3 });
		ClosureCompilerOptimizer optimizer = new ClosureCompilerOptimizer(null);

		File result = optimizer.gzipFile(gzFile.toFile());

		// Verify
		assertNull(result);
		assertFalse(Files.exists(tempDir.resolve("script.js.gz.gz")));
	}

}