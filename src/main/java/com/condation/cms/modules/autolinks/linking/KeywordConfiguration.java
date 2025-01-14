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
import com.condation.cms.api.db.ContentNode;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.utils.MapUtil;
import com.condation.cms.api.utils.PathUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author t.marx
 */
public class KeywordConfiguration {

	public final Path configFile;
	private FileTime lastModifiedTime;
	private final Consumer<Keyword> updateConsumer;
	private final ProcessingConfig config;

	private DB db;

	public KeywordConfiguration(Path configFile, Consumer<Keyword> updateConsumer, ProcessingConfig config) throws IOException {
		this.configFile = configFile;
		this.updateConsumer = updateConsumer;
		this.config = config;
	}
	
	public void setDB (DB db) {
		this.db = db;
	}

	public boolean isUpdateNecassary() {
		try {
			return isModified();
		} catch (IOException ex) {
			return false;
		}
	}

	private boolean isModified() throws IOException {
		if (Files.exists(configFile) && lastModifiedTime == null) {
			return true;
		} else if (!Files.exists(configFile)) {
			return false;
		} else if (Files.exists(configFile)
				&& Files.getLastModifiedTime(configFile).compareTo(lastModifiedTime) > 0) {
			return true;
		}

		return false;
	}

	private void updateLastModifiedTime() throws IOException {
		if (Files.exists(configFile)) {
			this.lastModifiedTime = Files.getLastModifiedTime(configFile);
		}
	}

	public void update() {

		loadFromDB();
		
		loadFromTaxonomies();
		
		if (!Files.exists(configFile)) {
			return;
		}

		try (FileInputStream fis = new FileInputStream(this.configFile.toFile())) {
			Yaml yaml = new Yaml();
			Map<String, Object> yamlData = yaml.load(fis);

			config.setCaseSensitive((boolean) yamlData.getOrDefault("caseSensitive", true));
			config.setWholeWordsOnly((boolean) yamlData.getOrDefault("wholeWordsOnly", true));
			config.setUseTaxonomies((boolean) yamlData.getOrDefault("useTaxonomies", true));

			config.setLinkFrequency((int) yamlData.getOrDefault("linkFrequency", 2));
			config.setTotalLinkCount((int) yamlData.getOrDefault("totalLinkCount", 10));
			
			config.setExcludeTags((Collection<String>) yamlData.getOrDefault("excludeTags", Collections.emptyList()));

			List<Map<String, Object>> replacements = (List<Map<String, Object>>) yamlData.getOrDefault("replacements", Collections.emptyList());

			if (replacements != null) {
				replacements.stream()
						.map(map -> new Keyword((String) map.get("link"), (List<String>) map.get("keywords")))
						.forEach(updateConsumer);
			}

			updateLastModifiedTime();

		} catch (IOException e) {
			throw new RuntimeException("Failed to load configuration from " + configFile.toString(), e);
		}
	}

	private void loadFromTaxonomies () {
		if (db == null) {
			return;
		}
		
		if (!config.isUseTaxonomies()) {
			
		}
		
		db.getTaxonomies().all().forEach(taxo -> {
			String url = "/%s".formatted(taxo.slug);
			
			updateConsumer.accept(new Keyword(url, List.of(taxo.title)));
		});
	}
	
	private void loadFromDB() {
		if (db == null) {
			return;
		}
		var query = db.getContent().query((ContentNode t, Integer u) -> t);
	
		var nodes = query.whereExists("autolinks.keywords").get();
		
		nodes.forEach(node -> {
			final Path contentBase = db.getFileSystem().resolve(Constants.Folders.CONTENT);
			var nodePath = contentBase.resolve(node.uri());
			
			String url = PathUtil.toURI(nodePath, contentBase);
			List<String> keywords = (List<String>) MapUtil.getValue(node.data(), "autolinks.keywords");
			
			updateConsumer.accept(new Keyword(url, keywords));
		});
	}

	public static record Keyword(String url, List<String> keywords) {

		public String[] keywordArray() {
			return keywords.toArray(String[]::new);
		}
	}

}
