package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Stav všech položek zdrojů. Píše vlákno načítání a vlákno, které přepíná zdroje, čte kdokoli (snímek je neměnný). Zápis stavu vypnuté položky i zápis
 * zrušeného běhu (jiná generace) se ignoruje, aby zpožděné zrušené načítání nepřepsalo vypnutí ani nový běh.
 */
public class RegistrStavuZdroju {

	private final Map<File, StavPolozky> polozky = new LinkedHashMap<>();
	private volatile StavZdroju snimek = StavZdroju.PRAZDNY;
	private volatile Map<File, TypZdroje> typy = Collections.emptyMap();
	private volatile Runnable posluchac = () -> {};
	/** Zvyšuje se s každým během načítání i přepnutím zdroje; zápis s jinou generací pochází ze zrušeného běhu. */
	private int generace;

	/** Volá se po každé změně z vlákna, které ji provedlo. */
	public void setPosluchac(final Runnable posluchac) {
		this.posluchac = posluchac;
	}

	/** Generace pro zápisy běhu, který začal po posledním přepnutí (testy a ukázky stavu bez načítání). */
	public synchronized int getGenerace() {
		return generace;
	}

	public StavZdroju getSnimek() {
		return snimek;
	}

	/** Soubory typu podle posledního skenu. */
	public List<File> getSoubory(final TypZdroje typ) {
		final List<File> vysledek = new ArrayList<>();
		for (final StavPolozky p : snimek.getPolozky(typ)) {
			vysledek.add(p.getSoubor());
		}
		return vysledek;
	}

	/** Typ souboru podle posledního skenu, null u neznámého. */
	public TypZdroje getTyp(final File soubor) {
		return typy.get(soubor);
	}

	/**
	 * Nový seznam položek po skenu, vrací generaci pro zápisy běhu načítání. Načítaná položka (zapnutá a s nevypnutým typem) zůstane načtená (nebo chybná, nebo čekající na zápis), dokud na ni nepřijde řada, ostatní
	 * zapnuté čekají na řadu; ostatní jsou vypnuté. Počty waypointů se pamatují.
	 */
	public int prepis(final List<File> soubory, final Function<File, TypZdroje> typ, final Function<File, String> nazev, final Predicate<File> zapnuto,
			final Predicate<TypZdroje> typVypnut, final ToLongFunction<File> velikost) {
		final int gen;
		synchronized (this) {
			final Map<File, TypZdroje> noveTypy = new HashMap<>();
			for (final File f : soubory) {
				noveTypy.put(f, typ.apply(f));
			}
			typy = noveTypy;
			final Map<File, StavPolozky> stare = new HashMap<>(polozky);
			polozky.clear();
			for (final File f : soubory) {
				final StavPolozky predtim = stare.get(f);
				final boolean zap = zapnuto.test(f);
				final boolean vypnutyTyp = typVypnut.test(noveTypy.get(f));
				final StavZdroje stav;
				if (!zap || vypnutyTyp) {
					stav = StavZdroje.VYPNUTO;
				} else if (predtim != null && predtim.isNacitat() && (predtim.getStav() == StavZdroje.NACTENO || predtim.getStav() == StavZdroje.CHYBA || predtim.getStav() == StavZdroje.CEKA_NA_ZAPIS)) {
					stav = predtim.getStav();
				} else {
					stav = StavZdroje.CEKA_NA_RADU;
				}
				final StavPolozky nova = predtim != null ? predtim.sTypem(noveTypy.get(f)) : new StavPolozky(f, nazev.apply(f), noveTypy.get(f), stav, 0, 0, StavPolozky.NEZNAMO, 0, zap, vypnutyTyp, null);
				polozky.put(f, nova.sZapnutim(zap).sTypVypnut(vypnutyTyp).s(stav, 0, stav == StavZdroje.CHYBA && predtim != null ? predtim.getChyba() : null).sVelikosti(velikost.applyAsLong(f)));
			}
			obnovSnimek();
			gen = ++generace;
		}
		posluchac.run();
		return gen;
	}

	/**
	 * Přepnutí zapnutí z jiného vlákna než načítání: stav se změní hned, bez čekání na načítání. Nová generace zahodí zápisy celého běžícího běhu, nejen přepnutých
	 * položek; to platí jen proto, že každé přepnutí běh zruší a spustí nový ({@code KesoidModel.zmenZapnute}).
	 */
	public void prepisZapnuti(final Predicate<File> zapnuto, final Predicate<TypZdroje> typVypnut) {
		boolean zmena = false;
		synchronized (this) {
			for (final Map.Entry<File, StavPolozky> e : polozky.entrySet()) {
				final StavPolozky p = e.getValue();
				final boolean zap = zapnuto.test(e.getKey());
				final boolean vypnutyTyp = typVypnut.test(p.getTyp());
				if (zap == p.isZapnuto() && vypnutyTyp == p.isTypVypnut()) {
					continue;
				}
				final boolean nacitat = zap && !vypnutyTyp;
				StavPolozky nova = p.sZapnutim(zap).sTypVypnut(vypnutyTyp);
				if (nacitat != p.isNacitat()) {
					nova = nova.s(nacitat ? StavZdroje.CEKA_NA_RADU : StavZdroje.VYPNUTO, 0, null);
				}
				e.setValue(nova);
				zmena = true;
			}
			if (zmena) {
				obnovSnimek();
				generace++;
			}
		}
		if (zmena) {
			posluchac.run();
		}
	}

	public void zacina(final int gen, final File soubor) {
		zmen(gen, soubor, p -> p.s(StavZdroje.NACITA_SE, 0, null));
	}

	/** Postup nikdy neklesá, loader může založit víc průběhů za sebou. */
	public void postup(final int gen, final File soubor, final int procent) {
		zmen(gen, soubor, p -> p.getStav() == StavZdroje.NACITA_SE && procent > p.getPostup() ? p.s(StavZdroje.NACITA_SE, Math.min(99, procent), null) : null);
	}

	public void hotovo(final int gen, final File soubor, final int celkem, final int brano) {
		zmen(gen, soubor, p -> p.s(StavZdroje.NACTENO, 0, null).sPocty(celkem, brano));
	}

	public void cekaNaZapis(final int gen, final File soubor) {
		zmen(gen, soubor, p -> p.s(StavZdroje.CEKA_NA_ZAPIS, 0, null));
	}

	public void chyba(final int gen, final File soubor, final String chyba) {
		zmen(gen, soubor, p -> p.s(StavZdroje.CHYBA, 0, chyba));
	}

	private void zmen(final int gen, final File soubor, final Function<StavPolozky, StavPolozky> uprava) {
		synchronized (this) {
			final StavPolozky p = polozky.get(soubor);
			if (gen != generace || p == null || !p.isNacitat()) {
				return;
			}
			final StavPolozky nova = uprava.apply(p);
			if (nova == null) {
				return;
			}
			polozky.put(soubor, nova);
			obnovSnimek();
		}
		posluchac.run();
	}

	/** Pořadí ve snímku je stálé: podle typu (GPX, GeoGet, GSAK, OpenSAK), pak podle názvu. */
	private void obnovSnimek() {
		final List<StavPolozky> seznam = new ArrayList<>(polozky.values());
		seznam.sort(Comparator.comparing(StavPolozky::getTyp).thenComparing(p -> p.getNazev().toLowerCase(Locale.ROOT)).thenComparing(StavPolozky::getCesta));
		snimek = new StavZdroju(seznam);
	}
}
