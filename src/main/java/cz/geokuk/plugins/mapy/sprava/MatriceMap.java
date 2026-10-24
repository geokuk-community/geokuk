package cz.geokuk.plugins.mapy.sprava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Typ;

/**
 * Matice stažení: řádek je základní mapa z katalogu (klíč = cesta mapy na serveru bez přípony), sloupce jsou mapa, trasy, vrstevnice a stínování. Každá buňka
 * má stav a velikost; řádek existuje i bez stažené mapy. Bez Swingu, použije ho Správa map i průvodce nastavením.
 */
public final class MatriceMap {

	private MatriceMap() {}

	public enum Cast {
		MAPA, TRASY, VRSTEVNICE, STINOVANI;

		static Cast pro(final Typ typ) {
			switch (typ) {
			case TRASY:
				return TRASY;
			case VRSTEVNICE:
				return VRSTEVNICE;
			case DEM:
			default:
				return STINOVANI;
			}
		}

		Typ typ() {
			switch (this) {
			case TRASY:
				return Typ.TRASY;
			case VRSTEVNICE:
				return Typ.VRSTEVNICE;
			case STINOVANI:
				return Typ.DEM;
			case MAPA:
			default:
				return null;
			}
		}
	}

	public enum StavBunky {
		/** Pro tuto mapu část neexistuje. */
		NENI,
		KE_STAZENI,
		STAZENO,
		/** Na serveru je novější verze. */
		NOVEJSI,
		STAHUJE,
		CHYBA
	}

	/** Jedna buňka matice; neměnná. */
	public static final class Bunka {
		private final Cast cast;
		private final StavBunky stav;
		private final String id;
		private final long velikost;
		private final long stazeno;
		private final String chyba;
		private final java.time.LocalDate datumDat;
		private final boolean overuje;
		private final int regionuCelkem;
		private final int regionuHotovo;

		Bunka(final Cast cast, final StavBunky stav, final String id, final long velikost, final long stazeno, final String chyba, final java.time.LocalDate datumDat) {
			this(cast, stav, id, velikost, stazeno, chyba, datumDat, false);
		}

		Bunka(final Cast cast, final StavBunky stav, final String id, final long velikost, final long stazeno, final String chyba, final java.time.LocalDate datumDat,
				final boolean overuje) {
			this(cast, stav, id, velikost, stazeno, chyba, datumDat, overuje, 0, 0);
		}

		Bunka(final Cast cast, final StavBunky stav, final String id, final long velikost, final long stazeno, final String chyba, final java.time.LocalDate datumDat,
				final boolean overuje, final int regionuCelkem, final int regionuHotovo) {
			this.regionuCelkem = regionuCelkem;
			this.regionuHotovo = regionuHotovo;
			this.cast = cast;
			this.stav = stav;
			this.id = id;
			this.velikost = velikost;
			this.stazeno = stazeno;
			this.chyba = chyba;
			this.datumDat = datumDat;
			this.overuje = overuje;
		}

		/** Stažené se ověřuje (stav STAHUJE); {@link #getStazeno()} je pak ověřená část z {@link #getVelikost()}. */
		public boolean isOveruje() {
			return overuje;
		}

		/** Souhrn vrstev regionů celé země (ne vlastní vrstva řádku). */
		public boolean isSouhrn() {
			return regionuCelkem > 0;
		}

		/** U souhrnu počet regionů, které mají vrstvu tohoto typu. */
		public int getRegionuCelkem() {
			return regionuCelkem;
		}

		/** U souhrnu počet regionů se staženou vrstvou. */
		public int getRegionuHotovo() {
			return regionuHotovo;
		}

		public Cast getCast() {
			return cast;
		}

		public StavBunky getStav() {
			return stav;
		}

		/** Id mapy nebo vrstvy z katalogu pro akce (stáhnout, smazat); null u stavu NENI. */
		public String getId() {
			return id;
		}

		/** Velikost podle katalogu, -1 když není známá. */
		public long getVelikost() {
			return velikost;
		}

		/** Stažených bajtů, nejvýš velikost; u stavu STAZENO celá velikost. */
		public long getStazeno() {
			return stazeno;
		}

		/** Chyba pro uživatele u stavu CHYBA; u STAHUJE text čekání na další pokus po přerušení, jinak null. */
		public String getChyba() {
			return chyba;
		}

		/** Datum dat staženého souboru nebo vrstvy z katalogu, null když není známé. */
		public java.time.LocalDate getDatumDat() {
			return datumDat;
		}

		/** Je co mazat: stažené, nebo rozpracované stahování. */
		public boolean jeCoMazat() {
			return jeStazeno() || (stav == StavBunky.KE_STAZENI || stav == StavBunky.CHYBA) && stazeno > 0;
		}

		/** Je co stahovat nebo aktualizovat. */
		public boolean jeKeStazeni() {
			return stav == StavBunky.KE_STAZENI || stav == StavBunky.NOVEJSI || stav == StavBunky.CHYBA;
		}

		/** Je co mazat. */
		public boolean jeStazeno() {
			return stav == StavBunky.STAZENO || stav == StavBunky.NOVEJSI;
		}
	}

	/** Řádek matice: základní mapa a čtyři buňky. */
	public static final class Radek {
		private final KatalogovaMapa mapa;
		private final Map<Cast, Bunka> bunky;
		private final Map<Cast, Bunka> souhrnRegionu;

		Radek(final KatalogovaMapa mapa, final Map<Cast, Bunka> bunky) {
			this(mapa, bunky, Collections.emptyMap());
		}

		Radek(final KatalogovaMapa mapa, final Map<Cast, Bunka> bunky, final Map<Cast, Bunka> souhrnRegionu) {
			this.mapa = mapa;
			this.bunky = Collections.unmodifiableMap(bunky);
			this.souhrnRegionu = souhrnRegionu;
		}

		public KatalogovaMapa getMapa() {
			return mapa;
		}

		/** Klíč řádku, cesta mapy na serveru bez přípony. */
		public String getKlic() {
			return mapa.getId();
		}

		public Bunka getBunka(final Cast cast) {
			return bunky.get(cast);
		}

		/**
		 * Buňka pro tlačítka řádku: u celé země bez vlastní vrstvy daného typu souhrn vrstev jejích regionů (velikost je součet), jinak vlastní buňka. Souhrn se
		 * nikde jinde nepočítá, jako vlastní buňka celku se nepočítá.
		 */
		public Bunka getBunkaNabidky(final Cast cast) {
			final Bunka souhrn = souhrnRegionu.get(cast);
			return souhrn != null ? souhrn : bunky.get(cast);
		}

		/** Jedna věc z řádku se právě stahuje. */
		public boolean bezi() {
			return bunky.values().stream().anyMatch(b -> b.getStav() == StavBunky.STAHUJE);
		}

		/** Na disku je mapa nebo některá její vrstva. */
		public boolean mameCokoli() {
			return bunky.values().stream().anyMatch(Bunka::jeStazeno);
		}

		/** Na disku je cokoli z mapy a jejích vrstev, i rozpracované, nebo se něco stahuje. */
		public boolean maCokoli() {
			return bunky.values().stream().anyMatch(b -> b.jeCoMazat() || b.getStav() == StavBunky.STAHUJE);
		}
	}

	/** Matice pro všechny mapy katalogu v jeho pořadí; vrstvy a jejich stavy mohou být null (jen sloupec Mapa). */
	public static List<Radek> sestav(final KatalogMap katalog, final Function<KatalogovaMapa, StavMapy> stavyMap, final KatalogVrstev vrstvy,
			final Function<KatalogovaVrstva, StavVrstvy> stavyVrstev) {
		final List<Radek> radky = new ArrayList<>();
		for (final KatalogovaMapa m : katalog.getMapy()) {
			final Map<Cast, Bunka> bunky = new EnumMap<>(Cast.class);
			bunky.put(Cast.MAPA, bunkaMapy(m, stavyMap.apply(m)));
			for (final Cast c : Cast.values()) {
				if (c == Cast.MAPA) {
					continue;
				}
				final KatalogovaVrstva v = vrstvy == null ? null : vrstvy.vrstva(m.getId(), c.typ()).orElse(null);
				bunky.put(c, v == null || stavyVrstev == null ? new Bunka(c, StavBunky.NENI, null, -1, 0, null, null) : bunkaVrstvy(c, v, stavyVrstev.apply(v)));
			}
			radky.add(new Radek(m, bunky, souhrnRegionu(katalog, m, bunky, vrstvy, stavyVrstev)));
		}
		return radky;
	}

	/** Celá země bez vlastní vrstvy daného typu: jedna buňka za vrstvy všech jejích regionů, jen pro tlačítka řádku. */
	private static Map<Cast, Bunka> souhrnRegionu(final KatalogMap katalog, final KatalogovaMapa m, final Map<Cast, Bunka> bunky, final KatalogVrstev vrstvy,
			final Function<KatalogovaVrstva, StavVrstvy> stavyVrstev) {
		final List<KatalogovaMapa> regiony = vrstvy == null || stavyVrstev == null ? Collections.emptyList() : katalog.regiony(m);
		final Map<Cast, Bunka> souhrn = new EnumMap<>(Cast.class);
		final Optional<KatalogovaMapa> celek = vrstvy == null || stavyVrstev == null ? Optional.empty() : katalog.celek(m);
		for (final Cast c : Cast.values()) {
			if (c == Cast.MAPA) {
				continue;
			}
			// Vlastní vrstvu regionu pokrývá stažená vrstva celé země.
			if (bunky.get(c).getStav() == StavBunky.KE_STAZENI && celek.isPresent()) {
				final KatalogovaVrstva vc = vrstvy.vrstva(celek.get().getId(), c.typ()).orElse(null);
				if (vc != null && bunkaVrstvy(c, vc, stavyVrstev.apply(vc)).getStav() == StavBunky.STAZENO) {
					final Bunka vlastni = bunky.get(c);
					souhrn.put(c, new Bunka(c, StavBunky.STAZENO, null, vlastni.getVelikost(), vlastni.getVelikost(), null, vc.getDatumDat()));
				}
				continue;
			}
			if (bunky.get(c).getStav() != StavBunky.NENI) {
				continue;
			}
			final List<Bunka> b = new ArrayList<>();
			for (final KatalogovaMapa r : regiony) {
				final KatalogovaVrstva v = vrstvy.vrstva(r.getId(), c.typ()).orElse(null);
				if (v != null) {
					b.add(bunkaVrstvy(c, v, stavyVrstev.apply(v)));
				}
			}
			if (!b.isEmpty()) {
				souhrn.put(c, souhrn(c, b));
			}
		}
		return souhrn;
	}

	private static Bunka souhrn(final Cast cast, final List<Bunka> b) {
		StavBunky stav = StavBunky.STAZENO;
		for (final StavBunky s : new StavBunky[] { StavBunky.STAHUJE, StavBunky.CHYBA, StavBunky.KE_STAZENI, StavBunky.NOVEJSI }) {
			if (b.stream().anyMatch(x -> x.getStav() == s)) {
				stav = s;
				break;
			}
		}
		final StavBunky vysledek = stav;
		final long velikost = b.stream().filter(x -> vysledek != StavBunky.NOVEJSI || x.getStav() == StavBunky.NOVEJSI).mapToLong(x -> Math.max(0, x.getVelikost())).sum();
		final long stazeno = b.stream().filter(x -> vysledek != StavBunky.NOVEJSI || x.getStav() == StavBunky.NOVEJSI).mapToLong(x -> x.getStav() == StavBunky.STAZENO ? Math.max(0, x.getVelikost()) : Math.max(0, x.getStazeno())).sum();
		final Bunka prvni = b.stream().filter(x -> x.getStav() == vysledek).findFirst().get();
		final int hotovo = (int) b.stream().filter(x -> x.getStav() == StavBunky.STAZENO).count();
		return new Bunka(cast, vysledek, null, velikost, stazeno, prvni.getChyba(), null, prvni.isOveruje(), b.size(), hotovo);
	}

	static Bunka bunkaMapy(final KatalogovaMapa m, final StavMapy s) {
		final long velikost = s.getCelkem() > 0 ? s.getCelkem() : m.getPribliznaVelikost();
		final StavBunky stav;
		if (s.isBezi()) {
			stav = StavBunky.STAHUJE;
		} else if (s.getFaze() == StavMapy.Faze.CHYBA || s.getMistni() == StavMapy.Mistni.POSKOZENA) {
			stav = StavBunky.CHYBA;
		} else if (s.getMistni() == StavMapy.Mistni.MAME) {
			stav = s.getServer() == StavMapy.Server.NOVEJSI ? StavBunky.NOVEJSI : StavBunky.STAZENO;
		} else {
			stav = StavBunky.KE_STAZENI;
		}
		final long stazeno = stav == StavBunky.STAZENO ? velikost : Math.min(s.getStazeno(), Math.max(velikost, 0));
		final java.time.LocalDate datum = s.getDatumDatMistni() == null ? null : s.getDatumDatMistni().atZone(java.time.ZoneOffset.UTC).toLocalDate();
		return new Bunka(Cast.MAPA, stav, m.getId(), velikost, stazeno, stav == StavBunky.CHYBA || stav == StavBunky.STAHUJE ? s.getChyba() : null, datum, s.getFaze() == StavMapy.Faze.OVERUJE);
	}

	static Bunka bunkaVrstvy(final Cast cast, final KatalogovaVrstva v, final StavVrstvy s) {
		final StavBunky stav;
		if (s.isBezi()) {
			stav = StavBunky.STAHUJE;
		} else if (s.getFaze() == StavMapy.Faze.CHYBA) {
			stav = StavBunky.CHYBA;
		} else if (s.getMistni() == StavVrstvy.Mistni.MAME) {
			stav = StavBunky.STAZENO;
		} else if (s.getMistni() == StavVrstvy.Mistni.JINA_VERZE) {
			stav = StavBunky.NOVEJSI;
		} else {
			stav = StavBunky.KE_STAZENI;
		}
		final long stazeno = stav == StavBunky.STAZENO ? v.getVelikost() : Math.min(s.getStazeno(), v.getVelikost());
		return new Bunka(cast, stav, v.getId(), v.getVelikost(), stazeno, stav == StavBunky.CHYBA || stav == StavBunky.STAHUJE ? s.getChyba() : null, v.getDatumDat(), s.getFaze() == StavMapy.Faze.OVERUJE);
	}
}
