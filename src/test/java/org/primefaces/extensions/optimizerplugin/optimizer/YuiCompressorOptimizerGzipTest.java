package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetCssAdapter;

/**
 * Tests gzipping of minified CSS files, see issue #318.
 */
public class YuiCompressorOptimizerGzipTest {

	private static final String CSS = ".menu {\n    color: #ff0000;\n    margin: 0px 0px 0px 0px;\n}\n"
			+ ".menu li {\n    color: #ff0000;\n    padding: 0px 0px 0px 0px;\n}\n"
			+ ".menu li a {\n    color: #ff0000;\n    padding: 0px 0px 0px 0px;\n}\n";

	private Path optimize(Path dir, boolean gzip) throws Exception {
		Path css = dir.resolve("style.css");
		Files.write(css, CSS.getBytes(StandardCharsets.UTF_8));
		ResourcesSetCssAdapter rsa = new ResourcesSetCssAdapter(dir.toFile(), Collections.singleton(css.toFile()),
				null, null, "UTF-8", false, null, gzip);
		new YuiCompressorOptimizer(new SystemStreamLog()).optimize(rsa);
		return css;
	}

	@Test
	public void testCssIsGzipped(@TempDir Path dir) throws Exception {
		Path css = optimize(dir, true);
		Path gz = dir.resolve("style.css.gz");

		assertTrue(Files.exists(gz));
		assertTrue(Files.size(gz) < Files.size(css));
		// .gz content equals the minified file
		assertEquals(new String(Files.readAllBytes(css), StandardCharsets.UTF_8), gunzip(gz));
	}

	@Test
	public void testCssNotGzippedByDefault(@TempDir Path dir) throws Exception {
		optimize(dir, false);

		assertFalse(Files.exists(dir.resolve("style.css.gz")));
	}

	private static String gunzip(Path gz) throws IOException {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(gz));
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			in.transferTo(out);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
