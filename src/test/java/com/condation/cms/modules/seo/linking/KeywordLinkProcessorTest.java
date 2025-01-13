package com.condation.cms.modules.seo.linking;

/*-
 * #%L
 * seo-module
 * %%
 * Copyright (C) 2024 - 2025 CondationCMS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import com.condation.cms.api.Constants;
import com.condation.cms.modules.autolinks.linking.KeywordLinkProcessor;
import com.condation.cms.modules.autolinks.linking.ProcessingConfig;
import com.condation.cms.api.cache.ICache;
import com.condation.cms.api.db.ContentNode;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.db.DBFileSystem;
import com.condation.cms.api.feature.features.CurrentNodeFeature;
import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.module.CMSModuleContext;
import com.condation.cms.api.module.CMSRequestContext;
import com.condation.cms.api.request.RequestContext;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.mockito.Mockito;

class KeywordLinkProcessorTest {

	private KeywordLinkProcessor processor;
	private ProcessingConfig defaultConfig;

	@BeforeEach
	void setUp() {
		defaultConfig = new ProcessingConfig.Builder()
				.setCaseSensitive(false)
				.setWholeWordsOnly(true)
				.setLinkFrequency(10)
				.setTotalLinkCount(100)
				.build();
		iCache = new ICache<String, String>() {
			@Override
			public void put(String key, String value) {
			}

			@Override
			public String get(String key) {
				return null;
			}

			@Override
			public boolean contains(String key) {
				return false;
			}

			@Override
			public void invalidate() {
			}

			@Override
			public void invalidate(String key) {
			}
		};
		processor = new KeywordLinkProcessor(defaultConfig, iCache, createModuleContext());
	}
	private ICache<String, String> iCache;

	private CMSModuleContext createModuleContext () {
		var db = Mockito.mock(DB.class);
		var fileSystem = Mockito.mock(DBFileSystem.class);
		var contentBase = Path.of("/content/");
		
		Mockito.when(fileSystem.resolve(Constants.Folders.CONTENT)).thenReturn(contentBase);
		Mockito.when(db.getFileSystem()).thenReturn(fileSystem);
		
		CMSModuleContext moduleContext = new CMSModuleContext();
		moduleContext.add(DBFeature.class, new DBFeature(db));
		
		return moduleContext;
	}
	
	private CMSRequestContext createRequestContext () {
		return createRequestContext("/");
	}
	
	private CMSRequestContext createRequestContext (String currentNodeURI) {
		var db = Mockito.mock(DB.class);
		var fileSystem = Mockito.mock(DBFileSystem.class);
		var currentNode = Mockito.mock(ContentNode.class);
		var contentBase = Path.of("/content/");
		
		Mockito.when(fileSystem.resolve(Constants.Folders.CONTENT)).thenReturn(contentBase);
		Mockito.when(currentNode.uri()).thenReturn(currentNodeURI);
		Mockito.when(db.getFileSystem()).thenReturn(fileSystem);
		
		RequestContext requestContext = new RequestContext();
		requestContext.add(DBFeature.class, new DBFeature(db));
		requestContext.add(CurrentNodeFeature.class, new CurrentNodeFeature(currentNode));
		return new CMSRequestContext(requestContext);
	}
	
	@Nested
	@DisplayName("Basic Functionality Tests")
	class BasicTests {

		@Test
		@DisplayName("Should replace single keyword with link")
		void shouldReplaceSingleKeyword() {
			processor.addKeywords("https://test.com", "Java");
			String result = processor.process("<div>Learn Java programming</div>", createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">Java</a>")
					.startsWith("<div>")
					.endsWith("</div>")
					.containsSubsequence("Learn", "Java", "programming");
		}

		@Test
		@DisplayName("Should handle multiple keywords for same URL")
		void shouldHandleMultipleKeywords() {
			processor.addKeywords("https://test.com", "Java", "Python");
			String result = processor.process("<div>Learn Java and Python programming</div>", createRequestContext());

			assertThat(result)
					.isEqualToIgnoringWhitespace(
							"<div>Learn <a href=\"https://test.com\">Java</a> and <a href=\"https://test.com\">Python</a> programming</div>");
		}

		@Test
		@DisplayName("Should add custom attributes to links")
		void shouldAddCustomAttributes() {
			Map<String, String> attributes = new HashMap<>();
			attributes.put("class", "external");
			attributes.put("target", "_blank");

			processor.addKeywords("https://test.com", attributes, "Java");
			String result = processor.process("<div>Learn Java</div>", createRequestContext());

			assertThat(result)
					.contains("href=\"https://test.com\"")
					.contains("class=\"external\"")
					.contains("target=\"_blank\"");
		}
	}

	@Nested
	@DisplayName("Edge Cases and Input Validation")
	class EdgeCasesTests {

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\t", "\n"})
		@DisplayName("Should handle null and empty inputs")
		void shouldHandleNullAndEmptyInputs(String input) {
			String result = processor.process(input, createRequestContext());
			assertThat(result).isEqualTo(input);
		}

		@Test
		@DisplayName("Should not modify existing links")
		void shouldNotModifyExistingLinks() {
			processor.addKeywords("https://new.com", "Java");
			String input = "<div><a href=\"https://old.com\">Java</a> and more Java</div>";
			String result = processor.process(input, createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://old.com\">Java</a>")
					.contains("<a href=\"https://new.com\">Java</a>")
					.containsPattern("<a href=\"https://old\\.com\">Java</a>.*<a href=\"https://new\\.com\">Java</a>");
		}

		@Test
		@DisplayName("Should handle nested HTML elements")
		void shouldHandleNestedElements() {
			processor.addKeywords("https://test.com", "Java");
			String input = "<div><span>Learn Java</span> and <b>Java basics</b></div>";
			String result = processor.process(input, createRequestContext());

			assertThat(result)
					.isEqualToIgnoringWhitespace("<div><span>Learn <a href=\"https://test.com\">Java</a></span> and <b><a href=\"https://test.com\">Java</a> basics</b></div>");
		}
	}

	@Nested
	@DisplayName("Configuration Tests")
	class ConfigurationTests {

		@Test
		@DisplayName("Should respect case sensitivity setting")
		void shouldRespectCaseSensitivity() {
			ProcessingConfig caseSensitiveConfig = new ProcessingConfig.Builder()
					.setCaseSensitive(true)
					.build();

			KeywordLinkProcessor sensitiveProcessor = new KeywordLinkProcessor(caseSensitiveConfig, iCache, createModuleContext());
			sensitiveProcessor.addKeywords("https://test.com", "Java");

			String result = sensitiveProcessor.process("<div>Learn Java and java</div>", createRequestContext());

			assertThat(result)
					.isEqualToIgnoringWhitespace("<div>Learn <a href=\"https://test.com\">Java</a> and java</div>");
		}

		@Test
		@DisplayName("Should respect excluded tags")
		void shouldRespectExcludedTags() {
			processor.addKeywords("https://test.com", "Java");
			String input = "<div>Learn Java <code>Java code</code> <pre>Java example</pre></div>";
			String result = processor.process(input, createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">Java</a>")
					.contains("<code>Java code</code>")
					.contains("<pre>Java example</pre>")
					.doesNotContain("<code><a")
					.doesNotContain("<pre><a");
		}
	}

	@Nested
	@DisplayName("Performance Tests")
	class PerformanceTests {

		@Test
		@DisplayName("Should handle concurrent processing")
		void shouldHandleConcurrentProcessing() throws InterruptedException {
			int threadCount = 10;
			CountDownLatch latch = new CountDownLatch(threadCount);
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);

			processor.addKeywords("https://test.com", "Java");
			String input = "<div>Learn Java programming</div>";
			String expected = "<div>Learn <a href=\"https://test.com\">Java</a> programming</div>";

			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					try {
						String result = processor.process(input, createRequestContext());
						assertThat(result)
								.isEqualTo(expected)
								.contains("<a href=\"https://test.com\">Java</a>");
					} finally {
						latch.countDown();
					}
				});
			}

			assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
					.as("All threads should complete within timeout")
					.isTrue();

			executor.shutdown();
		}

		@Test
		@DisplayName("Should handle large documents efficiently")
		void shouldHandleLargeDocuments() {
			StringBuilder largeDoc = new StringBuilder("<div>");
			for (int i = 0; i < 1000; i++) {
				largeDoc.append("Learn Java and Python programming. ");
			}
			largeDoc.append("</div>");

			processor.addKeywords("https://python.com", "Python");
			processor.addKeywords("https://java.com", "Java");

			long startTime = System.nanoTime();
			String result = processor.process(largeDoc.toString(), createRequestContext());
			long processingTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds

			assertThat(processingTime)
					.as("Processing time should be less than 1 second")
					.isLessThan(1000);

			assertThat(result)
					.contains("<a href=\"https://java.com\">Java</a>")
					.contains("<a href=\"https://python.com\">Python</a>")
					.startsWith("<div>")
					.endsWith("</div>");
		}
	}

	@Nested
	@DisplayName("Parameterized Tests")
	class ParameterizedTests {

		@ParameterizedTest
		@MethodSource("provideHtmlTestCases")
		@DisplayName("Should handle various HTML structures")
		void shouldHandleVariousHtmlStructures(String input, String expected) {
			processor.addKeywords("https://test.com", "Java");
			String result = processor.process(input, createRequestContext());

			assertThat(result)
					.isEqualToIgnoringWhitespace(expected)
					.satisfies(processed -> {
						if (processed.contains("Java")) {
							assertThat(processed).contains("<a href=\"https://test.com\">");
						}
					});
		}

		static Stream<Arguments> provideHtmlTestCases() {
			return Stream.of(
					Arguments.of(
							"<div>Java</div>",
							"<div><a href=\"https://test.com\">Java</a></div>"
					),
					Arguments.of(
							"<div>Java<span>Java</span></div>",
							"<div><a href=\"https://test.com\">Java</a><span><a href=\"https://test.com\">Java</a></span></div>"
					)
			);
		}
	}

	@Nested
	@DisplayName("Word Boundary Tests")
	class WordBoundaryTests {

		@Test
		@DisplayName("Should not replace substrings of words when whole words only")
		void shouldNotReplaceSubstrings() {
			processor.addKeywords("https://test.com", "Java");

			assertThat(processor.process("<div>JavaScript</div>", createRequestContext()))
					.isEqualToIgnoringWhitespace("<div>JavaScript</div>")
					.doesNotContain("<a href=");
		}

		@Test
		@DisplayName("Should replace substrings when whole words only is disabled")
		void shouldReplaceSubstringsWhenConfigured() {
			ProcessingConfig config = new ProcessingConfig.Builder()
					.setWholeWordsOnly(false)
					.build();

			KeywordLinkProcessor nonWholeWordProcessor = new KeywordLinkProcessor(config, iCache, createModuleContext());
			nonWholeWordProcessor.addKeywords("https://test.com", "Java");

			assertThat(nonWholeWordProcessor.process("<div>JavaScript</div>", createRequestContext()))
					.contains("<a href=\"https://test.com\">Java</a>Script");
		}

		@Test
		@DisplayName("Should handle words with punctuation correctly")
		void shouldHandleWordPunctuation() {
			processor.addKeywords("https://test.com", "Java");

			String result = processor.process("<div>Java, Java. Java! Java? Java; Java:</div>", createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">Java</a>,")
					.contains("<a href=\"https://test.com\">Java</a>.")
					.contains("<a href=\"https://test.com\">Java</a>!")
					.contains("<a href=\"https://test.com\">Java</a>?")
					.contains("<a href=\"https://test.com\">Java</a>;")
					.contains("<a href=\"https://test.com\">Java</a>:");
		}

		@Test
		@DisplayName("Should handle special cases")
		void shouldHandleSpecialCases() {
			processor.addKeywords("https://test.com", "Java");

			assertThat(processor.process("<div>JavaBean</div>", createRequestContext()))
					.isEqualToIgnoringWhitespace("<div>JavaBean</div>");

			assertThat(processor.process("<div>jquery.Java()</div>", createRequestContext()))
					.isEqualToIgnoringWhitespace("<div>jquery.<a href=\"https://test.com\">Java</a>()</div>");
		}
	}

	@Nested
	@DisplayName("Case Sensitivity Tests")
	class CaseSensitivityTests {

		@Test
		@DisplayName("Should match case-insensitively by default")
		void shouldMatchCaseInsensitivelyByDefault() {
			processor.addKeywords("https://test.com", "Java");
			String result = processor.process("<div>java JAVA Java jAVa</div>", createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">java</a>")
					.contains("<a href=\"https://test.com\">JAVA</a>")
					.contains("<a href=\"https://test.com\">Java</a>")
					.contains("<a href=\"https://test.com\">jAVa</a>");
		}

		@Test
		@DisplayName("Should match case-sensitively when configured")
		void shouldMatchCaseSensitively() {
			ProcessingConfig caseSensitiveConfig = new ProcessingConfig.Builder()
					.setCaseSensitive(true)
					.setWholeWordsOnly(true)
					.build();

			KeywordLinkProcessor sensitiveProcessor = new KeywordLinkProcessor(caseSensitiveConfig, iCache, createModuleContext());
			sensitiveProcessor.addKeywords("https://test.com", "Java");

			String result = sensitiveProcessor.process("<div>java JAVA Java jAVa</div>", createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">Java</a>")
					.doesNotContain("<a href=\"https://test.com\">java</a>")
					.doesNotContain("<a href=\"https://test.com\">JAVA</a>")
					.doesNotContain("<a href=\"https://test.com\">jAVa</a>");
		}

		@Test
		@DisplayName("Should preserve original case when replacing")
		void shouldPreserveOriginalCase() {
			processor.addKeywords("https://test.com", "java");
			String result = processor.process("<div>JAVA Java java</div>", createRequestContext());

			assertThat(result)
					.contains("<a href=\"https://test.com\">JAVA</a>")
					.contains("<a href=\"https://test.com\">Java</a>")
					.contains("<a href=\"https://test.com\">java</a>");
		}

		@Test
		@DisplayName("Should handle mixed case keywords")
		void shouldHandleMixedCaseKeywords() {
			processor.addKeywords("https://test.com", "Java", "JAVA", "java");
			String result = processor.process("<div>Java JAVA java</div>", createRequestContext());

			// All should be matched since it's case insensitive by default
			assertThat(result)
					.contains("<a href=\"https://test.com\">Java</a>")
					.contains("<a href=\"https://test.com\">JAVA</a>")
					.contains("<a href=\"https://test.com\">java</a>");
		}
	}

	@Test
	void remove_multiple_keywords() {
		processor.addKeywords("https://java.net", "java");
		processor.addKeywords("https://condation.com", "CondationCMS");

		var result = processor.process("<div>CondationCMS is build on java!</div>", createRequestContext());
		result = result.replaceAll("\\n", "");

		assertThat(result)
				.isEqualToIgnoringWhitespace("<div><a href=\"https://condation.com\">CondationCMS</a> is build on <a href=\"https://java.net\">java</a>!</div>");
	}
	
	@Test
	void remove_combined_keywords() {
		processor.addKeywords("https://java.net", "java is create");

		var result = processor.process("<div>CondationCMS is build on java because java is create!</div>", createRequestContext());
		result = result.replaceAll("\\n", "");

		assertThat(result)
				.isEqualToIgnoringWhitespace("<div>CondationCMS is build on java because <a href=\"https://java.net\">java is create</a>!</div>");
	}
	
	@Test
	void doesnt_replace_current_node() {
		processor.addKeywords("/java", "CondationCMS");

		var result = processor.process("<div>CondationCMS is build on java because java is create!</div>", createRequestContext("java/index.md"));
		result = result.replaceAll("\\n", "");

		assertThat(result)
				.isEqualToIgnoringWhitespace("<div>CondationCMS is build on java because java is create!</div>");
	}
}
