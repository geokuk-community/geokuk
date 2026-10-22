package cz.geokuk.util.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;

/** Textové soubory, které vznikly v UTF-8, v UTF-16 nebo ve starších verzích v kódování Windows (cp1250). */
public final class TextovySoubor {

	public static final Charset CP1250 = Charset.forName("windows-1250");

	/** Obsah souboru; neplatné UTF-8 se čte jako cp1250. Chybějící soubor je prázdný text. */
	public static String nacti(final File soubor) throws IOException {
		if (!soubor.isFile()) {
			return "";
		}
		return dekoduj(Files.readAllBytes(soubor.toPath()));
	}

	/** Text z bajtů: UTF-16 podle značky BOM, jinak UTF-8 a při neplatném UTF-8 cp1250. */
	public static String dekoduj(final byte[] bajty) {
		if (bajty.length >= 2 && (bajty[0] == (byte) 0xFF && bajty[1] == (byte) 0xFE || bajty[0] == (byte) 0xFE && bajty[1] == (byte) 0xFF)) {
			final String text = new String(bajty, StandardCharsets.UTF_16);
			return text.startsWith("\uFEFF") ? text.substring(1) : text;
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bajty)).toString();
		} catch (final CharacterCodingException e) {
			text = new String(bajty, CP1250);
		}
		return text.startsWith("﻿") ? text.substring(1) : text;
	}

	private TextovySoubor() {}
}
