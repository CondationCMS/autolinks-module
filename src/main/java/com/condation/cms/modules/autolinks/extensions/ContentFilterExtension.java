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

import com.condation.cms.api.extensions.HookSystemRegisterExtensionPoint;
import com.condation.cms.api.feature.features.CurrentNodeFeature;
import com.condation.cms.api.hooks.FilterContext;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.hooks.Hooks;
import com.condation.modules.api.annotation.Extension;

/**
 *
 * @author t.marx
 */
@Extension(HookSystemRegisterExtensionPoint.class)
public class ContentFilterExtension extends HookSystemRegisterExtensionPoint {

	@Override
	public void register(HookSystem hookSystem) {

		hookSystem.registerFilter(
				Hooks.CONTENT_FILTER.hook(),
				this::filterContent,
				1000
		);

	}

	private String filterContent (FilterContext<String> context) {
		if (!getRequestContext().has(CurrentNodeFeature.class)) {
			return context.value();
		}
		var currentNode = getRequestContext().get(CurrentNodeFeature.class).node();
		if (currentNode.getMetaValue("autolinks.disabled", false)) {
			return context.value();
		}
		return LifeCycleExtension.PROCESSOR.process(context.value(), getRequestContext());
	}
	
}
