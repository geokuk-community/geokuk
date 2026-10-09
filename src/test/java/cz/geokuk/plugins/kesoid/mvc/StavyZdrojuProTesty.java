package cz.geokuk.plugins.kesoid.mvc;

import java.io.File;
import java.util.*;

import cz.geokuk.plugins.kesoid.importek.*;

/** Skládá snímky stavu zdrojů přes veřejný registr, jak je plní načítání. */
final class StavyZdrojuProTesty {

	final RegistrStavuZdroju registr = new RegistrStavuZdroju();
	private final List<File> soubory = new ArrayList<>();
	private final Map<File, TypZdroje> typy = new HashMap<>();
	final Set<File> vypnute = new HashSet<>();
	final Set<TypZdroje> vypnuteTypy = EnumSet.noneOf(TypZdroje.class);

	File pridej(final TypZdroje typ, final String jmeno) {
		final File f = new File("/data/" + typ + "/" + jmeno);
		soubory.add(f);
		typy.put(f, typ);
		return f;
	}

	StavyZdrojuProTesty prepis() {
		registr.prepis(soubory, typy::get, File::getName, f -> !vypnute.contains(f), vypnuteTypy::contains, f -> 1_500_000L);
		return this;
	}

	void prepisZapnuti() {
		registr.prepisZapnuti(f -> !vypnute.contains(f), vypnuteTypy::contains);
	}

	StavZdroju snimek() {
		return registr.getSnimek();
	}

	/** Všechny čtyři typy po jedné až dvou položkách, vše načtené. */
	static StavyZdrojuProTesty vzorek() {
		final StavyZdrojuProTesty s = new StavyZdrojuProTesty();
		final File gpx = s.pridej(TypZdroje.GPX, "praha.gpx");
		final File gg1 = s.pridej(TypZdroje.GEOGET, "Cesko.db3");
		final File gg2 = s.pridej(TypZdroje.GEOGET, "Slovensko.db3");
		final File gsak = s.pridej(TypZdroje.GSAK, "Domov.db3");
		final File os = s.pridej(TypZdroje.OPENSAK, "opensak.db");
		s.prepis();
		s.registr.hotovo(s.registr.getGenerace(), gpx, 1240, 1240);
		s.registr.hotovo(s.registr.getGenerace(), gg1, 40100, 38204);
		s.registr.hotovo(s.registr.getGenerace(), gg2, 4511, 4511);
		s.registr.hotovo(s.registr.getGenerace(), gsak, 22340, 21050);
		s.registr.hotovo(s.registr.getGenerace(), os, 6020, 6020);
		return s;
	}

	/** Zaznamenává volání ovládání. */
	static final class Zaznam implements OvladaniZdroju {
		final List<String> volani = new ArrayList<>();

		@Override
		public void setNacitatTyp(final TypZdroje typ, final boolean nacitat) {
			volani.add("typ " + typ + " " + nacitat);
		}

		@Override
		public void setNacitatVseVTypu(final TypZdroje typ, final boolean nacitat) {
			volani.add("vseVTypu " + typ + " " + nacitat);
		}

		@Override
		public void setNacitatVse(final boolean nacitat) {
			volani.add("vse " + nacitat);
		}

		@Override
		public void setNacitatPolozku(final File soubor, final boolean nacitat) {
			volani.add("polozka " + soubor.getName() + " " + nacitat);
		}

		@Override
		public void setNacitatJenPolozku(final TypZdroje typ, final File soubor) {
			volani.add("jenPolozka " + typ + " " + soubor.getName());
		}

		@Override
		public void setNacitatJenTyp(final TypZdroje typ) {
			volani.add("jenTyp " + typ);
		}
	}
}
