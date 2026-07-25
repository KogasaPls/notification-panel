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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class NotificationRule
{
	/**
	 * The caps this class rejects a rule for exceeding.
	 *
	 * <p>Public because two callers have to stay under them rather than discover them: the migrator
	 * disables a converted rule whose pattern is too long, and the editor truncates a message it
	 * prefills a draft from. Both held their own copy of the number, which is a limit changed in one
	 * place and enforced from another.</p>
	 */
	public static final int MAX_NAME_CODE_POINTS = 64;
	public static final int MAX_PATTERN_CODE_POINTS = 512;
	private static final int MAX_RGB = 0xFFFFFF;
	private static final int MIN_OPACITY = 0;
	private static final int MAX_OPACITY = 100;

	private final UUID id;
	private final String name;
	private final boolean enabled;
	private final String pattern;
	private final Integer backgroundRgb;
	private final Integer opacityPercent;
	private final Visibility visibility;
	private final String migrationNote;

	public NotificationRule(UUID id, String name, boolean enabled, String pattern,
		Integer backgroundRgb, Integer opacityPercent, Visibility visibility, String migrationNote)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.name = name;
		this.enabled = enabled;
		this.pattern = pattern;
		this.backgroundRgb = backgroundRgb;
		this.opacityPercent = opacityPercent;
		this.visibility = visibility;
		this.migrationNote = migrationNote;
	}

	public UUID getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public String getPattern()
	{
		return pattern;
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
	 * Whether this rule decides visibility, and which way.
	 *
	 * <p>Null means the rule does not decide, exactly as a null background or opacity means it does
	 * not override those -- so visibility resolves through the same "first enabled matching rule
	 * that sets the attribute wins" pass rather than as a special case. All values are legal; there
	 * is nothing here to validate.</p>
	 */
	public Visibility getVisibility()
	{
		return visibility;
	}

	public String getMigrationNote()
	{
		return migrationNote;
	}

	public NotificationRule withEnabled(boolean enabled)
	{
		if (this.enabled == enabled)
		{
			return this;
		}
		return new NotificationRule(id, name, enabled, pattern, backgroundRgb, opacityPercent,
			visibility, migrationNote);
	}

	public NotificationRule withMigrationNote(String migrationNote)
	{
		if (Objects.equals(this.migrationNote, migrationNote))
		{
			return this;
		}
		return new NotificationRule(id, name, enabled, pattern, backgroundRgb, opacityPercent,
			visibility, migrationNote);
	}

	public List<String> validationErrors()
	{
		List<String> errors = new ArrayList<>();
		if (!hasCodePointCountBetween(name, 1, MAX_NAME_CODE_POINTS))
		{
			errors.add("Name must contain 1 to " + MAX_NAME_CODE_POINTS
				+ " Unicode code points.");
		}
		if (!hasCodePointCountBetween(pattern, 1, MAX_PATTERN_CODE_POINTS))
		{
			errors.add("Pattern must contain 1 to " + MAX_PATTERN_CODE_POINTS
				+ " Unicode code points.");
		}
		if (backgroundRgb != null && (backgroundRgb < 0 || backgroundRgb > MAX_RGB))
		{
			errors.add("Background color must be a 24-bit RGB value.");
		}
		if (opacityPercent != null && (opacityPercent < MIN_OPACITY || opacityPercent > MAX_OPACITY))
		{
			errors.add("Opacity must be between 0 and 100.");
		}
		return List.copyOf(errors);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof NotificationRule))
		{
			return false;
		}
		NotificationRule rule = (NotificationRule) other;
		return enabled == rule.enabled
			&& id.equals(rule.id)
			&& Objects.equals(name, rule.name)
			&& Objects.equals(pattern, rule.pattern)
			&& Objects.equals(backgroundRgb, rule.backgroundRgb)
			&& Objects.equals(opacityPercent, rule.opacityPercent)
			&& Objects.equals(visibility, rule.visibility)
			&& Objects.equals(migrationNote, rule.migrationNote);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, enabled, pattern, backgroundRgb, opacityPercent, visibility,
			migrationNote);
	}

	private static boolean hasCodePointCountBetween(String value, int minimum, int maximum)
	{
		if (value == null)
		{
			return false;
		}
		int count = value.codePointCount(0, value.length());
		return count >= minimum && count <= maximum;
	}
}
