package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

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

}