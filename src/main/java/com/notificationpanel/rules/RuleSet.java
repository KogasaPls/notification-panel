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
	 *
	 * <p>This is a sanity bound rather than a performance one. It used to be a hundred, when
	 * matching cost rules x pattern x text and the count was the only thing keeping a notification
	 * off the frame budget; matching is now linear in pattern plus text, so a thousand rules
	 * resolve in well under a millisecond and the number is here only to stop a configuration file
	 * that has had a novel pasted into it from being loaded as rules. The real ceiling is the
	 * length of a stored value, which holds roughly 1500 short rules and 380 with the longest
	 * patterns this allows.</p>
	 */
	public static final int MAX_RULES = 1000;
	private static final RuleSet EMPTY = new RuleSet(List.of());

	private final List<Compiled> compiled;
	// Whether any rule in this set overrides each attribute. Resolution stops once every attribute
	// is either resolved or unobtainable, and without these it could only stop on the former --
	// so a set whose rules all override colour alone would scan every rule on every notification,
	// waiting for an opacity nothing in it can supply.
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
	 * Every rule in this set whose pattern matches, in the order the resolver walks them.
	 *
	 * <p>Separate from {@link #resolve} because that stops as soon as no later rule can change the
	 * answer, which is the right thing when producing a style and the wrong thing when the question
	 * is "what else already matches this?". The set holds only enabled, valid rules, so what comes
	 * back is exactly what stands between a newly added rule and the notification.</p>
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
		// Folded once for the whole set: every rule would otherwise fold the same message again,
		// and folding is the per-character cost of matching.
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
			// Stop once nothing later can change the answer. An attribute is finished when it has
			// been taken from a rule or when no rule in the set overrides it at all; waiting only
			// for the former meant the common set -- every rule overriding colour and nothing
			// overriding opacity -- ran every rule on every notification even after matching the
			// first. The check sits after `matched` is set, so stopping cannot hide a match from
			// the allowlist. Every attribute needs its own clause: omitting one stops the scan too
			// early and quietly returns the wrong answer, and only in the orderings where the rule
			// that would have supplied it sits below one that settles everything else.
			if ((rgb != null || !anyOverridesBackground)
				&& (opacity != null || !anyOverridesOpacity)
				&& (visibility != null || !anyOverridesVisibility))
			{
				break;
			}
		}
		return new Resolution(rgb, opacity, visibility, matched);
	}

	/**
	 * An enabled rule together with its pattern folded to canonical case.
	 *
	 * <p>Folding happens here, once, rather than on every notification: rules change when
	 * configuration does, and messages arrive far more often than that. The pair is one object
	 * because the two are only ever read together, at the same index -- as parallel structures
	 * they could drift out of step, and nothing but the loop bound would have said so.</p>
	 */
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
		 * What the rules decided about visibility, or null if none of them decided.
		 *
		 * <p>Null is not "show": it means the caller falls back to whether anything matched and to
		 * the global default, which is the only place that distinction can be made.</p>
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
