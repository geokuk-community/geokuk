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

	/** Volba typu na liště: zapnuto, vypnuto, nebo částečně (část položek typu je odškrtnutá). */
	public enum VolbaTypu {
		ZAPNUTO, CASTECNE, VYPNUTO
	}

	private final Set<Typ> vypnuteTypy = EnumSet.noneOf(Typ.class);

	/** Přepínač typu; vypnutý typ si zachovává výběr svých položek. */
	public boolean isTypZapnut(final Typ typ) {
		return !vypnuteTypy.contains(typ);
	}

	public boolean isEfektivni(final Polozka p) {
		return p.nacist && isTypZapnut(p.typ);
	}

	public VolbaTypu getVolbaTypu(final Typ typ) {
		if (!isTypZapnut(typ)) {
			return VolbaTypu.VYPNUTO;
		}
		int zapnute = 0;
		final List<Polozka> polozkyTypu = getPolozky(typ);
		for (final Polozka p : polozkyTypu) {
			if (p.nacist) {
				zapnute++;
			}
		}
		return zapnute == 0 ? VolbaTypu.VYPNUTO : zapnute == polozkyTypu.size() ? VolbaTypu.ZAPNUTO : VolbaTypu.CASTECNE;
	}

	/** Stav přepínače typu: nejdůležitější stav jeho načítaných položek. */
	public Stav getStavTypu(final Typ typ) {
		Stav stav = Stav.VYPNUTO;
		for (final Polozka p : getPolozky(typ)) {
			if (!isEfektivni(p)) {
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

	/** Volba jedné položky v tabulce; výběr ostatních položek ani typu se nemění. */
	public void setPolozkaZapnuta(final Polozka p, final boolean nacist) {
		p.nacist = nacist;
		prepocitej(p);
		zmeneno();
	}

	/** Klik na zaškrtávátko typu na liště: vypnutý typ vrátí dřívější výběr položek, vypnutí výběr zachová. */
	public void klikTyp(final Typ typ) {
		final VolbaTypu volba = getVolbaTypu(typ);
		if (volba == VolbaTypu.VYPNUTO && isTypZapnut(typ)) {
			// Typ je zapnutý, ale nemá žádnou zapnutou položku.
			nastavPolozkyTypu(typ, true);
		} else if (volba == VolbaTypu.VYPNUTO) {
			vypnuteTypy.remove(typ);
			boolean zadna = true;
			for (final Polozka p : getPolozky(typ)) {
				zadna &= !p.nacist;
			}
			if (zadna) {
				nastavPolozkyTypu(typ, true);
			}
		} else {
			vypnuteTypy.add(typ);
		}
		prepocitejTyp(typ);
		zmeneno();
	}

	/** Výslovné „Vše zapnout / Vše vypnout“ pro typ: přepíše výběr jeho položek. */
	public void setTypZapnut(final Typ typ, final boolean nacist) {
		vypnuteTypy.remove(typ);
		nastavPolozkyTypu(typ, nacist);
		prepocitejTyp(typ);
		zmeneno();
	}

	/** Výslovné „Vše zapnout / Vše vypnout“ pro všechny zdroje. */
	public void setVse(final boolean nacist) {
		for (final Typ typ : Typ.values()) {
			vypnuteTypy.remove(typ);
			nastavPolozkyTypu(typ, nacist);
			prepocitejTyp(typ);
		}
		zmeneno();
	}

	/** Jen pro ukázku: nastaví stav načítaných položek typu, vypnutí vypne typ a výběr položek zachová. */
	public void setStavTypu(final Typ typ, final Stav stav) {
		if (stav == Stav.VYPNUTO) {
			vypnuteTypy.add(typ);
			prepocitejTyp(typ);
		} else {
			vypnuteTypy.remove(typ);
			for (final Polozka p : getPolozky(typ)) {
				p.nacist = true;
				p.stav = stav;
				p.postup = stav == Stav.NACITA_SE ? 40 : 0;
			}
		}
		zmeneno();
	}

	private void nastavPolozkyTypu(final Typ typ, final boolean nacist) {
		for (final Polozka p : getPolozky(typ)) {
			p.nacist = nacist;
		}
	}

	private void prepocitejTyp(final Typ typ) {
		for (final Polozka p : getPolozky(typ)) {
			prepocitej(p);
		}
	}

	private void prepocitej(final Polozka p) {
		if (!isEfektivni(p)) {
			p.stav = Stav.VYPNUTO;
			p.postup = 0;
		} else if (p.stav == Stav.VYPNUTO) {
			p.stav = Stav.NACITA_SE;
			p.postup = 0;
		}
	}

	/** Posune simulované načítání; vypnuté položky se přeskočí a po dokončení se stav změní na načteno. */
	public void posunNacitani(final int krok) {
		boolean zmena = false;
		for (final Polozka p : polozky) {
			if (isEfektivni(p) && p.stav == Stav.NACITA_SE) {
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
			if (isEfektivni(p) && p.stav == Stav.NACITA_SE) {
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
