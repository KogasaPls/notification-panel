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
	/**
	 * How many rules one configuration may hold.
	 *
	 * <p>Declared here because this is where exceeding it is refused. Every other place that caps,
	 * validates or names the limit reads it from here: writing more rules than reading will accept
	 * would store a document that loads as corrupt from then on.</p>
	 */
	public static final int MAX_RULES = 100;
	private static final RuleSet EMPTY = new RuleSet(List.of());

	private final List<NotificationRule> rules;
	// Whether any rule in this set overrides each attribute. Resolution stops once every attribute
	// is either resolved or unobtainable, and without these it could only stop on the former --
	// so a set whose rules all override colour alone would scan every rule on every notification,
	// waiting for an opacity nothing in it can supply.
	private final boolean anyOverridesBackground;
	private final boolean anyOverridesOpacity;

	private RuleSet(List<NotificationRule> rules)
	{
		this.rules = List.copyOf(rules);
		boolean background = false;
		boolean opacity = false;
		for (NotificationRule rule : this.rules)
		{
			background |= rule.getBackgroundRgb() != null;
			opacity |= rule.getOpacityPercent() != null;
		}
		this.anyOverridesBackground = background;
		this.anyOverridesOpacity = opacity;
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

	public Resolution resolve(String message)
	{
		String sourceMessage = message == null ? "" : message;
		Integer rgb = null;
		Integer opacity = null;
		boolean matched = false;
		for (NotificationRule rule : rules)
		{
			if (!Wildcards.matches(rule.getPattern(), sourceMessage))
			{
				continue;
			}

			matched = true;
			if (rgb == null && rule.getBackgroundRgb() != null)
			{
				rgb = rule.getBackgroundRgb();
			}
			if (opacity == null && rule.getOpacityPercent() != null)
			{
				opacity = rule.getOpacityPercent();
			}
			// Stop once nothing later can change the answer. An attribute is finished when it has
			// been taken from a rule or when no rule in the set overrides it at all; waiting only
			// for the former meant the common set -- every rule overriding colour and nothing
			// overriding opacity -- ran every rule on every notification even after matching the
			// first. The check sits after `matched` is set, so stopping cannot hide a match from
			// the allowlist.
			if ((rgb != null || !anyOverridesBackground)
				&& (opacity != null || !anyOverridesOpacity))
			{
				break;
			}
		}
		return new Resolution(rgb, opacity, matched);
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
		private final boolean matched;

		private Resolution(Integer backgroundRgb, Integer opacityPercent, boolean matched)
		{
			this.backgroundRgb = backgroundRgb;
			this.opacityPercent = opacityPercent;
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

		public boolean isMatched()
		{
			return matched;
		}
	}
}
