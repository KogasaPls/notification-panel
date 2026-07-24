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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RuleCodec
{
	/**
	 * How long one stored configuration value may be.
	 *
	 * <p>Declared here because this is where an over-long value is refused on read. The store
	 * checks it before writing and the legacy migrator before importing, both through this
	 * constant, so nothing can be written that this would then reject.</p>
	 */
	static final int MAX_CONFIG_LENGTH = 262_144;

	private final Gson gson;

	public RuleCodec(Gson gson)
	{
		this.gson = Objects.requireNonNull(gson, "gson").newBuilder().serializeNulls().create();
	}

	public String encode(RuleDocument document)
	{
		Objects.requireNonNull(document, "document");
		DocumentDto dto = new DocumentDto();
		dto.schemaVersion = document.getSchemaVersion();
		dto.migrationWarnings = new ArrayList<>(document.getMigrationWarnings());
		dto.rules = new ArrayList<>();
		for (NotificationRule rule : document.getRules())
		{
			RuleDto ruleDto = new RuleDto();
			ruleDto.id = rule.getId().toString();
			ruleDto.name = rule.getName();
			ruleDto.enabled = rule.isEnabled();
			ruleDto.pattern = rule.getPattern();
			ruleDto.backgroundColor = rule.getBackgroundRgb() == null
				? null : String.format("#%06X", rule.getBackgroundRgb());
			ruleDto.opacityPercent = rule.getOpacityPercent();
			ruleDto.migrationNote = rule.getMigrationNote();
			dto.rules.add(ruleDto);
		}
		return gson.toJson(dto);
	}

	public DecodeResult decode(String encoded)
	{
		if (encoded != null && encoded.length() > MAX_CONFIG_LENGTH)
		{
			return DecodeResult.failure(
				"Structured rule data exceeds " + MAX_CONFIG_LENGTH + " characters.");
		}

		DocumentDto dto;
		try
		{
			dto = gson.fromJson(encoded, DocumentDto.class);
		}
		catch (JsonParseException exception)
		{
			return DecodeResult.failure("Structured rules are not valid JSON.");
		}
		if (dto == null)
		{
			return DecodeResult.failure("Structured rules are not valid JSON.");
		}
		if (dto.schemaVersion != RuleDocument.CURRENT_SCHEMA_VERSION)
		{
			return DecodeResult.failure("Unsupported structured-rule schema version: "
				+ dto.schemaVersion + ".");
		}
		if (dto.migrationWarnings == null)
		{
			return malformed("migration warnings array is missing.");
		}
		for (String warning : dto.migrationWarnings)
		{
			if (warning == null)
			{
				return malformed("migration warning entries must not be null.");
			}
		}
		if (dto.rules == null)
		{
			return malformed("rules array is missing.");
		}
		if (dto.rules.size() > RuleSet.MAX_RULES)
		{
			return malformed("a document may contain at most " + RuleSet.MAX_RULES + " rules.");
		}

		List<NotificationRule> rules = new ArrayList<>();
		Set<UUID> ids = new HashSet<>();
		for (RuleDto ruleDto : dto.rules)
		{
			if (ruleDto == null)
			{
				return malformed("rules must not contain a null rule.");
			}

			UUID id;
			try
			{
				id = UUID.fromString(ruleDto.id);
			}
			catch (IllegalArgumentException | NullPointerException exception)
			{
				return malformed("rule UUID is missing or invalid.");
			}
			if (!id.toString().equalsIgnoreCase(ruleDto.id))
			{
				return malformed("rule UUID is missing or invalid.");
			}
			if (!ids.add(id))
			{
				return malformed("rule UUIDs must be unique.");
			}
			if (ruleDto.name == null)
			{
				return malformed("rule name must not be null.");
			}
			if (ruleDto.pattern == null)
			{
				return malformed("rule pattern must not be null.");
			}

			Integer backgroundRgb = null;
			if (ruleDto.backgroundColor != null)
			{
				if (!isRgbColor(ruleDto.backgroundColor))
				{
					return malformed("rule background color must use #RRGGBB.");
				}
				backgroundRgb = Integer.parseInt(ruleDto.backgroundColor.substring(1), 16);
			}
			if (ruleDto.opacityPercent != null
				&& (ruleDto.opacityPercent < 0 || ruleDto.opacityPercent > 100))
			{
				return malformed("rule opacity must be between 0 and 100.");
			}

			rules.add(new NotificationRule(id, ruleDto.name, ruleDto.enabled, ruleDto.pattern,
				backgroundRgb, ruleDto.opacityPercent, ruleDto.migrationNote));
		}

		return DecodeResult.success(new RuleDocument(dto.schemaVersion, dto.migrationWarnings,
			rules));
	}

	private static boolean isRgbColor(String value)
	{
		if (value.length() != 7 || value.charAt(0) != '#')
		{
			return false;
		}
		for (int index = 1; index < value.length(); index++)
		{
			char character = value.charAt(index);
			if (!((character >= '0' && character <= '9')
				|| (character >= 'A' && character <= 'F')
				|| (character >= 'a' && character <= 'f')))
			{
				return false;
			}
		}
		return true;
	}

	private static DecodeResult malformed(String reason)
	{
		return DecodeResult.failure("Structured rule data is malformed: " + reason);
	}

	public static final class DecodeResult
	{
		private final RuleDocument document;
		private final String error;

		private DecodeResult(RuleDocument document, String error)
		{
			this.document = document;
			this.error = error;
		}

		private static DecodeResult success(RuleDocument document)
		{
			return new DecodeResult(document, null);
		}

		private static DecodeResult failure(String error)
		{
			return new DecodeResult(null, error);
		}

		public boolean isSuccess()
		{
			return document != null;
		}

		public RuleDocument getDocument()
		{
			return document;
		}

		public String getError()
		{
			return error;
		}
	}

	private static final class DocumentDto
	{
		private int schemaVersion;
		private List<String> migrationWarnings;
		private List<RuleDto> rules;
	}

	private static final class RuleDto
	{
		private String id;
		private String name;
		private boolean enabled;
		private String pattern;
		private String backgroundColor;
		private Integer opacityPercent;
		private String migrationNote;
	}
}
