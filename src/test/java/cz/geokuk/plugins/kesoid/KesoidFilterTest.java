package cz.geokuk.plugins.kesoid;

import java.util.*;

import org.junit.*;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup;

/** Filtr zobrazených waypointů nad kešemi z importu GPX. */
public class KesoidFilterTest {

	private static final String PARKOVISTE = "<wpt lat=\"50.3\" lon=\"14.6\"><name>PK1111</name><sym>Parking Area</sym><type>Waypoint|Parking Area</type></wpt>\n";
	private static final String FINAL = "<wpt lat=\"50.2\" lon=\"14.5\"><name>FI2222</name><sym>Final Location</sym><type>Waypoint|Final Location</type></wpt>\n";
	private static final String PARKOVISTE_MYSTERY = "<wpt lat=\"50.25\" lon=\"14.55\"><name>PK2222</name><sym>Parking Area</sym><type>Waypoint|Parking Area</type></wpt>\n";

	private List<Wpt> wpts;

	@Before
	public void setUp() throws Exception {
		wpts = ImportKesiTestPristup.importuj(
				ImportKesiTestPristup.kes("GC1111", "Geocache Found", "Traditional Cache", 80, 3, 15), PARKOVISTE,
				ImportKesiTestPristup.kes("GC2222", "Geocache", "Unknown Cache", 95, 10, 50), FINAL, PARKOVISTE_MYSTERY,
				ImportKesiTestPristup.kes("GC3333", "Geocache", "Traditional Cache", 60, 1, 2),
				ImportKesiTestPristup.kesBezHodnoceni("GC4444")).getWpts();
	}

	private Set<String> zobrazene(final FilterDefinition definice) {
		final KesoidFilter filtr = new KesoidFilter(definice, null, null);
		final Set<String> vysledek = new TreeSet<>();
		for (final Wpt w : wpts) {
			if (filtr.isFiltered(w)) {
				vysledek.add(w.getName());
			}
		}
		return vysledek;
	}

	private static FilterDefinition definice(final boolean jenFinalUNalezenych, final boolean jenDoTerenuUNenalezenych) {
		final FilterDefinition d = new FilterDefinition();
		d.setJenFinalUNalezenych(jenFinalUNalezenych);
		d.setJenDoTerenuUNenalezenych(jenDoTerenuUNenalezenych);
		return d;
	}

	@Test
	public void bezOmezeniJeVidetVse() {
		Assert.assertEquals(new TreeSet<>(Arrays.asList("GC1111", "PK1111", "GC2222", "FI2222", "PK2222", "GC3333", "GC4444")), zobrazene(definice(false, false)));
	}

	@Test
	public void uNalezenychJenHlavniWaypoint() {
		final Set<String> z = zobrazene(definice(true, false));
		Assert.assertTrue(z.contains("GC1111"));
		Assert.assertFalse(z.contains("PK1111"));
		Assert.assertTrue(z.contains("PK2222"));
	}

	@Test
	public void uVylustenychNenalezenychJenFinal() {
		final Set<String> z = zobrazene(definice(false, true));
		Assert.assertTrue(z.contains("FI2222"));
		Assert.assertFalse("místo listingu je vidět final", z.contains("GC2222"));
		Assert.assertFalse(z.contains("PK2222"));
		Assert.assertTrue("u nalezených se neuplatní", z.contains("PK1111"));
	}

	@Test
	public void kesBezHodnotyNastavenyPrahNesplni() {
		final FilterDefinition d = definice(false, false);
		Assert.assertTrue("bez prahů je vidět", zobrazene(d).contains("GC4444"));
		d.setPrahFavorit(1);
		Assert.assertFalse("favority", zobrazene(d).contains("GC4444"));
		d.setPrahFavorit(0);
		Assert.assertTrue("po vynulování prahů je zase vidět", zobrazene(d).contains("GC4444"));
	}

	@Test
	public void prahFavoritu() {
		final FilterDefinition d = definice(false, false);
		d.setPrahFavorit(20);
		Assert.assertEquals(new TreeSet<>(Arrays.asList("GC2222", "FI2222", "PK2222")), zobrazene(d));
	}

	/**
	 * Hodnocení a BestOf se od roku 2013 nevyplňují, filtr podle nich už není (#916). Práh uložený starší verzí v nastavení se nesmí uplatnit, jinak by
	 * neviditelně skryl všechny novější keše.
	 */
	@Test
	public void ulozenyPrahHodnoceniABestOfSeNeuplatni() throws Exception {
		final MyPreferences pref = MyPreferences.current().node("test-filtr-916");
		try {
			pref.node("filtr").putInt("prahHodnoceni", 90);
			pref.node("filtr").putInt("prahBestOf", 5);
			final FilterDefinition d = pref.getStructure("filtr", definice(false, false));
			Assert.assertEquals(new TreeSet<>(Arrays.asList("GC1111", "PK1111", "GC2222", "FI2222", "PK2222", "GC3333", "GC4444")), zobrazene(d));
		} finally {
			pref.removeNode();
		}
	}

	@Test
	public void doplnkovaPodminkaSePridavaKeVsemuOstatnimuKazdemuWaypointuKesoidu() {
		final KesoidFilter filtr = new KesoidFilter(definice(false, false), null, null).doplnkovy(k -> "GC2222".equals(k.getIdentifier()));
		final Set<String> z = new TreeSet<>();
		for (final Wpt w : wpts) {
			if (filtr.isFiltered(w)) {
				z.add(w.getName());
			}
		}
		Assert.assertTrue(z.toString(), z.contains("GC2222"));
		Assert.assertFalse(z.contains("GC1111"));
		Assert.assertFalse(z.contains("GC4444"));
		final KesoidFilter bez = new KesoidFilter(definice(false, false), null, null).doplnkovy(null);
		Assert.assertTrue(bez.isFiltered(wpts.get(0)));
	}
}
