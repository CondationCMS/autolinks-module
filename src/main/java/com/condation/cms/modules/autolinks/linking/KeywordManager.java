package com.condation.cms.modules.autolinks.linking;

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
import com.condation.cms.api.cache.ICache;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author t.marx
 */
class KeywordManager {

	private final Map<String, KeywordMapping> keywordMappings;
	private final List<KeywordPattern> compiledPatterns;
	private final ICache<String, String> replacementCache;

	private final ProcessingConfig config;

	public KeywordManager(ProcessingConfig config, ICache<String, String> cache) {
		this.keywordMappings = new HashMap<>();
		this.compiledPatterns = new ArrayList<>();
		this.config = config;
		this.replacementCache = cache;
	}

	public void clear() {
		keywordMappings.clear();
		compiledPatterns.clear();
		replacementCache.invalidate();
	}

	public void addKeywords(String url, String... keywords) {
		addKeywords(url, new HashMap<>(), keywords);
	}

	public void addKeywords(String url, Map<String, String> attributes, String... keywords) {
		KeywordMapping mapping = new KeywordMapping(url, attributes);

		for (String keyword : keywords) {
			if (keyword != null && keyword.length() >= 2) {
				// Store keyword in original case
				String keywordKey = config.isCaseSensitive() ? keyword : keyword.toLowerCase();
				keywordMappings.put(keywordKey, mapping);
			}
		}
		updateCompiledPatterns();
	}

	private void updateCompiledPatterns() {
		compiledPatterns.clear();

		// Group keywords by length for optimal matching
		Map<Integer, List<String>> keywordsByLength = keywordMappings.keySet().stream()
				.collect(Collectors.groupingBy(String::length));

		// Compile patterns for each length group
		keywordsByLength.entrySet().stream()
				.sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey()))
				.forEach(entry -> {
					String patternStr = entry.getValue().stream()
							.map(Pattern::quote)
							.collect(Collectors.joining("|"));

					// Add word boundaries if wholeWordsOnly is true
					int flags = config.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
					Pattern pattern = Pattern.compile(
							config.isWholeWordsOnly()
							? "\\b(" + patternStr + ")\\b"
							: "(" + patternStr + ")",
							flags
					);

					compiledPatterns.add(new KeywordPattern(pattern, entry.getKey()));
				});
	}

	public String replaceKeywords(String text) {
		// Check cache first
		String cacheKey = text + config.hashCode();
		String cachedResult = replacementCache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}

		// Find all matches first and store them
		List<Match> matches = new ArrayList<>();
		for (KeywordPattern pattern : compiledPatterns) {
			pattern.getPattern().matcher(text)
					.results()
					.forEach(matchResult -> {
						String matchedText = matchResult.group(1);
						String lookupKey = config.isCaseSensitive() ? matchedText : matchedText.toLowerCase();
						KeywordMapping mapping = keywordMappings.get(lookupKey);
						if (mapping != null) {
							matches.add(new Match(
									matchResult.start(),
									matchResult.end(),
									matchedText, // Use original matched text to preserve case
									mapping
							));
						}
					});
		}

		// Sort matches by position (descending)
		matches.sort((m1, m2) -> Integer.compare(m2.start, m1.start));

		// Apply replacements from end to start
		StringBuilder result = new StringBuilder(text);
		for (Match match : matches) {
			String replacement = buildLink(match.keyword, match.mapping);
			result.replace(match.start, match.end, replacement);
		}

		String finalResult = result.toString();
		replacementCache.put(cacheKey, finalResult);
		return finalResult;
	}

	private String buildLink(String matchedText, KeywordMapping mapping) {
		String cacheKey = matchedText + mapping.hashCode();

		if (replacementCache.contains(cacheKey)) {
			return replacementCache.get(cacheKey);
		}

		StringBuilder link = new StringBuilder(matchedText.length() * 2)
				.append("<a href=\"")
				.append(mapping.getUrl())
				.append("\"");

		mapping.getAttributes().forEach((key, value)
				-> link.append(" ").append(key).append("=\"").append(value).append("\""));

		String theLink = link.append(">")
				.append(matchedText) // Use original matched text to preserve case
				.append("</a>")
				.toString();

		replacementCache.put(cacheKey, theLink);

		return theLink;
	}

	private static class Match {

		final int start;
		final int end;
		final String keyword;
		final KeywordMapping mapping;

		Match(int start, int end, String keyword, KeywordMapping mapping) {
			this.start = start;
			this.end = end;
			this.keyword = keyword;
			this.mapping = mapping;
		}
	}

	private static class KeywordPattern {

		private final Pattern pattern;
		private final int length;

		public KeywordPattern(Pattern pattern, int length) {
			this.pattern = pattern;
			this.length = length;
		}

		public Pattern getPattern() {
			return pattern;
		}
	}
}
