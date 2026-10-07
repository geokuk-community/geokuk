package cz.geokuk.core.program.prototyp;

import java.util.*;

/** Zjednodušený model zdrojů kešoidů pro prototyp přepínačů ve stavovém řádku. */
public class ZdrojeModel {

	public enum Typ {
		GPX("GPX"), GEOGET("GeoGet"), GSAK("GSAK"), OPENSAK("OpenSAK");

		private final String nazev;

		Typ(final String nazev) {
			this.nazev = nazev;
		}

		public String getNazev() {
			return nazev;
		}
	}

	public enum Stav {
		NACTENO("Načteno"), VYPNUTO("Vypnuto"), NACITA_SE("Načítá se…"), CEKA_NA_ZAPIS("Čeká na zápis"), ZAMCENO("Zamčeno jiným programem"), CHYBA("Chyba čtení");

		private final String text;

		Stav(final String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}

	public enum Rezim {
		VSE("Zapnuto"), JEN_GPX("Jen GPX"), NIC("Vypnuto");

		private final String text;

		Rezim(final String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}

	public static class Polozka {
		public final Typ typ;
		public final String nazev;
		public final long velikost;
		public final int wpCelkem;
		public int wpBrano;
		public boolean nacist;
		public Stav stav;
		/** Průběh načítání v procentech, jen pro stav NACITA_SE. */
		public int postup;

		Polozka(final Typ typ, final String nazev, final long velikost, final int wpBrano, final int wpCelkem, final boolean nacist, final Stav stav) {
			this.typ = typ;
			this.nazev = nazev;
			this.velikost = velikost;
			this.wpBrano = wpBrano;
			this.wpCelkem = wpCelkem;
			this.nacist = nacist;
			this.stav = stav;
		}

		public int getPocetDuplicit() {
			return wpCelkem - wpBrano;
		}
	}

	private final List<Polozka> polozky = new ArrayList<>();
	private final List<Runnable> posluchaci = new ArrayList<>();

	public List<Polozka> getPolozky() {
		return Collections.unmodifiableList(polozky);
	}

	public List<Polozka> getPolozky(final Typ typ) {
		final List<Polozka> vysledek = new ArrayList<>();
		for (final Polozka p : polozky) {
			if (p.typ == typ) {
				vysledek.add(p);
			}
		}
		return vysledek;
	}

	public void addPosluchac(final Runnable posluchac) {
		posluchaci.add(posluchac);
	}

	private void zmeneno() {
		for (final Runnable p : posluchaci) {
			p.run();
		}
	}

	public boolean isTypZapnut(final Typ typ) {
		for (final Polozka p : getPolozky(typ)) {
			if (p.nacist) {
				return true;
			}
		}
		return false;
	}

	/** Stav přepínače typu: nejdůležitější stav jeho zapnutých položek. */
	public Stav getStavTypu(final Typ typ) {
		Stav stav = Stav.VYPNUTO;
		for (final Polozka p : getPolozky(typ)) {
			if (!p.nacist) {
				continue;
			}
			if (p.stav == Stav.ZAMCENO || p.stav == Stav.CHYBA) {
				return p.stav;
			}
			if (p.stav == Stav.NACITA_SE || p.stav == Stav.CEKA_NA_ZAPIS) {
				stav = Stav.NACITA_SE;
			} else if (stav == Stav.VYPNUTO) {
				stav = Stav.NACTENO;
			}
		}
		return stav;
	}

	public void setPolozkaZapnuta(final Polozka p, final boolean nacist) {
		nastav(p, nacist);
		zmeneno();
	}

	public void setTypZapnut(final Typ typ, final boolean nacist) {
		for (final Polozka p : getPolozky(typ)) {
			nastav(p, nacist);
		}
		zmeneno();
	}

	public void setRezim(final Rezim rezim) {
		for (final Polozka p : polozky) {
			nastav(p, rezim == Rezim.VSE || rezim == Rezim.JEN_GPX && p.typ == Typ.GPX);
		}
		zmeneno();
	}

	public Rezim getRezim() {
		boolean vse = true;
		boolean jenGpx = true;
		for (final Polozka p : polozky) {
			vse &= p.nacist;
			jenGpx &= p.nacist == (p.typ == Typ.GPX);
		}
		return vse ? Rezim.VSE : jenGpx ? Rezim.JEN_GPX : isNicZapnuto() ? null : Rezim.NIC;
	}

	private boolean isNicZapnuto() {
		for (final Polozka p : polozky) {
			if (p.nacist) {
				return true;
			}
		}
		return false;
	}

	private static void nastav(final Polozka p, final boolean nacist) {
		if (p.nacist == nacist) {
			return;
		}
		p.nacist = nacist;
		if (!nacist) {
			p.stav = Stav.VYPNUTO;
			p.postup = 0;
		} else if (p.stav == Stav.VYPNUTO) {
			p.stav = Stav.NACITA_SE;
			p.postup = 0;
		}
	}

	/** Posune simulované načítání; zrušené (vypnuté) položky se přeskočí a po dokončení se stav změní na načteno. */
	public void posunNacitani(final int krok) {
		boolean zmena = false;
		for (final Polozka p : polozky) {
			if (p.nacist && p.stav == Stav.NACITA_SE) {
				p.postup = Math.min(100, p.postup + krok);
				if (p.postup >= 100) {
					p.stav = Stav.NACTENO;
				}
				zmena = true;
			}
		}
		if (zmena) {
			zmeneno();
		}
	}

	public boolean nacitaSe() {
		for (final Polozka p : polozky) {
			if (p.nacist && p.stav == Stav.NACITA_SE) {
				return true;
			}
		}
		return false;
	}

	/** Ukázková data; počty a velikosti jsou vymyšlené. */
	public static ZdrojeModel ukazka() {
		final ZdrojeModel m = new ZdrojeModel();
		m.polozky.add(new Polozka(Typ.GPX, "pocket-query-praha.gpx", 3_400_000L, 1_240, 1_240, true, Stav.NACTENO));
		m.polozky.add(new Polozka(Typ.GPX, "sumava-2026.gpx", 880_000L, 310, 310, true, Stav.NACTENO));
		m.polozky.add(new Polozka(Typ.GPX, "geocaching.gpx", 41_000_000L, 9_870, 10_420, true, Stav.NACTENO));
		m.polozky.add(new Polozka(Typ.GEOGET, "Česko.db3", 912_000_000L, 38_204, 40_100, true, Stav.NACTENO));
		m.polozky.add(new Polozka(Typ.GEOGET, "Slovensko.db3", 84_000_000L, 4_511, 4_511, true, Stav.NACITA_SE));
		m.polozky.add(new Polozka(Typ.GEOGET, "Cesko-stare.db3", 650_000_000L, 12_000, 12_000, true, Stav.NACITA_SE));
		m.polozky.add(new Polozka(Typ.GSAK, "Domov.db3", 365_000_000L, 21_050, 22_340, true, Stav.ZAMCENO));
		m.polozky.add(new Polozka(Typ.GSAK, "Archiv.db3", 1_420_000_000L, 0, 55_900, false, Stav.VYPNUTO));
		m.polozky.add(new Polozka(Typ.OPENSAK, "opensak.db", 120_000_000L, 6_020, 6_020, true, Stav.NACTENO));
		return m;
	}
}
