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
	private static final int MAX_RULES = 100;
	private static final RuleSet EMPTY = new RuleSet(List.of());

	private final List<CompiledRule> compiledRules;

	private RuleSet(List<CompiledRule> compiledRules)
	{
		this.compiledRules = List.copyOf(compiledRules);
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
			throw new IllegalArgumentException("A rule set may contain at most 100 rules.");
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

		List<CompiledRule> compiled = new ArrayList<>();
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

			compiled.add(new CompiledRule(rule));
		}
		return new CompileResult(new RuleSet(compiled), errors);
	}

	public Resolution resolve(String message)
	{
		String sourceMessage = message == null ? "" : message;
		Integer rgb = null;
		Integer opacity = null;
		boolean matched = false;
		for (CompiledRule compiledRule : compiledRules)
		{
			if (!Wildcards.matches(compiledRule.source.getPattern(), sourceMessage))
			{
				continue;
			}

			matched = true;
			NotificationRule source = compiledRule.source;
			if (rgb == null && source.getBackgroundRgb() != null)
			{
				rgb = source.getBackgroundRgb();
			}
			if (opacity == null && source.getOpacityPercent() != null)
			{
				opacity = source.getOpacityPercent();
			}
			if (rgb != null && opacity != null)
			{
				break;
			}
		}
		return new Resolution(rgb, opacity, matched);
	}

	private static final class CompiledRule
	{
		private final NotificationRule source;

		private CompiledRule(NotificationRule source)
		{
			this.source = source;
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
