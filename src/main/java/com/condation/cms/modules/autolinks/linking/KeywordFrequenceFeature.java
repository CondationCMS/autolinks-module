package com.condation.cms.modules.autolinks.linking;

/*-
 * #%L
 * autolinks-module
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

import com.condation.cms.api.feature.Feature;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author t.marx
 */
public class KeywordFrequenceFeature implements Feature {
	
	private final ConcurrentMap<String, AtomicInteger> keywords = new ConcurrentHashMap<>();
	
	public void increment (String keyword) {
		keywords.computeIfAbsent(keyword, k -> new AtomicInteger(0)).incrementAndGet();
	}
	
	public int get (String keyword) {
		if (keywords.containsKey(keyword)) {
			return keywords.get(keyword).get();
		}
			
		return 0;
	}
	
	public int total () {
		return keywords.values().stream().mapToInt(AtomicInteger::get).sum();
	}
}
