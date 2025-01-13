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
public class LinkFrequenceFeature implements Feature {
	
	private final ConcurrentMap<String, AtomicInteger> links = new ConcurrentHashMap<>();
	
	public void increment (String link) {
		links.computeIfAbsent(link, k -> new AtomicInteger(0)).incrementAndGet();
	}
	
	public int get (String link) {
		if (links.containsKey(link)) {
			return links.get(link).get();
		}
			
		return 0;
	}
	
	public int total () {
		return links.values().stream().mapToInt(AtomicInteger::get).sum();
	}
}
