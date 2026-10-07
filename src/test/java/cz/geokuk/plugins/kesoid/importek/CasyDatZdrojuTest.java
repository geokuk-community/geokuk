package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.MyPreferences;

/** Otisk obsahu zdroje a čas jeho dat, včetně uložení do nastavení. */
public class CasyDatZdrojuTest {

	/** Pole, která do výsledku načtení nejdou, takže je otisk obsahu nesleduje. */
	private static final Set<String> MIMO_OTISK = new HashSet<>(Arrays.asList(
			"iInformaceOZdroji", // zdroj, ze kterého waypoint je, ne obsah
			"hintZDatabaze")); // hint načítaný až při zobrazení

	private static long otisk(final GpxWpt w) {
		final KliceZdroje.Sberac s = new KliceZdroje.Sberac();
		s.obsah(w);
		return s.hotovo().otiskObsahu;
	}

	private static GpxWpt plny() {
		final GpxWpt w = new GpxWpt();
		w.name = "GC0001";
		w.desc = "popis";
		w.cmt = "komentář";
		w.sym = "Geocache";
		w.time = "2020-01-01";
		w.type = "Geocache|Traditional Cache";
		w.wgs = new Wgs(50.1, 14.4);
		w.ele = 300;
		w.link.href = "http://a";
		w.link.text = "a";
		w.link.type = "text";
		w.gpxg.found = "2021";
		w.gpxg.czkraj = "Praha";
		w.gpxg.czokres = "Praha";
		w.gpxg.putUserTag("gen", "alela");
		w.groundspeak = new Groundspeak();
		for (final Field f : Groundspeak.class.getDeclaredFields()) {
			if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) {
				try {
					f.set(w.groundspeak, f.getName());
				} catch (final IllegalAccessException e) {
					throw new AssertionError(e);
				}
			}
		}
		return w;
	}

	/** Každé pole, které jde do výsledku, změní otisk; pole přidané do GpxWpt bez otisku tento test shodí. */
	@Test
	public void zmenaKazdehoPoleZmeniOtisk() throws Exception {
		final long puvodni = otisk(plny());
		final List<String> nezmenene = new ArrayList<>();
		final List<String> overene = new ArrayList<>();
		for (final Object[] cesta : pole()) {
			final GpxWpt w = plny();
			final Object vlastnik = ((Field) cesta[0]) == null ? w : ((Field) cesta[0]).get(w);
			final Field f = (Field) cesta[1];
			if (!zmen(vlastnik, f)) {
				continue;
			}
			overene.add(f.getName());
			if (otisk(w) == puvodni) {
				nezmenene.add(f.getDeclaringClass().getSimpleName() + "." + f.getName());
			}
		}
		Assert.assertEquals("pole bez vlivu na otisk", Collections.emptyList(), nezmenene);
		Assert.assertTrue(overene.toString(), overene.size() > 30);
	}

	/** Dvojice (pole v GpxWpt s vnořeným objektem nebo null, pole k změně). */
	private static List<Object[]> pole() throws Exception {
		final List<Object[]> vysledek = new ArrayList<>();
		for (final Field f : GpxWpt.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || MIMO_OTISK.contains(f.getName())) {
				continue;
			}
			if (f.getType() == Groundspeak.class || f.getType() == Gpxg.class || f.getType() == GpxLink.class) {
				for (final Field g : f.getType().getDeclaredFields()) {
					if (!Modifier.isStatic(g.getModifiers()) && !MIMO_OTISK.contains(g.getName())) {
						g.setAccessible(true);
						vysledek.add(new Object[] { f, g });
					}
				}
			} else {
				vysledek.add(new Object[] { null, f });
			}
		}
		return vysledek;
	}

	private static boolean zmen(final Object vlastnik, final Field f) throws Exception {
		final Class<?> t = f.getType();
		if (t == String.class) {
			f.set(vlastnik, f.get(vlastnik) + "x");
		} else if (t == int.class) {
			f.setInt(vlastnik, f.getInt(vlastnik) + 1);
		} else if (t == double.class) {
			f.setDouble(vlastnik, f.getDouble(vlastnik) + 1);
		} else if (t == boolean.class) {
			f.setBoolean(vlastnik, !f.getBoolean(vlastnik));
		} else if (t == Wgs.class) {
			final Wgs w = (Wgs) f.get(vlastnik);
			f.set(vlastnik, new Wgs(w.lat + 0.001, w.lon));
		} else if (Map.class.isAssignableFrom(t)) {
			@SuppressWarnings("unchecked")
			final Map<String, String> m = (Map<String, String>) f.get(vlastnik);
			m.put("gen", "jina");
		} else {
			throw new AssertionError("neznámý typ pole " + f + ": doplnit do testu i do otisku, nebo do MIMO_OTISK");
		}
		return true;
	}

	@Test
	public void ulozeneZaznamyPrezijiNacteniAVadneSePreskoci() {
		final CasyDatZdroju casy = new CasyDatZdroju();
		final File a = new File("/data/a.gpx");
		final File b = new File("/data/b c;d.db3");
		casy.put(a, new CasyDatZdroju.Zaznam(-5L, 1000L));
		casy.put(b, new CasyDatZdroju.Zaznam(42L, 2000L));
		final Set<String> ulozene = casy.ponechej(f -> true);

		final CasyDatZdroju nove = new CasyDatZdroju();
		final Set<String> sVadnymi = new LinkedHashSet<>(ulozene);
		sVadnymi.add("nesmysl");
		sVadnymi.add("zz;1;/data/x.gpx");
		sVadnymi.add("1;2;");
		nove.nacti(sVadnymi);
		Assert.assertEquals(1000L, nove.get(a).cas);
		Assert.assertEquals(-5L, nove.get(a).otiskObsahu);
		Assert.assertEquals("středník v cestě", 2000L, nove.get(b).cas);
		Assert.assertNull(nove.get(new File("/data/x.gpx")));
		Assert.assertEquals(ulozene, nove.ponechej(f -> true));
	}

	@Test
	public void zaznamZTohotoBehuSeNacitanimNeprepise() {
		final CasyDatZdroju casy = new CasyDatZdroju();
		final File a = new File("/data/a.gpx");
		casy.put(a, new CasyDatZdroju.Zaznam(1L, 5000L));
		casy.nacti(Collections.singleton("1;1000;" + a.getPath()));
		Assert.assertEquals(5000L, casy.get(a).cas);
	}

	@Test
	public void ponechejZapomeneZmizeleZdroje() {
		final CasyDatZdroju casy = new CasyDatZdroju();
		casy.put(new File("/data/a.gpx"), new CasyDatZdroju.Zaznam(1L, 1L));
		casy.put(new File("/data/b.gpx"), new CasyDatZdroju.Zaznam(2L, 2L));
		final Set<String> zbyle = casy.ponechej(f -> f.getName().equals("a.gpx"));
		Assert.assertEquals(1, zbyle.size());
		Assert.assertNull(casy.get(new File("/data/b.gpx")));
	}

	@Test
	public void casPoPrecteniBezZmenyObsahuZustane() {
		final CasyDatZdroju casy = new CasyDatZdroju();
		final File a = new File("/data/a.gpx");
		Assert.assertEquals("bez záznamu čas souboru", 700L, casy.casPoPrecteni(a, 9L, 700L));
		casy.put(a, new CasyDatZdroju.Zaznam(9L, 100L));
		Assert.assertEquals(100L, casy.casPoPrecteni(a, 9L, 700L));
		Assert.assertEquals(700L, casy.casPoPrecteni(a, 8L, 700L));
	}

	/** Dlouhá hodnota se v nastavení dělí na části; po zkrácení nesmí zůstat staré části. */
	@Test
	public void zkracenyZaznamVNastaveniNenechaStareCasti() {
		final MyPreferences pref = MyPreferences.current().node("test-casy-dat-zdroju");
		final Set<String> dlouhe = new LinkedHashSet<>();
		for (int i = 0; i < 400; i++) {
			dlouhe.add("abcdef0123456789;1700000000000;/velmi/dlouha/cesta/ke/zdroji/cislo/" + i + ".gpx");
		}
		pref.putStringSet("casy", dlouhe);
		Assert.assertEquals(dlouhe, pref.getStringSet("casy", null));
		final Set<String> kratke = Collections.singleton("1;2;/a.gpx");
		pref.putStringSet("casy", kratke);
		Assert.assertEquals(kratke, pref.getStringSet("casy", null));
		pref.remove("casy");
	}
}
