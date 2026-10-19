package cz.geokuk.core.napoveda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.geokuk.plugins.mapy.sprava.JsonCteni;

/** Popisy vydání pro dialog Co je nového: z CHANGELOG.md v programu nebo z vydání na GitHubu. */
public final class CoJeNoveho {

	/** Popis jedné verze v Markdownu. */
	public static final class Vydani {
		private final String verze;
		private final String markdown;

		Vydani(final String verze, final String markdown) {
			this.verze = verze;
			this.markdown = markdown;
		}

		public String getVerze() {
			return verze;
		}

		public String getMarkdown() {
			return markdown;
		}

		boolean isTestovaci() {
			return verze.contains("-");
		}

		String zaklad() {
			return verze.split("-", 2)[0];
		}
	}

	static final String CHANGELOG = "/CHANGELOG.md";

	/** Odkaz v Markdownu, nebo holá adresa; jedním průchodem, aby se adresa uvnitř odkazu nepřevedla podruhé. */
	private static final Pattern ODKAZY = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)|(?<![\"=>])(https?://[^\\s<]+[^\\s<.,;:)])");
	private static final Pattern NADPIS = Pattern.compile("^(#{1,6})\\s+(.*)$");
	private static final Pattern KOD = Pattern.compile("`([^`]+)`");
	private static final Pattern TUCNE = Pattern.compile("\\*\\*([^*]+)\\*\\*");

	private CoJeNoveho() {}

	/** Verze z CHANGELOG.md přibaleného v programu, nejnovější první; bez souboru prázdný seznam. */
	public static List<Vydani> zProgramu() {
		try (InputStream in = CoJeNoveho.class.getResourceAsStream(CHANGELOG)) {
			if (in == null) {
				return Collections.emptyList();
			}
			final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			final byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return zChangelogu(new String(out.toByteArray(), StandardCharsets.UTF_8));
		} catch (final IOException e) {
			return Collections.emptyList();
		}
	}

	/** Oddíly „## verze“ z CHANGELOG.md v pořadí souboru. */
	static List<Vydani> zChangelogu(final String md) {
		final List<Vydani> vydani = new ArrayList<>();
		String verze = null;
		StringBuilder text = new StringBuilder();
		for (final String radek : md.split("\\r?\\n", -1)) {
			if (radek.startsWith("## ")) {
				if (verze != null) {
					vydani.add(new Vydani(verze, text.toString().trim()));
				}
				verze = radek.substring(3).trim();
				text = new StringBuilder();
			} else if (verze != null) {
				text.append(radek).append('\n');
			}
		}
		if (verze != null) {
			vydani.add(new Vydani(verze, text.toString().trim()));
		}
		return vydani;
	}

	/** Vydání z odpovědi GitHub API (pole vydání); koncepty se vynechají. */
	static List<Vydani> zGitHubu(final String json) throws IOException {
		final Object o = JsonCteni.precti(json);
		if (!(o instanceof List)) {
			throw new IOException("Odpověď serveru není seznam vydání.");
		}
		final List<Vydani> vydani = new ArrayList<>();
		for (final Object prvek : (List<?>) o) {
			if (!(prvek instanceof Map)) {
				continue;
			}
			final Map<?, ?> m = (Map<?, ?>) prvek;
			final Object tag = m.get("tag_name");
			if (!(tag instanceof String) || Boolean.TRUE.equals(m.get("draft"))) {
				continue;
			}
			final Object telo = m.get("body");
			vydani.add(new Vydani(((String) tag).replaceFirst("^v", ""), telo instanceof String ? ((String) telo).trim() : ""));
		}
		return vydani;
	}

	/**
	 * Verze novější než používaná až po novou včetně, nejnovější první. Testovací verze jen s beta kanálem nebo když je
	 * nabízená; testovací verze téže řady obsahují i předchozí, proto z každé řady jen nejnovější.
	 */
	static List<Vydani> mezi(final List<Vydani> vsechna, final String pouzivana, final String nova, final boolean betaKanal) {
		final List<Vydani> vybrana = new ArrayList<>();
		for (final Vydani v : vsechna) {
			if (!ZkontrolovatAktualizaceSwingWorker.jeNovejsi(v.getVerze(), pouzivana) || ZkontrolovatAktualizaceSwingWorker.jeNovejsi(v.getVerze(), nova)) {
				continue;
			}
			if (v.isTestovaci() && !betaKanal && !v.getVerze().equals(nova)) {
				continue;
			}
			vybrana.add(v);
		}
		vybrana.sort((a, b) -> ZkontrolovatAktualizaceSwingWorker.jeNovejsi(a.getVerze(), b.getVerze()) ? -1
				: ZkontrolovatAktualizaceSwingWorker.jeNovejsi(b.getVerze(), a.getVerze()) ? 1 : 0);
		final Set<String> rady = new HashSet<>();
		vybrana.removeIf(v -> !rady.add(v.zaklad()));
		return vybrana;
	}

	/** HTML pro dialog: nadpis každé verze a její popis. */
	public static String html(final List<Vydani> vydani) {
		final StringBuilder sb = new StringBuilder("<html><body>");
		for (final Vydani v : vydani) {
			sb.append("<h2>").append(escape(v.getVerze())).append("</h2>");
			sb.append(naHtml(v.getMarkdown()));
		}
		return sb.append("</body></html>").toString();
	}

	/** Jednoduchý Markdown (nadpisy, odrážky s pokračováním na dalších řádcích, odstavce, odkazy, kód, tučně) na HTML. */
	static String naHtml(final String md) {
		final StringBuilder sb = new StringBuilder();
		StringBuilder odstavec = null;
		StringBuilder polozka = null;
		boolean seznam = false;
		for (final String radek : md.split("\\r?\\n", -1)) {
			final String r = radek.trim();
			final boolean odrazka = r.startsWith("- ") || r.startsWith("* ");
			final boolean pokracovani = !r.isEmpty() && polozka != null && !odrazka && Character.isWhitespace(radek.charAt(0));
			if (pokracovani) {
				polozka.append(' ').append(r);
				continue;
			}
			if (polozka != null) {
				sb.append("<li>").append(radkovy(polozka.toString())).append("</li>");
				polozka = null;
			}
			if (odrazka) {
				if (odstavec != null) {
					sb.append("<p>").append(radkovy(odstavec.toString())).append("</p>");
					odstavec = null;
				}
				if (!seznam) {
					sb.append("<ul>");
					seznam = true;
				}
				polozka = new StringBuilder(r.substring(2).trim());
				continue;
			}
			if (seznam && !r.isEmpty()) {
				sb.append("</ul>");
				seznam = false;
			}
			if (r.isEmpty()) {
				if (odstavec != null) {
					sb.append("<p>").append(radkovy(odstavec.toString())).append("</p>");
					odstavec = null;
				}
				continue;
			}
			final Matcher nadpis = NADPIS.matcher(r);
			if (nadpis.matches()) {
				if (odstavec != null) {
					sb.append("<p>").append(radkovy(odstavec.toString())).append("</p>");
					odstavec = null;
				}
				final int uroven = Math.min(6, Math.max(3, nadpis.group(1).length()));
				sb.append("<h").append(uroven).append('>').append(radkovy(nadpis.group(2))).append("</h").append(uroven).append('>');
				continue;
			}
			odstavec = odstavec == null ? new StringBuilder(r) : odstavec.append(' ').append(r);
		}
		if (polozka != null) {
			sb.append("<li>").append(radkovy(polozka.toString())).append("</li>");
		}
		if (seznam) {
			sb.append("</ul>");
		}
		if (odstavec != null) {
			sb.append("<p>").append(radkovy(odstavec.toString())).append("</p>");
		}
		return sb.toString();
	}

	/** Úpravy uvnitř řádku; text se nejdřív escapuje, značky vznikají až z Markdownu. */
	static String radkovy(final String text) {
		String s = escape(text);
		s = KOD.matcher(s).replaceAll("<code>$1</code>");
		s = TUCNE.matcher(s).replaceAll("<b>$1</b>");
		final Matcher m = ODKAZY.matcher(s);
		final StringBuffer sb = new StringBuffer();
		while (m.find()) {
			final String odkaz = m.group(1) != null ? "<a href=\"" + m.group(2) + "\">" + m.group(1) + "</a>" : "<a href=\"" + m.group(3) + "\">" + m.group(3) + "</a>";
			m.appendReplacement(sb, Matcher.quoteReplacement(odkaz));
		}
		return m.appendTail(sb).toString();
	}

	public static String escape(final String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
