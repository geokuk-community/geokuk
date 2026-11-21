package cz.geokuk.util.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Simple reader of Windows INI files: sections and keys are case-insensitive, the first occurrence of a key wins. */
public final class IniFile {

	private final Map<String, Map<String, String>> sections = new HashMap<>();

	private IniFile() {}

	/** Reads the file in UTF-8, UTF-16 with BOM or windows-1250; a missing file is empty. */
	public static IniFile read(final File file) throws IOException {
		return parse(TextovySoubor.nacti(file));
	}

	public static IniFile parse(final String text) {
		final IniFile ini = new IniFile();
		Map<String, String> section = null;
		final String withoutBom = text.startsWith("\uFEFF") ? text.substring(1) : text;
		for (final String raw : withoutBom.split("\r\n|\r|\n")) {
			final String line = raw.trim();
			if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("[") && line.endsWith("]")) {
				section = ini.sections.computeIfAbsent(norm(line.substring(1, line.length() - 1)), k -> new HashMap<>());
				continue;
			}
			final int eq = line.indexOf('=');
			if (section != null && eq > 0) {
				section.putIfAbsent(norm(line.substring(0, eq)), line.substring(eq + 1).trim());
			}
		}
		return ini;
	}

	/** Value of the key, or null when the section or the key is missing. */
	public String get(final String section, final String key) {
		final Map<String, String> s = sections.get(norm(section));
		return s == null ? null : s.get(norm(key));
	}

	private static String norm(final String s) {
		return s.trim().toLowerCase(Locale.ROOT);
	}
}
