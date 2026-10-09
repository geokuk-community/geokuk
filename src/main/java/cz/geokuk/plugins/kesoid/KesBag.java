package cz.geokuk.plugins.kesoid;

import java.util.*;

import cz.geokuk.plugins.kesoid.genetika.*;
import cz.geokuk.plugins.kesoid.genetika.Genom.CitacAlel;
import cz.geokuk.plugins.kesoid.importek.InformaceOZdrojich;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.util.index2d.BoundingRect;
import cz.geokuk.util.index2d.Indexator;
import cz.geokuk.util.lang.CounterMap;

/**
 * Kešbag drží všechny kešoidy a jejich waypointy a to i zaindexované podle pozice.
 * Vznikají dvě instance:
 *   1. Načtené kešoidy.
 *   2. Vyfiltroivané kešoidy (bude jich míň).
 *
 * Kešoid dostane genom, který pak sdílý. Obě instance sdílejí stejný genom.
 * Během plnění KesBag se může plnit i genom o nové alely, geny a geny vstupují do druhů.
 *
 * Postup je:
 *
 * <pre>
 *    new KesBag(genom)
 *    v cyklu:
 *       add(wpt)
 *    done()
 * </pre>
 * Teprve po done() je připrven k poskytování informací.
 * Musí se do něj vložit všechny waypointy už provázané do kešoidů, kešoidy si odvodí sám.
 * Může tedy obsahovat jen některé waypointy kešoidů, což se často děje právě při filtrování.
 *
 * @author veverka
 *
 */
public class KesBag {
	//

	private final ArrayList<Wpt> wpts;
	private Set<Kesoid> kesoidyset;
	private List<Kesoid> kesoidy;

	private CounterMap<Alela> poctyAlel;

	private Indexator<Wpt> indexator;


	private int maximalniBestOf = 0;
	private int maximalniHodnoceni;
	private int maximalniFavorit;
	private final Genom genom;

	private final CitacAlel citacAlel;

	private InformaceOZdrojich iInformaceOZdrojich;

	private boolean indexatorOdevzdan = false;

	/** Skupiny zdrojů v bagu: kešoidy a nejvyšší hodnoty se počítají za skupinu zvlášť a příště se převezmou celé. */
	private final Map<Object, Skupina> skupiny = new LinkedHashMap<>();
	/** Skupina, do které jdou právě přidávané waypointy, nebo null. */
	private Skupina skupina;

	private static final class Skupina {
		final Cast hotova;
		Set<Kesoid> kesoidy;
		List<Kesoid> seznamKesoidu;
		int pocetWpt;
		int maxBestOf, maxHodnoceni, maxFavorit;

		Skupina(final Cast hotova) {
			this.hotova = hotova;
			if (hotova == null) {
				kesoidy = new HashSet<>();
			}
		}
	}

	/** Hotová část bagu pro skupinu zdrojů: počet jejích waypointů se souřadnicemi, její kešoidy a nejvyšší hodnoty. */
	public static final class Cast {
		final int pocetWpt;
		final List<Kesoid> kesoidy;
		final int maxBestOf, maxHodnoceni, maxFavorit;

		Cast(final Skupina s) {
			pocetWpt = s.pocetWpt;
			kesoidy = s.seznamKesoidu;
			maxBestOf = s.maxBestOf;
			maxHodnoceni = s.maxHodnoceni;
			maxFavorit = s.maxFavorit;
		}

		public int getPocetWpt() {
			return pocetWpt;
		}
	}

	public KesBag(final Genom genom) {
		this(genom, 10);
	}

	/** S místem pro očekávaný počet waypointů, aby seznam při přidávání nerostl. */
	public KesBag(final Genom genom, final int ocekavanyPocetWpt) {
		this.genom = genom;
		wpts = new ArrayList<>(ocekavanyPocetWpt);
		kesoidyset = new HashSet<>();
		citacAlel = genom.createCitacAlel();
	}

	public void add(final Wpt wpt) {
		if (indexatorOdevzdan || indexator != null) {
			throw new IllegalStateException("Indexator uz byl odevztdan");
		}
		// Následné volání má vedlejší efekt spočívající ve výpočtu genotypu a schování ve Wpt.
		// Přitom ovšem může docházet ke vniku alel, genů a přidávání genů do druhů.
		// Tento efekt je důležitý, dělat to líně by bylo divné.
		wpt.computeGenotypIfNotExistsForAllRing(genom);
		if (wpt.hasEmptyCoords()) {
			// On se sice nepřidá do bagu, takže přímo nebudezobrazen, ale přesto je v ringu kešoidových waypointů.
			// a za určité situace se na něj dostaneme, nejspíš bude vidět v seznamu keší.
			// TODO Udělat ak, aby nebyl ani v ringu od začátku.
			wpt.removeMeFromRing();
			return;
		}

		final Genotyp genotyp = wpt.getGenotyp();

		final Kesoid kesoid = wpt.getKesoid();
		wpts.add(wpt);
		if (skupina != null) {
			skupina.kesoidy.add(kesoid);
			skupina.pocetWpt++;
			if (kesoid instanceof Kes) {
				final Kes kes = (Kes) kesoid;
				skupina.maxBestOf = Math.max(skupina.maxBestOf, kes.getBestOf());
				skupina.maxHodnoceni = Math.max(skupina.maxHodnoceni, kes.getHodnoceni());
				skupina.maxFavorit = Math.max(skupina.maxFavorit, kes.getFavorit());
			}
		} else {
			kesoidyset.add(kesoid);
			if (kesoid instanceof Kes) {
				final Kes kes = (Kes) kesoid;
				maximalniBestOf = Math.max(maximalniBestOf, kes.getBestOf());
				maximalniHodnoceni = Math.max(maximalniHodnoceni, kes.getHodnoceni());
				maximalniFavorit = Math.max(maximalniFavorit, kes.getFavorit());
			}
		}
		genotyp.countTo(citacAlel);
	}

	/**
	 * Následující waypointy patří skupině zdrojů (null: čtené waypointy). Skupina si počítá kešoidy a nejvyšší hodnoty zvlášť, po {@link #done()} je vrátí
	 * {@link #getCast(Object)}; s ničím jiným v bagu kešoidy nesdílí. Waypointy skupiny mohou přijít ve víc úsecích, na místech jejích zdrojů.
	 *
	 * @param hotova
	 *            část skupiny z minula; její waypointy se pak přidají {@link #pridejHotove(List)}, bez počítání po waypointech
	 */
	public void zacniSkupinu(final Object klic, final Cast hotova) {
		skupina = klic == null ? null : skupiny.computeIfAbsent(klic, k -> new Skupina(hotova));
	}

	/** Přidá waypointy hotové části skupiny; jen alely se počítají znovu (genom mohl od minula přibrat geny). */
	public void pridejHotove(final List<Wpt> wpty) {
		if (indexatorOdevzdan || indexator != null) {
			throw new IllegalStateException("Indexator uz byl odevztdan");
		}
		wpts.addAll(wpty);
		for (final Wpt wpt : wpty) {
			wpt.getGenotyp().countTo(citacAlel);
		}
	}

	/** Část skupiny po {@link #done()}: hotová převzatá, nebo nově spočítaná; null u skupiny bez waypointů. */
	public Cast getCast(final Object klic) {
		final Skupina s = skupiny.get(klic);
		if (s == null) {
			return null;
		}
		if (s.hotova != null) {
			return s.hotova;
		}
		return s.pocetWpt == 0 ? null : new Cast(s);
	}

	public void done() {
		// Filtr přidá jen část z místa pro všechny waypointy.
		wpts.trimToSize();
		postavIndex();
		int pocet = kesoidyset.size();
		for (final Skupina s : skupiny.values()) {
			pocet += s.hotova != null ? s.hotova.kesoidy.size() : s.kesoidy.size();
		}
		kesoidy = new ArrayList<>(pocet);
		kesoidy.addAll(kesoidyset);
		for (final Skupina s : skupiny.values()) {
			if (s.hotova != null) {
				kesoidy.addAll(s.hotova.kesoidy);
				maximalniBestOf = Math.max(maximalniBestOf, s.hotova.maxBestOf);
				maximalniHodnoceni = Math.max(maximalniHodnoceni, s.hotova.maxHodnoceni);
				maximalniFavorit = Math.max(maximalniFavorit, s.hotova.maxFavorit);
			} else {
				// Množina se po dokončení nedrží, část si nese jen seznam.
				s.seznamKesoidu = new ArrayList<>(s.kesoidy);
				s.kesoidy = null;
				kesoidy.addAll(s.seznamKesoidu);
				maximalniBestOf = Math.max(maximalniBestOf, s.maxBestOf);
				maximalniHodnoceni = Math.max(maximalniHodnoceni, s.maxHodnoceni);
				maximalniFavorit = Math.max(maximalniFavorit, s.maxFavorit);
			}
		}
		kesoidyset = null;
		poctyAlel = citacAlel.getCounterMap();
		// System.out.println(poctyAlel);
	}

	/**
	 * @return the genom
	 */
	public Genom getGenom() {
		return genom;
	}

	public Indexator<Wpt> getIndexator() {
		postavIndex();
		indexatorOdevzdan = true;
		return indexator;
	}

	/** Index se staví najednou ze všech přidaných waypointů. */
	private void postavIndex() {
		if (indexator == null) {
			indexator = Indexator.postav(BoundingRect.ALL, wpts, wpt -> wpt.getMou().xx, wpt -> wpt.getMou().yy);
		}
	}

	/**
	 * @return the informaceOZdrojich
	 */
	public InformaceOZdrojich getInformaceOZdrojich() {
		return iInformaceOZdrojich;
	}

	public List<Kesoid> getKesoidy() {
		if (kesoidy == null) {
			throw new RuntimeException("Jeste neni kesBag vytvoren");
		}
		return kesoidy;
	}

	/**
	 * @return the maximalniBestOf
	 */
	public int getMaximalniBestOf() {
		return maximalniBestOf;
	}

	public int getMaximalniFavorit() {
		return maximalniFavorit;
	}

	public int getMaximalniHodnoceni() {
		return maximalniHodnoceni;
	}

	/**
	 * @return the poctyAlel
	 */
	public CounterMap<Alela> getPoctyAlel() {
		return poctyAlel;
	}

	public Set<Alela> getPouziteAlely() {
		// TODO optimalizovat
		return poctyAlel.getMap().keySet();
	}

	public List<Wpt> getWpts() {
		if (kesoidy == null) {
			throw new RuntimeException("Jeste neni kesBag vytvoren");
		}
		return wpts;
	}

	/**
	 * @param informaceOZdrojich
	 *            the informaceOZdrojich to set
	 */
	public void setInformaceOZdrojich(final InformaceOZdrojich informaceOZdrojich) {
		iInformaceOZdrojich = informaceOZdrojich;
	}
}
