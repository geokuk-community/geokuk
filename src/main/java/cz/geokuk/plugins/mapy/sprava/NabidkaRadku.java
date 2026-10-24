package cz.geokuk.plugins.mapy.sprava;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Rezim;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Bunka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;

/**
 * Dvě pozice tlačítek jednoho řádku země nebo regionu: „Stáhnout mapu“ a „Stáhnout mapu a vrstvy“. Text tlačítka se nemění podle stavu, mění se jen velikost
 * v závorce, která říká, kolik se opravdu stáhne. Bez Swingu.
 */
public final class NabidkaRadku {

	public enum Druh {
		/** Tlačítko s akcí. */
		TLACITKO,
		/** Cíl je splněný, šedý stav se zatržítkem. */
		HOTOVO,
		/** Stahuje se nebo ověřuje; text platí pro obě pozice. */
		PRUBEH,
		/** Tato pozice se pro mapu nenabízí (vrstvy zatím nejsou). */
		NENI
	}

	/** Jedna pozice: co ukázat a co udělat. */
	public static final class Pozice {
		private final Druh druh;
		private final String text;
		private final String tooltip;
		private final long velikost;
		private final Rezim rezim;
		private final boolean povoleno;

		Pozice(final Druh druh, final String text, final String tooltip, final long velikost, final Rezim rezim, final boolean povoleno) {
			this.druh = druh;
			this.text = text;
			this.tooltip = tooltip;
			this.velikost = velikost;
			this.rezim = rezim;
			this.povoleno = povoleno;
		}

		public Druh getDruh() {
			return druh;
		}

		public String getText() {
			return text;
		}

		public String getTooltip() {
			return tooltip;
		}

		/** Kolik se stáhne podle katalogu, 0 když není známo nebo se nic nestahuje. */
		public long getVelikost() {
			return velikost;
		}

		/** Pravidlo stahování pro plán tlačítka; null u tlačítek, která nespouštějí plán vrstev (samotná mapa). */
		public Rezim getRezim() {
			return rezim;
		}

		/** Tlačítko jde stisknout; vypnuté má v tooltipu důvod. */
		public boolean isPovoleno() {
			return povoleno;
		}
	}

	/** Okolnosti mimo řádek: volné místo na disku a jméno jiné stahované mapy. */
	public static final class Okolnosti {
		static final Okolnosti ZADNE = new Okolnosti(-1, null);

		private final long volneMisto;
		private final String bezici;

		public Okolnosti(final long volneMisto, final String bezici) {
			this.volneMisto = volneMisto;
			this.bezici = bezici;
		}
	}

	/** Tooltip vypnutých tlačítek u regionu stažené země. */
	public static final String V_CELE_ZEME_NEJDE = "Pro stažení jednotlivých oblastí nejdřív smažte celou zemi";

	private static final Cast[] VRSTVY = { Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI };

	private final Pozice mapa;
	private final Pozice vrstvy;

	private NabidkaRadku(final Pozice mapa, final Pozice vrstvy) {
		this.mapa = mapa;
		this.vrstvy = vrstvy;
	}

	/** Region, který nemáme zvlášť, protože máme celou jeho zemi: místo tlačítek jen stav. */
	public static NabidkaRadku vCeleZemi() {
		return new NabidkaRadku(new Pozice(Druh.HOTOVO, "✔ v mapě celé země", null, 0, null, false), new Pozice(Druh.NENI, "", null, 0, null, false));
	}

	/** Region celé stažené země: tlačítka ke stažení zůstanou vidět, ale nejdou stisknout. */
	public NabidkaRadku zakazanoVCeleZemi() {
		return new NabidkaRadku(zakazana(mapa), zakazana(vrstvy));
	}

	private static Pozice zakazana(final Pozice p) {
		return p.druh == Druh.TLACITKO ? new Pozice(p.druh, p.text, V_CELE_ZEME_NEJDE, p.velikost, p.rezim, false) : p;
	}

	/** Pozice 1: Stáhnout mapu. */
	public Pozice getMapa() {
		return mapa;
	}

	/** Pozice 2: Stáhnout mapu a vrstvy. */
	public Pozice getVrstvy() {
		return vrstvy;
	}

	public static NabidkaRadku pro(final MatriceMap.Radek radek, final Okolnosti okolnosti) {
		return pro(radek::getBunkaNabidky, okolnosti);
	}

	public static NabidkaRadku pro(final Function<Cast, Bunka> bunky, final Okolnosti okolnosti) {
		final Bunka m = bunky.apply(Cast.MAPA);
		final List<Bunka> vrstvy = new ArrayList<>();
		for (final Cast c : VRSTVY) {
			final Bunka b = bunky.apply(c);
			if (b != null && b.getStav() != StavBunky.NENI) {
				vrstvy.add(b);
			}
		}
		final List<Bunka> vse = new ArrayList<>(vrstvy);
		vse.add(0, m);
		final Bunka bezi = vse.stream().filter(b -> b.getStav() == StavBunky.STAHUJE).findFirst().orElse(null);
		if (bezi != null) {
			final String text = bezi.isOveruje() ? "Ověřuji " + procenta(bezi) : "Stahuje se " + procenta(bezi);
			final Pozice p = new Pozice(Druh.PRUBEH, text, null, 0, null, false);
			return new NabidkaRadku(p, p);
		}
		return new NabidkaRadku(pozice1(m, okolnosti), pozice2(m, vrstvy, okolnosti));
	}

	private static Pozice pozice1(final Bunka m, final Okolnosti ok) {
		switch (m.getStav()) {
		case STAZENO:
			return new Pozice(Druh.HOTOVO, "✔ Mapa stažená", null, 0, null, false);
		case NOVEJSI:
			return tlacitko("Aktualizovat mapu", m.getVelikost(), null, Rezim.AKTUALIZOVAT, ok);
		case CHYBA:
			return tlacitko("Zkusit znovu", zbyva(m), m.getChyba(), Rezim.STAHNOUT, ok);
		case KE_STAZENI:
		default:
			if (m.getStazeno() > 0) {
				return tlacitko("Dokončit stažení", zbyva(m), null, Rezim.STAHNOUT, ok);
			}
			return tlacitko("Stáhnout mapu", m.getVelikost(), null, Rezim.STAHNOUT, ok);
		}
	}

	private static Pozice pozice2(final Bunka m, final List<Bunka> vrstvy, final Okolnosti ok) {
		if (vrstvy.isEmpty()) {
			return new Pozice(Druh.NENI, "Vrstvy zatím nejsou", "Pro tuto mapu GeoKuk vrstvy zatím nenabízí.", 0, null, false);
		}
		final List<Bunka> vse = new ArrayList<>(vrstvy);
		vse.add(0, m);
		final boolean chybi = vse.stream().anyMatch(b -> b.getStav() == StavBunky.KE_STAZENI || b.getStav() == StavBunky.CHYBA);
		final boolean novejsi = vse.stream().anyMatch(b -> b.getStav() == StavBunky.NOVEJSI);
		if (!chybi && !novejsi) {
			return new Pozice(Druh.HOTOVO, "✔ Mapa i vrstvy stažené", null, 0, null, false);
		}
		if (chybi) {
			final long velikost = vse.stream().filter(b -> b.getStav() == StavBunky.KE_STAZENI || b.getStav() == StavBunky.CHYBA).mapToLong(PomocnikVelikosti::zbyva).sum();
			return tlacitko(m.getStav() == StavBunky.STAZENO ? "Stáhnout vrstvy" : "Stáhnout mapu a vrstvy", velikost, seznam(vse, Rezim.STAHNOUT), Rezim.STAHNOUT, ok);
		}
		final long velikost = vse.stream().filter(b -> b.getStav() == StavBunky.NOVEJSI).mapToLong(b -> Math.max(0, b.getVelikost())).sum();
		return tlacitko("Aktualizovat mapu a vrstvy", velikost, seznam(vse, Rezim.AKTUALIZOVAT), Rezim.STAHNOUT_A_AKTUALIZOVAT, ok);
	}

	private static String seznam(final List<Bunka> vse, final Rezim rezim) {
		final List<String> casti = new ArrayList<>();
		for (final Bunka b : vse) {
			final boolean bere = rezim == Rezim.AKTUALIZOVAT ? b.getStav() == StavBunky.NOVEJSI : b.getStav() == StavBunky.KE_STAZENI || b.getStav() == StavBunky.CHYBA;
			if (bere) {
				casti.add(nazev(b.getCast()) + (b.getVelikost() > 0 ? " " + velikost(b.getVelikost()) : ""));
			}
		}
		return String.join(", ", casti) + ".";
	}

	private static String nazev(final Cast c) {
		switch (c) {
		case TRASY:
			return "trasy";
		case VRSTEVNICE:
			return "vrstevnice";
		case STINOVANI:
			return "stínování";
		case MAPA:
		default:
			return "mapa";
		}
	}

	private static Pozice tlacitko(final String text, final long velikost, final String tooltip, final Rezim rezim, final Okolnosti ok) {
		final String popis = velikost > 0 ? text + " (" + velikost(velikost) + ")" : text;
		if (ok.bezici != null) {
			return new Pozice(Druh.TLACITKO, popis, "Až se dokončí stahování mapy " + ok.bezici + ".", velikost, rezim, false);
		}
		if (ok.volneMisto >= 0 && velikost > ok.volneMisto) {
			return new Pozice(Druh.TLACITKO, popis, "Na disku je volných jen " + velikost(ok.volneMisto) + ", potřeba asi " + velikost(velikost) + ".", velikost, rezim, false);
		}
		return new Pozice(Druh.TLACITKO, popis, tooltip, Math.max(0, velikost), rezim, true);
	}

	private static long zbyva(final Bunka b) {
		return PomocnikVelikosti.zbyva(b);
	}

	private static String procenta(final Bunka b) {
		return b.getVelikost() > 0 ? Math.min(100, b.getStazeno() * 100 / b.getVelikost()) + " %" : "";
	}

	/** Velikost bez vlnovky: „606 MB“, „1,2 GB“. */
	static String velikost(final long b) {
		if (b >= 1_000_000_000L) {
			return String.format(new Locale("cs"), "%.1f GB", b / 1e9);
		}
		return String.format(new Locale("cs"), "%d MB", Math.max(1, Math.round(b / 1e6)));
	}

	private static final class PomocnikVelikosti {
		/** Kolik zbývá stáhnout; neznámá velikost je 0. */
		static long zbyva(final Bunka b) {
			return b.getVelikost() > 0 ? Math.max(0, b.getVelikost() - Math.max(0, b.getStazeno())) : 0;
		}
	}
}
