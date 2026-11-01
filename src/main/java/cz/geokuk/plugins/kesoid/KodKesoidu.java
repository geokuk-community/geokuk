package cz.geokuk.plugins.kesoid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Z textu nebo odkazu vytáhne kódy kešoidů (GC19ACP, OC12345, WM1ABC…). Rozumí odkazům na coord.info, geocaching.com a dalším tvarům s parametrem {@code wp=} a
 * holým kódům v textu; kód v odkazu končí před {@code _}, {@code ?}, {@code #}, {@code /}, {@code &} nebo mezerou. Odkazy s GUID bez kódu nepodporuje. Co je kód
 * skutečně kešoidu, rozhoduje až shoda s identifikátory v datech ({@link #najdi}).
 */
public final class KodKesoidu {

	private static final String KOD = "([A-Z0-9]{3,10})(?![A-Z0-9])";
	private static final int PRIZNAKY = Pattern.CASE_INSENSITIVE;

	/** Kód v odkazu: za adresou známého serveru, v cestě nebo v parametru {@code wp}. */
	private static final Pattern[] ODKAZY = {
		Pattern.compile("coord\\.info/" + KOD, PRIZNAKY),
		Pattern.compile("geocaching\\.com/geocache/" + KOD, PRIZNAKY),
		Pattern.compile("waymarking\\.com/waymarks/" + KOD, PRIZNAKY),
		Pattern.compile("opencaching\\.[a-z.]+/(OC[A-Z0-9]{1,8})(?![A-Z0-9])", PRIZNAKY),
		Pattern.compile("[?&;]wp=" + KOD, PRIZNAKY) };

	/**
	 * Holý kód: dvě až tři písmena a číslice, nebo předpona GC, WM, OC, MU a písmena a číslice; neleží uvnitř slova. Čísla bez písmenné předpony se nepřijímají.
	 */
	private static final Pattern HOLY = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z]{2,3}[0-9]{1,8}|(?:GC|WM|OC|MU)[A-Za-z0-9]{2,8})(?![A-Za-z0-9])", PRIZNAKY);

	private KodKesoidu() {}

	/** Kódy, které text obsahuje, velkými písmeny a bez opakování: nejdřív kódy z odkazů v pořadí v textu, pak holé kódy. */
	public static List<String> kandidati(final String text) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}
		final Set<String> kody = new LinkedHashSet<>();
		final List<int[]> odkazy = new ArrayList<>();
		final List<String> zOdkazu = new ArrayList<>();
		for (final Pattern p : ODKAZY) {
			final Matcher m = p.matcher(text);
			while (m.find()) {
				odkazy.add(new int[] { m.start(), zOdkazu.size() });
				zOdkazu.add(m.group(1).toUpperCase(Locale.ROOT));
			}
		}
		odkazy.sort((a, b) -> Integer.compare(a[0], b[0]));
		for (final int[] o : odkazy) {
			kody.add(zOdkazu.get(o[1]));
		}
		final Matcher holy = HOLY.matcher(text);
		while (holy.find()) {
			kody.add(holy.group(1).toUpperCase(Locale.ROOT));
		}
		return new ArrayList<>(kody);
	}

	/**
	 * Kešoid, jehož identifikátor je některý kód z textu; když jich je víc, ten, jehož kód je v textu dřív. Null, když text kód neobsahuje nebo kód v datech není.
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
