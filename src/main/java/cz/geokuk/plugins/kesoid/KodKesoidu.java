package cz.geokuk.plugins.kesoid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Z textu nebo odkazu vytáhne kódy kešoidů (GC19ACP, OC12345, WM1ABC…). Rozumí odkazům na coord.info, geocaching.com (stránka keše, tisková verze, logy, galerie,
 * mapa, i mobilní adresa), opencaching a waymarking a holým kódům v textu; kód v odkazu končí před {@code _}, {@code ?}, {@code #}, {@code /}, {@code &} nebo mezerou.
 * Nepodporuje odkazy s GUID a tvary, které na kešoid nevedou. Zpracuje nejvýš {@link #NEJVIC_ZNAKU} znaků textu. Co je kód skutečně kešoidu, rozhoduje až
 * shoda s identifikátory v datech ({@link #najdi}).
 */
public final class KodKesoidu {

	/** Delší text se ořízne; kód ani odkaz tak dlouhý není a výrazy nad dlouhým vstupem nezdržují. */
	public static final int NEJVIC_ZNAKU = 2000;

	/** Největší číselné ID keše, které se převede na kód: GC kódy do GCFFFF jsou šestnáctkový zápis ID. */
	private static final int NEJVETSI_ID = 0xFFFF;

	private static final int PRIZNAKY = Pattern.CASE_INSENSITIVE;
	private static final String KONEC = "(?![A-Z0-9])";
	/** Abeceda GC kódů nemá I, L, O, S, U. */
	private static final String GC = "(GC[0-9A-HJKMNPQRTV-Z]{1,7})" + KONEC;
	/** Předpony kešoidů sítě Opencaching: Německo OC, Polsko OP, Česko OZ, USA OU, Velká Británie OK, Nizozemsko OB, Rumunsko OR. */
	private static final String PREDPONY_OC = "OC|OP|OZ|OU|OK|OB|OR";
	/** Předpony Opencaching, které jako holé slovo v textu vyžadují aspoň jednu číslici (jinak by je splnila běžná slova: okno, obec, oprava). */
	private static final String PREDPONY_OC_S_CISLEM = "OP|OZ|OU|OK|OB|OR";
	private static final String OC = "((?:" + PREDPONY_OC + ")[A-Z0-9]{1,8})" + KONEC;
	private static final String WM = "(WM[A-Z0-9]{1,8})" + KONEC;
	/** Začátek jména serveru: ne uprostřed delšího jména. */
	private static final String SERVER = "(?<![A-Za-z0-9-])";
	private static final String OPENCACHING = SERVER + "opencaching\\.[a-z]{2,10}(?:\\.[a-z]{2,10})?/";
	/** Parametr dotazu: před ním je znak, který nepatří ke jménu parametru; zbytek dotazu do mezery nebo kotvy. */
	private static final String PARAMETR = "\\?[^\\s#]*?(?<![A-Za-z0-9])";
	private static final String STRANKY_GEOCACHING = "geocaching\\.com/(?:seek/(?:cache_details|cdpf|log|gallery)\\.aspx|map/default\\.aspx|play/map)";

	/** Kód v odkazu: za adresou známého serveru, v cestě nebo v parametru. */
	private static final Pattern[] ODKAZY = {
		Pattern.compile(SERVER + "coord\\.info/(?:" + GC + "|" + WM + ")", PRIZNAKY),
		Pattern.compile(SERVER + "geocaching\\.com/geocache/" + GC, PRIZNAKY),
		Pattern.compile(SERVER + "geocaching\\.com/play/map/" + GC, PRIZNAKY),
		Pattern.compile(SERVER + STRANKY_GEOCACHING + PARAMETR + "(?:wp|gc)=" + GC, PRIZNAKY),
		Pattern.compile(OPENCACHING + OC, PRIZNAKY),
		Pattern.compile(OPENCACHING + "viewcache\\.php" + PARAMETR + "wp=" + OC, PRIZNAKY),
		Pattern.compile(SERVER + "waymarking\\.com/waymarks/" + WM, PRIZNAKY) };

	/** Číselné ID keše v adrese stránky keše. */
	private static final Pattern ID_KESE = Pattern.compile(SERVER + "geocaching\\.com/seek/cache_details\\.aspx" + PARAMETR + "id=([0-9]{1,6})(?![0-9])", PRIZNAKY);

	/**
	 * Holý kód: GC s kódem v abecedě GC, předpona WM, MU, OC nebo další předpona Opencaching a písmena s aspoň jednou číslicí, nebo dvě až tři písmena a číslice; neleží uvnitř slova. Čísla bez písmenné
	 * předpony se nepřijímají. Za kódem smí být přípona {@code -číslo} (jedna až tři číslice), jak se píší zastávky Adventure Lab (GC5PGVC-3).
	 */
	private static final Pattern HOLY = Pattern.compile("(?<![A-Za-z0-9])(GC[0-9A-HJKMNPQRTV-Z]{1,7}|(?:WM|MU|OC)[A-Z0-9]{2,8}|(?=[A-Z0-9]*[0-9])(?:" + PREDPONY_OC_S_CISLEM + ")[A-Z0-9]{2,8}|[A-Z]{2,3}[0-9]{1,8})(-[0-9]{1,3})?(?![A-Za-z0-9])", PRIZNAKY);

	private KodKesoidu() {}

	/**
	 * Kódy, které text obsahuje, velkými písmeny a bez opakování: nejdřív kódy z odkazů v pořadí v textu, potom holé kódy mimo odkazy. Odkaz má tedy přednost před holým
	 * kódem, ať jsou v textu v jakémkoli pořadí.
	 */
	public static List<String> kandidati(final String vstup) {
		if (vstup == null || vstup.trim().isEmpty()) {
			return new ArrayList<>();
		}
		final String text = vstup.length() > NEJVIC_ZNAKU ? vstup.substring(0, NEJVIC_ZNAKU) : vstup;
		final Set<String> kody = new LinkedHashSet<>();
		final Map<Integer, String> zOdkazu = new TreeMap<>();
		for (final Pattern p : ODKAZY) {
			final Matcher m = p.matcher(text);
			while (m.find()) {
				final int skupina = m.group(1) != null ? 1 : 2;
				zOdkazu.putIfAbsent(m.start(skupina), m.group(skupina).toUpperCase(Locale.ROOT));
			}
		}
		final Matcher id = ID_KESE.matcher(text);
		while (id.find()) {
			final String kod = kodZId(id.group(1));
			if (kod != null) {
				zOdkazu.putIfAbsent(id.start(1), kod);
			}
		}
		kody.addAll(zOdkazu.values());
		for (final String token : text.split("\\s+")) {
			final String t = bezOkrajovychZnaku(token);
			if (t.isEmpty() || jeOdkaz(t)) {
				continue;
			}
			final Matcher holy = HOLY.matcher(t);
			while (holy.find()) {
				final String zaklad = holy.group(1).toUpperCase(Locale.ROOT);
				if (holy.group(2) != null) {
					kody.add(zaklad + holy.group(2));
				}
				kody.add(zaklad);
			}
		}
		return new ArrayList<>(kody);
	}

	/** GC kód z číselného ID keše; null, když ID převést neumíme. */
	private static String kodZId(final String cislo) {
		try {
			final int id = Integer.parseInt(cislo);
			return id < 1 || id > NEJVETSI_ID ? null : "GC" + Integer.toHexString(id).toUpperCase(Locale.ROOT);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	private static String bezOkrajovychZnaku(final String token) {
		int od = 0;
		int doKonce = token.length();
		while (od < doKonce && !Character.isLetterOrDigit(token.charAt(od))) {
			od++;
		}
		while (doKonce > od && !Character.isLetterOrDigit(token.charAt(doKonce - 1))) {
			doKonce--;
		}
		return token.substring(od, doKonce);
	}

	/** Slovo, které vypadá jako adresa nebo její část; kód z něj se bere jen podle známých tvarů odkazů. */
	private static boolean jeOdkaz(final String slovo) {
		return slovo.indexOf('/') >= 0 || slovo.indexOf('?') >= 0 || slovo.indexOf('=') >= 0 || slovo.indexOf('&') >= 0 || slovo.regionMatches(true, 0, "www.", 0, 4);
	}

	/**
	 * Kešoid, jehož identifikátor je některý kód z textu; když jich je víc, ten, který je v seznamu {@link #kandidati} dřív, tedy nejdřív kód z odkazu v pořadí v
	 * textu a pak holé kódy. U kódu s příponou zastávky ({@code GC5PGVC-3}) je dřív přesná shoda i s příponou, potom základní kešoid ({@code GC5PGVC}). Null, když text kód neobsahuje nebo kód v datech není.
	 */
	public static <T> T najdi(final String text, final Collection<? extends T> kesoidy, final Function<? super T, String> identifikator) {
		final List<String> kandidati = kandidati(text);
		if (kandidati.isEmpty()) {
			return null;
		}
		final Map<String, Integer> poradi = new HashMap<>();
		for (int i = 0; i < kandidati.size(); i++) {
			poradi.put(kandidati.get(i), i);
		}
		T nejlepsi = null;
		int nejlepsiPoradi = Integer.MAX_VALUE;
		for (final T kesoid : kesoidy) {
			final String id = identifikator.apply(kesoid);
			if (id == null) {
				continue;
			}
			final Integer p = poradi.get(id.toUpperCase(Locale.ROOT));
			if (p != null && p < nejlepsiPoradi) {
				nejlepsi = kesoid;
				nejlepsiPoradi = p;
			}
		}
		return nejlepsi;
	}
}
