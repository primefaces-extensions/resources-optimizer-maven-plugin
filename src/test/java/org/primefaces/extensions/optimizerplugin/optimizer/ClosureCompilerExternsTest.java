package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetJsAdapter;

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.Environment;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

/**
 * Tests for externs with ADVANCED_OPTIMIZATIONS, see issue #358.
 */
public class ClosureCompilerExternsTest {

	private static final String BROWSER_JS = "window.addEventListener('load', function(){"
			+ " document.title = String(42); console.log(JSON.stringify({a:1})); });";

	private static final String LIB_JS = "myLib.init({debugMode: true}); myLib.render(document.body);";

	private static final String LIB_EXTERNS = "/** @externs */\n" + "var myLib = {};\n"
			+ "/** @record */ function MyLibConfig() {}\n" + "/** @type {boolean} */ MyLibConfig.prototype.debugMode;\n"
			+ "/** @param {MyLibConfig} config */ myLib.init = function(config) {};\n"
			+ "/** @param {Element} el */ myLib.render = function(el) {};\n";

	private String optimize(Path dir, String js, List<SourceFile> externs) throws Exception {
		Path file = dir.resolve("test.js");
		Files.write(file, js.getBytes(StandardCharsets.UTF_8));
		ResourcesSetJsAdapter rsa = new ResourcesSetJsAdapter(dir.toFile(), Collections.singleton(file.toFile()),
				null, CompilationLevel.ADVANCED_OPTIMIZATIONS, WarningLevel.DEFAULT, null, "UTF-8", false, null,
				LanguageMode.ECMASCRIPT_2015, LanguageMode.NO_TRANSPILE, false, false, false, true, externs);
		new ClosureCompilerOptimizer(new SystemStreamLog()).optimize(rsa);
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	private static List<SourceFile> browserExterns() throws IOException {
		return new ArrayList<>(CommandLineRunner.getBuiltinExterns(Environment.BROWSER));
	}

	@Test
	public void testNoExternsFailsInAdvancedMode(@TempDir Path dir) {
		assertThrows(MojoExecutionException.class, () -> optimize(dir, BROWSER_JS, Collections.emptyList()));
	}

	@Test
	public void testBrowserExternsInAdvancedMode(@TempDir Path dir) throws Exception {
		String result = assertDoesNotThrow(() -> optimize(dir, BROWSER_JS, browserExterns()));
		assertTrue(result.contains("window.addEventListener"), result);
		assertTrue(result.contains("document.title"), result);
	}

	@Test
	public void testCustomExternsKeepLibraryNames(@TempDir Path dir) throws Exception {
		List<SourceFile> externs = browserExterns();
		externs.add(SourceFile.fromCode("mylib-externs.js", LIB_EXTERNS));

		String result = optimize(dir, LIB_JS, externs);

		// names declared in the externs must not be renamed
		assertTrue(result.contains("myLib.init"), result);
		assertTrue(result.contains("myLib.render"), result);
		assertTrue(result.contains("debugMode"), result);
	}
}
