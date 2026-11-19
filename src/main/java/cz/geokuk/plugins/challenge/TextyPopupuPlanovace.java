package cz.geokuk.plugins.challenge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Texty popupu plánovače challenge: shrnutí, řádky chybějících sektorů a upozornění na nejisté sektory. */
final class TextyPopupuPlanovace {

	private TextyPopupuPlanovace() {}

	/** „1 nález“, „2 nálezy“, „5 nálezů“. */
	static String nalezu(final int n) {
		return n + " " + tvar(n, "nález", "nálezy", "nálezů");
	}

	/** „v 1 políčku“, „v 77 políčkách“. */
	static String vPolickach(final int n) {
		return "v " + n + " " + (n == 1 ? "políčku" : "políčkách");
	}

	/** Tooltip odznaku u matice D/T s násobností: „Chybí 186 nálezů v 77 políčkách z 81 – klik ukáže podrobnosti“. */
	static String tipOdznakuNalezu(final VysledekPolicek p) {
		return "Chybí " + nalezu(p.chybiNalezu()) + " " + vPolickach(p.pocetChybejicich()) + " z " + p.celkem() + " – klik ukáže podrobnosti";
	}

	/** „1 kandidát“, „2 kandidáti“, „5 kandidátů“. */
	static String kandidatu(final int n) {
		return n + " " + tvar(n, "kandidát", "kandidáti", "kandidátů");
	}

	/** „213°  5 kandidátů  nejbližší 2,8 km od středu“; bez kandidátů s pomlčkou místo vzdálenosti. */
	static String radekSektoru(final int sektor, final VysledekSektoru.Kandidati k) {
		final int pocet = k == null ? 0 : k.pocet();
		final String nejblizsi = pocet == 0 ? "–" : "nejbližší " + StitkyPruhu.vzdalenost(k.nejblizsiVzdalenost()) + " od středu";
		return sektor + "°  " + kandidatu(pocet) + "  " + nejblizsi;
	}

	static String nadpis(final Challenge c) {
		return c.nazev();
	}

	static String souhrn(final VysledekSektoru v) {
		final int splneno = v.pocetObsazenych();
		return "Splněno " + splneno + " z 360 (" + procenta(v) + "), chybí " + (360 - splneno);
	}

	static String procenta(final VysledekSektoru v) {
		return Math.round(v.pocetObsazenych() * 100.0 / 360) + " %";
	}

	static String souhrn(final VysledekPolicek p) {
		if (p.cil() > 1) {
			return "Splněno " + p.splnenoNalezu() + " z " + p.celkemNalezu() + " nálezů (" + procenta(p) + "), chybí " + nalezu(p.chybiNalezu()) + " " + vPolickach(p.pocetChybejicich());
		}
		return "Splněno " + (p.celkem() - p.pocetChybejicich()) + " z " + p.celkem() + " (" + procenta(p) + "), chybí " + p.pocetChybejicich();
	}

	static String procenta(final VysledekPolicek p) {
		return Math.round(p.splnenoNalezu() * 100.0 / Math.max(1, p.celkemNalezu())) + " %";
	}

	static String nadpisSeznamu(final Challenge.Typ typ) {
		switch (typ) {
		case DT_MATICE:
			return "Chybějící políčka D/T s kandidáty (po řádcích):";
		case KALENDAR:
			return "Chybějící dny:";
		default:
			return "Chybějící sektory s kandidáty (od severu po směru hodin):";
		}
	}

	/** Když strop štítků na mapě zkrátil kandidáty ve výřezu: „Štítky na mapě: 200 nejbližších z 1 234“; jinak mezera. */
	static String omezeniStitku(final int veVyrezu) {
		if (veVyrezu <= TvarPlanovace.NEJVIC_ZNACEK) {
			return " ";
		}
		return String.format(Locale.ROOT, "Štítky na mapě: %d nejbližších z %,d", TvarPlanovace.NEJVIC_ZNACEK, veVyrezu).replace(',', '\u00a0');
	}

	/** Vysvětlení kružnice na mapě: leží na ní nejvzdálenější z nejbližších kandidátů sektorů v seznamu. */
	static String kruznice(final double polomer) {
		return "Kružnice " + StitkyPruhu.vzdalenost(polomer) + ": uvnitř leží nejbližší kandidáti všech sektorů v seznamu.";
	}

	/** Dovětek o dosahu spočítaných kandidátů D/T; mezera, když výřez dosah nepřesahuje (NaN). */
	static String dosahKandidatu(final double metru) {
		return Double.isNaN(metru) ? " " : TextyKresleni.dosahKandidatu(metru);
	}

	/** Dovětek u vybraného sektoru 360°, když na mapě není žádný jeho kandidát. */
	static final String MIMO_VYREZ_SEKTORU = "mimo výřez";

	/** Dovětek u vybraného políčka D/T, když na mapě není žádný jeho kandidát; skok Ctrl+Shift+šipka hledá jen mezi kandidáty vybraného políčka. */
	static final String MIMO_VYREZ = "mimo výřez – Ctrl+Shift+šipka ukáže nejbližšího kandidáta";

	/** Dovětek u vybraného políčka D/T mimo výřez: kam skočit; bez nejbližšího kandidáta obecný text. */
	static String mimoVyrez(final SkokVeSmeru.SmerAVzdalenost nejblizsi) {
		if (nejblizsi == null) {
			return MIMO_VYREZ;
		}
		return "mimo výřez – nejbližší kandidát je " + StitkyPruhu.vzdalenost(nejblizsi.vzdalenost()) + " od středu mapy, Ctrl+Shift+" + nejblizsi.smer().sipka() + " na něj skočí";
	}

	static String pravidla(final Challenge.ZdrojPravidel z) {
		return z == Challenge.ZdrojPravidel.STATOR ? "Stator" : "Project-GC";
	}

	static String stred(final Challenge.Stred s) {
		switch (s.druh()) {
		case DOMOV:
			return "domov";
		case LISTING:
			return "keš " + s.kodListingu();
		default:
			return FormularDefinice.formatSouradnic(s.lat(), s.lon());
		}
	}

	/** Střed „domov“ se souřadnicemi; když domov není nastavený (výchozí bod programu), řekne to. */
	static String stredDomov(final cz.geokuk.core.coordinates.Wgs domov) {
		if (domov == null || cz.geokuk.core.coord.VyrezModel.DEFAULTNI_DOMACI_SOURADNICE.equals(domov)) {
			return "domov není nastavený";
		}
		return "domov " + domov;
	}

	static final String NASOBNOST = "Každé políčko splnit:";

	/** Tooltip šipky ◀ ▶: na kolik se násobnost změní. */
	static String nasobnostTip(final int n) {
		return "Každé políčko splnit " + n + "× – matice se přepočítá";
	}

	/** Důvod, proč se výsledek nepočítá, zalomený na danou šířku; u chybějící středové keše i co s tím. */
	static String chyba(final String duvod, final int sirka) {
		final String d = duvod == null || duvod.isEmpty() ? "Výsledek nejde spočítat." : duvod;
		String text = d;
		if (d.contains("není v načtených datech")) {
			text = d + " Načtěte tuto keš, nebo v Nastavení plánovače… vytvořte kopii se středem Domov či Souřadnice.";
		} else if (d.equals(ResolverDefinice.DOMOV_NENASTAVEN)) {
			text = d + " Klikněte na mapu na místo domova a zvolte Soubor > Jsem doma…";
		}
		return "<html><body style='width: " + sirka + "px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>";
	}

	/** U kolika sektorů leží nalezená keš u hranice se sousedním sektorem; prázdný text, když u žádného. */
	static String nejiste(final VysledekSektoru v) {
		int n = 0;
		for (int s = 0; s < 360; s++) {
			if (v.jeNejisty(s)) {
				n++;
			}
		}
		if (n == 0) {
			return "";
		}
		return "U " + n + " " + tvar(n, "sektoru", "sektorů", "sektorů") + " je nalezená keš těsně u hranice, takže není jisté, do kterého sektoru patří. Na mapě to značí šedý čárkovaný úsek prstence a u keše štítek ≈.";
	}

	/** Chybějící sektory po kružnici od 0°, ve stejném pořadí jako ◀ ▶. */
	static List<Integer> chybejiciPoKruznici(final VysledekSektoru v) {
		final List<Integer> s = new ArrayList<>(v.chybejici());
		s.sort(Comparator.naturalOrder());
		return s;
	}

	/** „+ 140 sektorů bez kandidátů“ pod seznamem; prázdný text, když všechny chybějící mají kandidáty. */
	static String bezKandidatu(final Challenge.Typ typ, final int n) {
		if (n == 0) {
			return "";
		}
		final String co = typ == Challenge.Typ.DT_MATICE ? tvar(n, "políčko", "políčka", "políček") : tvar(n, "sektor", "sektory", "sektorů");
		return "+ " + n + " " + co + " bez kandidátů";
	}

	/** Tooltip počtu bez kandidátů. */
	static String tipBezKandidatu(final Challenge.Typ typ) {
		final String co = typ == Challenge.Typ.DT_MATICE ? "políčka" : "sektory";
		return "Chybějící " + co + ", které žádná nenalezená keš na mapě nedoplní. Seznam i ◀ ▶ je přeskakují.";
	}

	/** Řádek nad maticí D/T: vybrané políčko s počtem kandidátů, nebo výzva ke kliknutí. */
	static String vybranePolicko(final String stitek, final int kandidatu, final boolean plne, final boolean jenKandidati) {
		return vybranePolicko(stitek, kandidatu, plne, jenKandidati, 0, 1);
	}

	/** Totéž s násobností: u cíle nad 1 i „1 z 3 nálezů“. */
	static String vybranePolicko(final String stitek, final int kandidatu, final boolean plne, final boolean jenKandidati, final int nalezu, final int cil) {
		return vybranePolicko(stitek, kandidatu, plne, jenKandidati, nalezu, cil, -1);
	}

	/** Totéž s počtem kandidátů ve výřezu mapy ({@code vOkne} záporné = neznámý). */
	static String vybranePolicko(final String stitek, final int kandidatu, final boolean plne, final boolean jenKandidati, final int nalezu, final int cil, final int vOkne) {
		if (stitek == null) {
			return "Obtížnost × terén – klikněte na políčko s číslem, na mapě se zvýrazní jeho keše:";
		}
		final String pocet = vOkne >= 0 ? kandidatuVOkne(vOkne) + (plne ? ", v seznamu " + plnyPocet(kandidatu) : "") : plne ? plnyPocet(kandidatu) : kandidatu(kandidatu);
		final String nalezy = cil > 1 ? Math.min(nalezu, cil) + " z " + cil + " nálezů, " : "";
		return "Vybrané políčko: " + stitek + " – " + nalezy + pocet;
	}

	/** Počet kandidátů ve výřezu mapy: „132 kandidátů v okně“, bez nich „žádný kandidát v okně“. */
	static String kandidatuVOkne(final int n) {
		return n == 0 ? "žádný kandidát v okně" : kandidatu(n) + " v okně";
	}

	/** Plné políčko: výpočet ponechává jen nejbližší kandidáty ke středu mapy, může jich být víc. */
	static String plnyPocet(final int n) {
		return n + " nejbližších ke středu mapy";
	}

	/** Legenda pod maticí D/T. */
	static final String LEGENDA_MATICE = "✓ splněno · číslo = počet kandidátů · – chybí, bez kandidátů";

	/** Legenda matice, když čísla ukazují kandidáty ve výřezu mapy. */
	static final String LEGENDA_MATICE_V_OKNE = "✓ splněno · číslo = kandidátů v okně · – chybí, bez kandidátů";

	/** Legenda matice s násobností: políčko ukazuje nálezy z cíle, tečka znamená kandidáty. */
	static String legendaMatice(final int cil) {
		return "✓ splněno " + cil + "× · 1/" + cil + " = nálezy z cíle · tečka = má kandidáty";
	}

	/** Nápověda pod seznamem chybějících. */
	static String napovedaSeznamu(final Challenge.Typ typ) {
		return "Shift+←/→ předchozí / další · Ctrl+Shift+šipka nejbližší kandidát · F4 jen kandidáti";
	}

	/** Tooltip řádku seznamu: klik vybere, druhý klik na vybraný výběr zruší. */
	static String tipRadku(final Challenge.Typ typ, final boolean vybrany) {
		final String co = typ == Challenge.Typ.DT_MATICE ? "políčko" : "sektor";
		return vybrany ? "Klik: zrušit výběr" : "Klik: vybrat " + co;
	}

	private static String tvar(final int n, final String jeden, final String dvaAzCtyri, final String jinak) {
		if (n == 1) {
			return jeden;
		}
		return n >= 2 && n <= 4 ? dvaAzCtyri : jinak;
	}
}
