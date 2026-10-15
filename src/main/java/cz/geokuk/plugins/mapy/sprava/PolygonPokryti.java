package cz.geokuk.plugins.mapy.sprava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cz.geokuk.core.coordinates.Wgs;

/**
 * Obrys pokrytí offline mapy ve formátu .poly (osmosis): první řádek je jméno, pak sekce s řádky „zeměpisná délka šířka“ ukončené END, sekce se jménem
 * začínajícím „!“ je díra, celý soubor končí END.
 */
public final class PolygonPokryti {

	private final List<double[]> obrysy;
	private final List<double[]> diry;
	private final double jih, sever, zapad, vychod;

	private PolygonPokryti(final List<double[]> obrysy, final List<double[]> diry) {
		this.obrysy = obrysy;
		this.diry = diry;
		double j = Double.POSITIVE_INFINITY, s = Double.NEGATIVE_INFINITY, z = Double.POSITIVE_INFINITY, v = Double.NEGATIVE_INFINITY;
		for (final double[] kruh : obrysy) {
			for (int i = 0; i < kruh.length; i += 2) {
				z = Math.min(z, kruh[i]);
				v = Math.max(v, kruh[i]);
				j = Math.min(j, kruh[i + 1]);
				s = Math.max(s, kruh[i + 1]);
			}
		}
		jih = j;
		sever = s;
		zapad = z;
		vychod = v;
	}

	/** Obdélník jako polygon, pro mapu, ke které obrys není. */
	public static PolygonPokryti obdelnik(final double jih, final double zapad, final double sever, final double vychod) {
		if (!(jih < sever) || !(zapad < vychod)) {
			throw new IllegalArgumentException("Prázdný obdélník: " + jih + " " + zapad + " " + sever + " " + vychod);
		}
		return new PolygonPokryti(Collections.singletonList(new double[] { zapad, jih, vychod, jih, vychod, sever, zapad, sever }), Collections.emptyList());
	}

	public static PolygonPokryti nacti(final Reader reader) throws IOException {
		final BufferedReader r = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
		final List<double[]> obrysy = new ArrayList<>();
		final List<double[]> diry = new ArrayList<>();
		int cislo = 0;
		String radek = r.readLine();
		cislo++;
		if (radek == null) {
			throw new IOException("Prázdný soubor .poly");
		}
		for (;;) {
			radek = r.readLine();
			cislo++;
			if (radek == null) {
				throw new IOException("Soubor .poly nekončí řádkem END (řádek " + cislo + ")");
			}
			final String jmeno = radek.trim();
			if (jmeno.isEmpty()) {
				continue;
			}
			if ("END".equals(jmeno)) {
				break;
			}
			final List<Double> body = new ArrayList<>();
			for (;;) {
				radek = r.readLine();
				cislo++;
				if (radek == null) {
					throw new IOException("Sekce " + jmeno + " nekončí řádkem END (řádek " + cislo + ")");
				}
				final String t = radek.trim();
				if ("END".equals(t)) {
					break;
				}
				if (t.isEmpty()) {
					continue;
				}
				final String[] casti = t.split("\\s+");
				if (casti.length != 2) {
					throw new IOException("Řádek " + cislo + " souboru .poly nemá dvě souřadnice: " + t);
				}
				final double lon, lat;
				try {
					lon = Double.parseDouble(casti[0]);
					lat = Double.parseDouble(casti[1]);
				} catch (final NumberFormatException e) {
					throw new IOException("Řádek " + cislo + " souboru .poly nemá čísla: " + t, e);
				}
				if (!(Math.abs(lat) <= 90) || !(Math.abs(lon) <= 180)) {
					throw new IOException("Řádek " + cislo + " souboru .poly je mimo rozsah souřadnic: " + t);
				}
				body.add(lon);
				body.add(lat);
			}
			if (body.size() < 6) {
				throw new IOException("Sekce " + jmeno + " souboru .poly má méně než tři body (řádek " + cislo + ")");
			}
			final double[] kruh = new double[body.size()];
			for (int i = 0; i < kruh.length; i++) {
				kruh[i] = body.get(i);
			}
			(jmeno.startsWith("!") ? diry : obrysy).add(kruh);
		}
		if (obrysy.isEmpty()) {
			throw new IOException("Soubor .poly nemá žádný obrys");
		}
		return new PolygonPokryti(obrysy, diry);
	}

	public boolean obsahuje(final Wgs bod) {
		return obsahuje(bod.lat, bod.lon);
	}

	public boolean obsahuje(final double lat, final double lon) {
		if (lat < jih || lat > sever || lon < zapad || lon > vychod) {
			return false;
		}
		boolean uvnitr = false;
		for (final double[] kruh : obrysy) {
			if (vKruhu(kruh, lat, lon)) {
				uvnitr = true;
				break;
			}
		}
		if (!uvnitr) {
			return false;
		}
		for (final double[] kruh : diry) {
			if (vKruhu(kruh, lat, lon)) {
				return false;
			}
		}
		return true;
	}

	/** Paprskový test; kruh nemusí být uzavřený (poslední bod = první). */
	private static boolean vKruhu(final double[] kruh, final double lat, final double lon) {
		boolean uvnitr = false;
		final int n = kruh.length / 2;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			final double xi = kruh[2 * i], yi = kruh[2 * i + 1];
			final double xj = kruh[2 * j], yj = kruh[2 * j + 1];
			if (yi > lat != yj > lat && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
				uvnitr = !uvnitr;
			}
		}
		return uvnitr;
	}

	/** Přibližná plocha obrysů ve čtverečních stupních; jen pro řazení map od nejmenší. */
	public double plocha() {
		double soucet = 0;
		for (final double[] kruh : obrysy) {
			soucet += Math.abs(plochaKruhu(kruh));
		}
		for (final double[] kruh : diry) {
			soucet -= Math.abs(plochaKruhu(kruh));
		}
		return soucet;
	}

	private static double plochaKruhu(final double[] kruh) {
		double a = 0;
		final int n = kruh.length / 2;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			a += (kruh[2 * j] + kruh[2 * i]) * (kruh[2 * j + 1] - kruh[2 * i + 1]);
		}
		return a / 2;
	}

	public List<List<Wgs>> getObrysy() {
		return naWgs(obrysy);
	}

	public List<List<Wgs>> getDiry() {
		return naWgs(diry);
	}

	private static List<List<Wgs>> naWgs(final List<double[]> kruhy) {
		final List<List<Wgs>> vysledek = new ArrayList<>(kruhy.size());
		for (final double[] kruh : kruhy) {
			final List<Wgs> body = new ArrayList<>(kruh.length / 2);
			for (int i = 0; i < kruh.length; i += 2) {
				body.add(new Wgs(kruh[i + 1], kruh[i]));
			}
			vysledek.add(Collections.unmodifiableList(body));
		}
		return Collections.unmodifiableList(vysledek);
	}

	public double getJih() {
		return jih;
	}

	public double getSever() {
		return sever;
	}

	public double getZapad() {
		return zapad;
	}

	public double getVychod() {
		return vychod;
	}
}
