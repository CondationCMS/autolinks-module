package com.condation.cms.modules.autolinks.extensions;

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
import com.condation.cms.api.cache.CacheManager;
import com.condation.cms.api.cache.ICache;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.feature.features.CacheManagerFeature;
import com.condation.cms.api.feature.features.CronJobSchedulerFeature;
import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.module.CMSModuleContext;
import com.condation.cms.api.module.CMSRequestContext;
import com.condation.cms.api.scheduler.CronJobContext;
import com.condation.cms.modules.autolinks.linking.KeywordLinkProcessor;
import com.condation.modules.api.ModuleLifeCycleExtension;
import com.condation.modules.api.annotation.Extension;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ModuleLifeCycleExtension.class)
public class LifeCycleExtension extends ModuleLifeCycleExtension<CMSModuleContext, CMSRequestContext> {

	public static KeywordLinkProcessor PROCESSOR;

	@Override
	public void init() {

	}

	@Override
	public void activate() {
		try {
			final DB db = getContext().get(DBFeature.class).db();
			Path configPath = db.getFileSystem().resolve("config/keyword_links.yaml");

			ICache<String, String> cache = getContext().get(CacheManagerFeature.class).cacheManager().get(
					"seo_keyword_links",
					new CacheManager.CacheConfig(100l, Duration.ofMinutes(1)));

			PROCESSOR = KeywordLinkProcessor.build(configPath, cache);
			PROCESSOR.getKeywordConfig().setDB(db);

			getContext().get(CronJobSchedulerFeature.class)
					.cronJobScheduler().schedule("0/15 * * * * ? *", "", (CronJobContext jobContext) -> {
						PROCESSOR.update();
					});
		} catch (IOException ex) {
			log.error("", ex);
		}
	}

}
