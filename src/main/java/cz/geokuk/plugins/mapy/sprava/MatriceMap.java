package cz.geokuk.plugins.mapy.sprava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

		Bunka(final Cast cast, final StavBunky stav, final String id, final long velikost, final long stazeno, final String chyba, final java.time.LocalDate datumDat) {
			this(cast, stav, id, velikost, stazeno, chyba, datumDat, false);
		}

		Bunka(final Cast cast, final StavBunky stav, final String id, final long velikost, final long stazeno, final String chyba, final java.time.LocalDate datumDat,
				final boolean overuje) {
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

		Radek(final KatalogovaMapa mapa, final Map<Cast, Bunka> bunky) {
			this.mapa = mapa;
			this.bunky = Collections.unmodifiableMap(bunky);
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
			radky.add(new Radek(m, bunky));
		}
		return radky;
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
