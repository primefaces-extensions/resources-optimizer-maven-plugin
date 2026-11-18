package org.primefaces.extensions.optimizerplugin.optimizer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetJsAdapter;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.WarningLevel;

/**
 * Tests for polyfill injection, see issue #355.
 */
public class ClosureCompilerPolyfillTest {

	// uses Array.prototype.find (ES6) which triggers a polyfill for ES5 output
	private static final String JS = "function f(arr){return arr.find(function(x){return x>1;});} window.f=f;";

	private String optimize(Path dir, LanguageMode in, LanguageMode out, boolean rewritePolyfills)
			throws IOException, MojoExecutionException {
		Path js = dir.resolve("test.js");
		Files.write(js, JS.getBytes(StandardCharsets.UTF_8));
		File file = js.toFile();
		ResourcesSetJsAdapter rsa = new ResourcesSetJsAdapter(dir.toFile(), Collections.singleton(file), null,
				CompilationLevel.SIMPLE_OPTIMIZATIONS, WarningLevel.QUIET, null, "UTF-8", false, null, in, out,
				false, false, false, rewritePolyfills);
		new ClosureCompilerOptimizer(new SystemStreamLog()).optimize(rsa);
		return new String(Files.readAllBytes(js), StandardCharsets.UTF_8);
	}

	@Test
	public void testPolyfillsInjectedForEs5OutputByDefault(@TempDir Path dir) throws Exception {
		String result = optimize(dir, LanguageMode.ECMASCRIPT5_STRICT, LanguageMode.NO_TRANSPILE, true);
		assertTrue(result.contains("$jscomp"), result);
	}

	@Test
	public void testNoPolyfillsWhenRewritePolyfillsDisabled(@TempDir Path dir) throws Exception {
		String result = optimize(dir, LanguageMode.ECMASCRIPT5_STRICT, LanguageMode.NO_TRANSPILE, false);
		assertFalse(result.contains("$jscomp"), result);
		assertTrue(result.contains(".find("), result);
	}

	@Test
	public void testNoPolyfillsForEs2015Output(@TempDir Path dir) throws Exception {
		String result = optimize(dir, LanguageMode.ECMASCRIPT5_STRICT, LanguageMode.ECMASCRIPT_2015, true);
		assertFalse(result.contains("$jscomp"), result);
	}
}
