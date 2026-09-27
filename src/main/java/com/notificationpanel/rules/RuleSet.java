/*
 * Copyright (c) 2026, KogasaPls
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notificationpanel.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RuleSet
{
	public static final int MAX_RULES = 1000;
	private static final RuleSet EMPTY = new RuleSet(List.of());

	private final List<Compiled> compiled;
	private final boolean anyOverridesBackground;
	private final boolean anyOverridesOpacity;
	private final boolean anyOverridesVisibility;

	private RuleSet(List<NotificationRule> rules)
	{
		List<Compiled> entries = new ArrayList<>(rules.size());
		boolean background = false;
		boolean opacity = false;
		boolean visibility = false;
		for (NotificationRule rule : rules)
		{
			background |= rule.getBackgroundRgb() != null;
			opacity |= rule.getOpacityPercent() != null;
			visibility |= rule.getVisibility() != null;
			entries.add(new Compiled(rule, Wildcards.fold(rule.getPattern())));
		}
		this.compiled = List.copyOf(entries);
		this.anyOverridesBackground = background;
		this.anyOverridesOpacity = opacity;
		this.anyOverridesVisibility = visibility;
	}

	public static RuleSet empty()
	{
		return EMPTY;
	}

	public static CompileResult compile(List<NotificationRule> rules)
	{
		if (rules == null)
		{
			throw new IllegalArgumentException("Rules must not be null.");
		}
		if (rules.size() > MAX_RULES)
		{
			throw new IllegalArgumentException(
				"A rule set may contain at most " + MAX_RULES + " rules.");
		}

		Set<UUID> ids = new HashSet<>();
		for (NotificationRule rule : rules)
		{
			if (rule == null)
			{
				throw new IllegalArgumentException("Rules must not contain null entries.");
			}
			if (!ids.add(rule.getId()))
			{
				throw new IllegalArgumentException("Rule UUIDs must be unique.");
			}
		}

		List<NotificationRule> enabled = new ArrayList<>();
		Map<UUID, String> errors = new LinkedHashMap<>();
		for (NotificationRule rule : rules)
		{
			if (!rule.isEnabled())
			{
				continue;
			}

			List<String> validationErrors = rule.validationErrors();
			if (!validationErrors.isEmpty())
			{
				errors.put(rule.getId(), String.join(" ", validationErrors));
				continue;
			}

			enabled.add(rule);
		}
		return new CompileResult(new RuleSet(enabled), errors);
	}

	/**
	 * Finds all enabled rules whose patterns match the message, in evaluation order.
	 */
	public List<NotificationRule> matching(String message)
	{
		char[] text = Wildcards.fold(message);
		List<NotificationRule> matches = new ArrayList<>();
		for (Compiled entry : compiled)
		{
			if (Wildcards.matches(entry.pattern, text))
			{
				matches.add(entry.rule);
			}
		}
		return List.copyOf(matches);
	}

	public Resolution resolve(String message)
	{
		Integer rgb = null;
		Integer opacity = null;
		Visibility visibility = null;
		boolean matched = false;
		char[] text = Wildcards.fold(message);
		for (Compiled entry : compiled)
		{
			if (!Wildcards.matches(entry.pattern, text))
			{
				continue;
			}

			NotificationRule rule = entry.rule;
			matched = true;
			if (rgb == null && rule.getBackgroundRgb() != null)
			{
				rgb = rule.getBackgroundRgb();
			}
			if (opacity == null && rule.getOpacityPercent() != null)
			{
				opacity = rule.getOpacityPercent();
			}
			if (visibility == null && rule.getVisibility() != null)
			{
				visibility = rule.getVisibility();
			}
			if ((rgb != null || !anyOverridesBackground)
				&& (opacity != null || !anyOverridesOpacity)
				&& (visibility != null || !anyOverridesVisibility))
			{
				break;
			}
		}
		return new Resolution(rgb, opacity, visibility, matched);
	}

	private static final class Compiled
	{
		private final NotificationRule rule;
		private final char[] pattern;

		private Compiled(NotificationRule rule, char[] pattern)
		{
			this.rule = rule;
			this.pattern = pattern;
		}
	}

	public static final class CompileResult
	{
		private final RuleSet ruleSet;
		private final Map<UUID, String> errors;

		private CompileResult(RuleSet ruleSet, Map<UUID, String> errors)
		{
			this.ruleSet = ruleSet;
			this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
		}

		public RuleSet getRuleSet()
		{
			return ruleSet;
		}

		public Map<UUID, String> getErrors()
		{
			return errors;
		}
	}

	/**
	 * The outcome of resolving a message against the rule set: the effective formatting overrides
	 * and whether any enabled rule matched.
	 */
	public static final class Resolution
	{
		private final Integer backgroundRgb;
		private final Integer opacityPercent;
		private final Visibility visibility;
		private final boolean matched;

		private Resolution(Integer backgroundRgb, Integer opacityPercent, Visibility visibility,
			boolean matched)
		{
			this.backgroundRgb = backgroundRgb;
			this.opacityPercent = opacityPercent;
			this.visibility = visibility;
			this.matched = matched;
		}

		public Integer getBackgroundRgb()
		{
			return backgroundRgb;
		}

		public Integer getOpacityPercent()
		{
			return opacityPercent;
		}

		/**
		 * Visibility override, or null if unspecified.
		 */
		public Visibility getVisibility()
		{
			return visibility;
		}

		public boolean isMatched()
		{
			return matched;
		}
	}
}
