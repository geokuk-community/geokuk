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

	/** Úseky seznamu waypointů. Bez úseků se index staví najednou; části skupin zdrojů mají vlastní index a souhrny, které se příště převezmou celé. */
	private final List<Usek> useky = new ArrayList<>();
	private Usek usek;

	private static final class Usek {
		final int od;
		final boolean castSkupiny;
		final Cast hotova;
		Indexator<Wpt> index;
		Set<Kesoid> kesoidy;
		int maxBestOf, maxHodnoceni, maxFavorit;

		Usek(final int od, final boolean castSkupiny, final Cast hotova) {
			this.od = od;
			this.castSkupiny = castSkupiny;
			this.hotova = hotova;
			if (castSkupiny && hotova == null) {
				kesoidy = new HashSet<>();
			}
		}
	}

	/** Hotová část bagu pro skupinu zdrojů: index jejích waypointů se souřadnicemi, její kešoidy a nejvyšší hodnoty. */
	public static final class Cast {
		final int pocetWpt;
		final List<Kesoid> kesoidy;
		final int maxBestOf, maxHodnoceni, maxFavorit;

		Cast(final int pocetWpt, final Usek u, final List<Kesoid> kesoidy) {
			this.pocetWpt = pocetWpt;
			this.kesoidy = kesoidy;
			maxBestOf = u.maxBestOf;
			maxHodnoceni = u.maxHodnoceni;
			maxFavorit = u.maxFavorit;
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
		if (usek != null && usek.castSkupiny) {
			usek.kesoidy.add(kesoid);
			if (kesoid instanceof Kes) {
				final Kes kes = (Kes) kesoid;
				usek.maxBestOf = Math.max(usek.maxBestOf, kes.getBestOf());
				usek.maxHodnoceni = Math.max(usek.maxHodnoceni, kes.getHodnoceni());
				usek.maxFavorit = Math.max(usek.maxFavorit, kes.getFavorit());
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
	 * Následující waypointy tvoří úsek. Čtené úseky se indexují spolu; část skupiny zdrojů dostane vlastní index a souhrny, které po {@link #done()} vrátí
	 * {@link #getCast(int)}. Části skupin nesdílejí kešoidy s ničím jiným v bagu.
	 */
	public void zacniUsek(final boolean castSkupiny) {
		usek = new Usek(wpts.size(), castSkupiny, null);
		useky.add(usek);
	}

	/**
	 * Převezme hotovou část skupiny i s jejími waypointy bez počítání po waypointech, jen alely se počítají znovu (genom mohl od minula přibrat geny).
	 *
	 * @return false, když část k waypointům nepatří (jiný počet); pak se mají přidat po jednom do {@link #zacniUsek(boolean) nové části}
	 */
	public boolean pridejCast(final Cast cast, final List<Wpt> wpty) {
		if (indexatorOdevzdan || indexator != null) {
			throw new IllegalStateException("Indexator uz byl odevztdan");
		}
		if (cast.pocetWpt != wpty.size()) {
			return false;
		}
		usek = new Usek(wpts.size(), true, cast);
		useky.add(usek);
		wpts.addAll(wpty);
		for (final Wpt wpt : wpty) {
			wpt.getGenotyp().countTo(citacAlel);
		}
		return true;
	}

	/** Hotová část pro úsek v pořadí vzniku, po {@link #done()}; null u úseku, který není částí skupiny, nebo u prázdné části. */
	public Cast getCast(final int poradiUseku) {
		final Usek u = useky.get(poradiUseku);
		final int doo = poradiUseku + 1 < useky.size() ? useky.get(poradiUseku + 1).od : wpts.size();
		if (!u.castSkupiny || doo == u.od) {
			return null;
		}
		return u.hotova != null ? u.hotova : new Cast(doo - u.od, u, new ArrayList<>(u.kesoidy));
	}

	public void done() {
		// Filtr přidá jen část z místa pro všechny waypointy.
		wpts.trimToSize();
		postavIndex();
		int pocet = kesoidyset.size();
		for (final Usek u : useky) {
			pocet += u.hotova != null ? u.hotova.kesoidy.size() : u.kesoidy != null ? u.kesoidy.size() : 0;
		}
		kesoidy = new ArrayList<>(pocet);
		kesoidy.addAll(kesoidyset);
		for (final Usek u : useky) {
			if (u.hotova != null) {
				kesoidy.addAll(u.hotova.kesoidy);
				maximalniBestOf = Math.max(maximalniBestOf, u.hotova.maxBestOf);
				maximalniHodnoceni = Math.max(maximalniHodnoceni, u.hotova.maxHodnoceni);
				maximalniFavorit = Math.max(maximalniFavorit, u.hotova.maxFavorit);
			} else if (u.kesoidy != null) {
				kesoidy.addAll(u.kesoidy);
				maximalniBestOf = Math.max(maximalniBestOf, u.maxBestOf);
				maximalniHodnoceni = Math.max(maximalniHodnoceni, u.maxHodnoceni);
				maximalniFavorit = Math.max(maximalniFavorit, u.maxFavorit);
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
			indexator = postav(wpts);

		}
	}

	private Indexator<Wpt> postavUsek(final int od, final int doo) {
		return doo > od ? postav(wpts.subList(od, doo)) : null;
	}

	private static Indexator<Wpt> pripoj(final Indexator<Wpt> celek, final Indexator<Wpt> dalsi) {
		if (dalsi == null) {
			return celek;
		}
		return celek == null ? dalsi : dalsi.merge(celek);
	}

	private static Indexator<Wpt> postav(final List<Wpt> wpty) {
		return Indexator.postav(BoundingRect.ALL, wpty, wpt -> wpt.getMou().xx, wpt -> wpt.getMou().yy);
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
