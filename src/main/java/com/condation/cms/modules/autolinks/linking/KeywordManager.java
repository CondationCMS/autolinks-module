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
import com.condation.cms.api.Constants;
import com.condation.cms.api.cache.ICache;
import com.condation.cms.api.db.ContentNode;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.feature.features.CurrentNodeFeature;
import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.cms.api.module.SiteRequestContext;
import com.condation.cms.api.utils.HTTPUtil;
import com.condation.cms.api.utils.PathUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author t.marx
 */
public class KeywordManager {

	private final ConcurrentMap<String, KeywordMapping> keywordMappings;
	private final List<KeywordPattern> compiledPatterns;
	private final ICache<String, String> replacementCache;

	private final ConcurrentMap<String, KeywordMapping> update_keywordMappings;
	private final List<KeywordPattern> update_compiledPatterns;
	
	private final SiteModuleContext moduleContext;
	
	private final ProcessingConfig config;

	public KeywordManager(ProcessingConfig config, ICache<String, String> cache, SiteModuleContext moduleContext) {
		this.keywordMappings = new ConcurrentHashMap<>();
		this.compiledPatterns = new CopyOnWriteArrayList<>();
		this.update_keywordMappings = new ConcurrentHashMap<>();
		this.update_compiledPatterns = new CopyOnWriteArrayList<>();
		this.config = config;
		this.replacementCache = cache;
		this.moduleContext = moduleContext;
	}

	public void prepareUpdate () {
		this.update_compiledPatterns.clear();
		this.update_keywordMappings.clear();
	}
	
	public void finishUpdate () {
		keywordMappings.clear();
		compiledPatterns.clear();
		replacementCache.invalidate();
		
		keywordMappings.putAll(update_keywordMappings);
		compiledPatterns.addAll(update_compiledPatterns);
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
				update_keywordMappings.put(keywordKey, mapping);
			}
		}
		updateCompiledPatterns();
	}

	private void updateCompiledPatterns() {
		update_compiledPatterns.clear();

		// Group keywords by length for optimal matching
		Map<Integer, List<String>> keywordsByLength = update_keywordMappings.keySet().stream()
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

					update_compiledPatterns.add(new KeywordPattern(pattern, entry.getKey()));
				});
	}

	public String replaceKeywords(String text, SiteRequestContext requestContext) {
		// Check cache first
		String cacheKey = Integer.toHexString((text + config.hashCode()).hashCode());
		String cachedResult = replacementCache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}

		final DB db = moduleContext.get(DBFeature.class).db();

		ContentNode currentNode = requestContext.get(CurrentNodeFeature.class).node();
		final Path contentBase = db.getFileSystem().resolve(Constants.Folders.CONTENT);
		var nodePath = contentBase.resolve(currentNode.uri());
		String currentNodeUrl = PathUtil.toURL(nodePath, contentBase);
		
		var kwFrequency = requestContext.get(KeywordFrequenceFeature.class);
		var linkFrequency = requestContext.get(LinkFrequenceFeature.class);
		
		// Find all matches first and store them
		List<Match> matches = new ArrayList<>();
		for (KeywordPattern pattern : compiledPatterns) {
			pattern.getPattern().matcher(text)
					.results()
					.forEach(matchResult -> {
						String matchedText = matchResult.group(1);
						String lookupKey = config.isCaseSensitive() ? matchedText : matchedText.toLowerCase();
						KeywordMapping mapping = keywordMappings.get(lookupKey);
						
						if (mapping != null && !mapping.getUrl().equals(currentNodeUrl)) {
							
							if (linkFrequency.get(mapping.getUrl()) == config.getLinkFrequency()) {
								return;
							} else if (linkFrequency.total()>= config.getTotalLinkCount()) {
								return;
							}
							
							linkFrequency.increment(mapping.getUrl());
							
							matches.add(new Match(
									matchResult.start(),
									matchResult.end(),
									matchedText, // Use original matched text to preserve case
									mapping
							));
						}
					});
		}


		// Sort matches by start position (descending), and by keyword length (descending) for overlaps
		matches.sort((m1, m2) -> {
			int positionComparison = Integer.compare(m2.start, m1.start);
			if (positionComparison == 0) {
				return Integer.compare(m2.keyword.length(), m1.keyword.length());
			}
			return positionComparison;
		});
	
		// Remove overlapping matches
		List<Match> filteredMatches = new ArrayList<>();
		int lastEnd = Integer.MAX_VALUE;
		for (Match match : matches) {
			if (match.end <= lastEnd) {
				filteredMatches.add(match);
				lastEnd = match.start; // Update the last end position
			}
		}
	
		// Replace keywords in the text using placeholders
		StringBuilder result = new StringBuilder(text);
		Map<String, String> placeholderToReplacement = new HashMap<>();
		for (Match match : filteredMatches) {
			String placeholder = "##PLACEHOLDER_" + match.start + "_" + match.end + "##";
			placeholderToReplacement.put(placeholder, buildLink(match.keyword, match.mapping, requestContext));
			result.replace(match.start, match.end, placeholder);
		}
	
		// Replace placeholders with final values
		String finalResult = result.toString();
		for (Map.Entry<String, String> entry : placeholderToReplacement.entrySet()) {
			finalResult = finalResult.replace(entry.getKey(), entry.getValue());
		}
	
		// Cache the result
		replacementCache.put(cacheKey, finalResult);
		return finalResult;

	}

	private String buildLink(String matchedText, KeywordMapping mapping, SiteRequestContext requestContext) {
		String cacheKey = matchedText + mapping.hashCode();

		if (replacementCache.contains(cacheKey)) {
			return replacementCache.get(cacheKey);
		}

		StringBuilder link = new StringBuilder(matchedText.length() * 2)
				.append("<a href=\"")
				.append(HTTPUtil.modifyUrl(mapping.getUrl(), requestContext))
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
