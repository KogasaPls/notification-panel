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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NotificationRuleTest
{
	@Test
	public void validatesUnicodeLengthsOpacityAndRequiredOverride()
	{
		NotificationRule valid = rule("Drops", "dragon", 0xBF616A, 90);
		assertTrue(valid.validationErrors().isEmpty());

		NotificationRule noOverride = rule("Drops", "dragon", null, null);
		assertTrue(noOverride.validationErrors().isEmpty());

		NotificationRule longName = rule("😀".repeat(65), "dragon", 0xBF616A, 90);
		assertFalse(longName.validationErrors().isEmpty());
	}

	@Test
	public void validatesNameAndPatternUnicodeCodePointBounds()
	{
		assertEquals(Arrays.asList("Name must contain 1 to 64 Unicode code points."),
			rule(null, "pattern", 0, null)
				.validationErrors());
		assertEquals(Arrays.asList("Name must contain 1 to 64 Unicode code points."),
			rule("", "pattern", 0, null)
				.validationErrors());
		assertTrue(rule("😀".repeat(64), "pattern", 0, null).validationErrors().isEmpty());
		assertEquals(Arrays.asList("Name must contain 1 to 64 Unicode code points."),
			rule("😀".repeat(65), "pattern", 0, null).validationErrors());

		assertEquals(Arrays.asList("Pattern must contain 1 to 512 Unicode code points."),
			rule("rule", null, 0, null)
				.validationErrors());
		assertEquals(Arrays.asList("Pattern must contain 1 to 512 Unicode code points."),
			rule("rule", "", 0, null)
				.validationErrors());
		assertTrue(rule("rule", "😀".repeat(512), 0, null).validationErrors().isEmpty());
		assertEquals(Arrays.asList("Pattern must contain 1 to 512 Unicode code points."),
			rule("rule", "😀".repeat(513), 0, null).validationErrors());
	}

	@Test
	public void validatesRgbOpacityAndMessagesInStableOrder()
	{
		assertEquals(Arrays.asList("Background color must be a 24-bit RGB value."),
			rule("rule", "pattern", -1, null)
				.validationErrors());
		assertTrue(rule("rule", "pattern", 0x000000, null).validationErrors().isEmpty());
		assertTrue(rule("rule", "pattern", 0xFFFFFF, null).validationErrors().isEmpty());
		assertEquals(Arrays.asList("Background color must be a 24-bit RGB value."),
			rule("rule", "pattern", 0x1000000, null).validationErrors());

		assertEquals(Arrays.asList("Opacity must be between 0 and 100."),
			rule("rule", "pattern", null, -1)
				.validationErrors());
		assertTrue(rule("rule", "pattern", null, 0)
			.validationErrors().isEmpty());
		assertTrue(rule("rule", "pattern", null, 100)
			.validationErrors().isEmpty());
		assertEquals(Arrays.asList("Opacity must be between 0 and 100."),
			rule("rule", "pattern", null, 101)
				.validationErrors());

		List<String> errors = rule("", "", -1, 101).validationErrors();
		assertEquals(Arrays.asList(
			"Name must contain 1 to 64 Unicode code points.",
			"Pattern must contain 1 to 512 Unicode code points.",
			"Background color must be a 24-bit RGB value.",
			"Opacity must be between 0 and 100."), errors);
	}

	@Test
	public void copyMethodsPreserveValuesWithoutMutatingSource()
	{
		NotificationRule source = new NotificationRule(UUID.fromString(
			"7df65dc5-c46f-450e-9152-a1959767b65f"), "Drops", true, "dragon",
			0xBF616A, 90, null);

		NotificationRule disabled = source.withEnabled(false);
		NotificationRule noted = disabled.withMigrationNote("legacy pattern");

		assertTrue(source.isEnabled());
		assertFalse(disabled.isEnabled());
		assertEquals("legacy pattern", noted.getMigrationNote());
		assertEquals(source.getId(), noted.getId());
		assertEquals(source.getPattern(), noted.getPattern());
		assertNotEquals(source, disabled);
		assertSame(source, source.withEnabled(true));
		assertSame(disabled, disabled.withMigrationNote(null));
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity,
			null);
	}
}
