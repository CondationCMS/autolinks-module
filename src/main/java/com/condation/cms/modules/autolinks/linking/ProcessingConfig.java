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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class ProcessingConfig {
    private final Set<String> excludedTags;
    private boolean caseSensitive;
    private boolean wholeWordsOnly;
	private int linkFrequency = 2;
	private int totalLinkCount = 10;
	private boolean useTaxonomies;
	@Getter
	@Setter
	private boolean useTaxonomyValues;

    private ProcessingConfig(Builder builder) {
        this.excludedTags = new HashSet<>(builder.excludedTags);
        this.caseSensitive = builder.caseSensitive;
        this.wholeWordsOnly = builder.wholeWordsOnly;
		this.linkFrequency = builder.linkFrequency;
		this.totalLinkCount = builder.totalLinkCount;
		this.useTaxonomies = builder.useTaxonomies;
		this.useTaxonomyValues = builder.useTaxonomyValues;
    }

	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	public void setWholeWordsOnly(boolean wholeWordsOnly) {
		this.wholeWordsOnly = wholeWordsOnly;
	}

	public void setExcludeTags (Collection<String> tags) {
		excludedTags.clear();
		excludedTags.addAll(tags);
	}
	
	public void setUseTaxonomies (boolean useTaxonomies) {
		this.useTaxonomies = useTaxonomies;
	}
	
    public boolean isUseTaxonomies() {
        return useTaxonomies;
    }
	
	
	
    public boolean isExcludedTag(String tag) {
        return excludedTags.contains(tag.toLowerCase());
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isWholeWordsOnly() {
        return wholeWordsOnly;
    }

	public int getLinkFrequency() {
		return linkFrequency;
	}

	public void setLinkFrequency(int linkFrequency) {
		this.linkFrequency = linkFrequency;
	}

	public int getTotalLinkCount() {
		return totalLinkCount;
	}

	public void setTotalLinkCount(int totalLinkCount) {
		this.totalLinkCount = totalLinkCount;
	}
	
	

    public static class Builder {
        private Set<String> excludedTags;
        private boolean caseSensitive;
        private boolean wholeWordsOnly;
		private int linkFrequency = 2;
		private int totalLinkCount = 10;
		private boolean useTaxonomies;
		private boolean useTaxonomyValues;

        public Builder() {
            this.excludedTags = new HashSet<>(Arrays.asList("a", "script", "style", "code", "pre"));
            this.caseSensitive = false;
            this.wholeWordsOnly = true;
			this.useTaxonomies = true;
			this.useTaxonomyValues = false;
        }

		public Builder setLinkFrequency(int lingFreq) {
            this.linkFrequency = lingFreq;
            return this;
        }
		
		public Builder setTotalLinkCount(int lingTotal) {
            this.totalLinkCount = lingTotal;
            return this;
        }
		
        public Builder setExcludedTags(Set<String> excludedTags) {
            this.excludedTags = excludedTags;
            return this;
        }

        public Builder setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }
        public Builder setUseTaxonomies(boolean useTaxonomies) {
            this.useTaxonomies = useTaxonomies;
            return this;
        }
		public Builder setUseTaxonomyValues(boolean useTaxonomyValues) {
            this.useTaxonomyValues = useTaxonomyValues;
            return this;
        }
		

        public Builder setWholeWordsOnly(boolean wholeWordsOnly) {
            this.wholeWordsOnly = wholeWordsOnly;
            return this;
        }

        public ProcessingConfig build() {
            return new ProcessingConfig(this);
        }
    }
}
