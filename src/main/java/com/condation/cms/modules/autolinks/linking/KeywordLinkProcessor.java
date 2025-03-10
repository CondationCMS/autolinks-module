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

/**
 *
 * @author t.marx
 */
import com.condation.cms.api.cache.ICache;
import com.condation.cms.api.module.CMSModuleContext;
import com.condation.cms.api.module.CMSRequestContext;
import java.io.IOException;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.*;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Getter;

public class KeywordLinkProcessor implements Consumer<KeywordConfiguration.Keyword> {
	private final KeywordManager keywordManager;
    private final ProcessingConfig config;
    private static final int MINIMUM_KEYWORD_LENGTH = 2;
	
	@Getter
	private KeywordConfiguration keywordConfig;
	
	public KeywordLinkProcessor(ProcessingConfig config, ICache<String, String> cache, CMSModuleContext context) {
        this.keywordManager = new KeywordManager(config, cache, context);
        this.config = config;
    }

	public static KeywordLinkProcessor build (Path configFile, ICache<String, String> cache, CMSModuleContext moduleContext) throws IOException {
		ProcessingConfig pconfig = new ProcessingConfig.Builder().build();
		var processor = new KeywordLinkProcessor(pconfig, cache, moduleContext);
		KeywordConfiguration kconfig = new KeywordConfiguration(configFile, processor, pconfig);
		
		processor.keywordConfig = kconfig;
		
		return processor;
	}

	public KeywordManager getKeywordManager() {
		return keywordManager;
	}
	
	
	
	public void update () {
		keywordManager.prepareUpdate();
		keywordConfig.update();
		keywordManager.finishUpdate();
	}
	
    public String process(String htmlContent, CMSRequestContext requestContext) {
        if (htmlContent == null || htmlContent.length() < MINIMUM_KEYWORD_LENGTH) {
            return htmlContent;
        }

		requestContext.add(KeywordFrequenceFeature.class, new KeywordFrequenceFeature());
		requestContext.add(LinkFrequenceFeature.class, new LinkFrequenceFeature());
		
        Document doc = Jsoup.parse(htmlContent);
        processNode(doc.body(), requestContext);
        String result = doc.body().html();
        doc.html(""); // Clear for reuse
        return result;
    }

    private void processNode(Element element, CMSRequestContext requestContext) {
        if (config.isExcludedTag(element.tagName())) {
            return;
        }

        // Process all text nodes in one pass
        element.textNodes().forEach(textNode -> processTextNode(textNode, requestContext));
        
        // Process child elements
        element.children().forEach(node -> processNode(node, requestContext));
    }

    private void processTextNode(TextNode textNode, CMSRequestContext requestContext) {
        String processed = keywordManager.replaceKeywords(textNode.text(), requestContext);
        if (!textNode.text().equals(processed)) {
            textNode.after(processed);
            textNode.remove();
        }
    }

    public void addKeywords(String url, String... keywords) {
        keywordManager.addKeywords(url, keywords);
    }

    public void addKeywords(String url, Map<String, String> attributes, String... keywords) {
        keywordManager.addKeywords(url, attributes, keywords);
    }

	@Override
	public void accept(KeywordConfiguration.Keyword keyword) {
		addKeywords(keyword.url(), keyword.keywordArray());
	}
}
