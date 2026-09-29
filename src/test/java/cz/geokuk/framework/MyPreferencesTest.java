package cz.geokuk.framework;

import static com.google.common.truth.Truth.assertThat;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.*;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.kesoid.mapicon.ASada;

/**
 * Unit tests for {@link MyPreferences}.
 *
 * TODO : add more tests
 */
@RunWith(JUnit4.class)
public class MyPreferencesTest {

	class MockPreferences extends AbstractPreferences {

		private final Map<String, String> storage = new HashMap<>();

		protected MockPreferences(final AbstractPreferences parent, final String name) {
			super(parent, name);
		}

		@Override
		protected void flushSpi() throws BackingStoreException {

		}

		@Override
		protected String getSpi(final String key) {
			return storage.get(key);
		}

		@Override
		protected String[] childrenNamesSpi() throws BackingStoreException {
			return new String[0];
		}

		@Override
		protected AbstractPreferences childSpi(final String name) {
			return null;
		}

		@Override
		protected String[] keysSpi() throws BackingStoreException {
			return new String[0];
		}

		@Override
		protected void putSpi(final String key, final String value) {
			storage.put(key, value);
		}

		@Override
		protected void removeNodeSpi() throws BackingStoreException {

		}

		@Override
		protected void removeSpi(final String key) {
			storage.remove(key);
		}

		@Override
		protected void syncSpi() throws BackingStoreException {

		}
	}

	private MyPreferences preferences;

	@Before
	public void setUp() {
		preferences = new MyPreferences(new MockPreferences(null, ""));
	}

	@Test
	public void test_longStringStorage() {
		final String storingString = Strings.repeat("FOOBAR@;", 3000);
		preferences.put("@jhka", storingString);
		assertThat(preferences.get("@jhka", null)).isEqualTo(storingString);
	}

	@Test
	public void kratkaHodnotaPoDlouheNemaPrilepky() {
		preferences.put("klic", Strings.repeat("x", 20000));
		preferences.put("klic", "kratka");
		assertThat(preferences.get("klic", null)).isEqualTo("kratka");
	}

	@Test
	public void kratsiDlouhaHodnotaPoDelsi() {
		preferences.put("klic", Strings.repeat("x", 30000));
		final String kratsi = Strings.repeat("y", 10000);
		preferences.put("klic", kratsi);
		assertThat(preferences.get("klic", null)).isEqualTo(kratsi);
	}

	@Test
	public void odstraneniDlouheHodnoty() {
		preferences.put("klic", Strings.repeat("x", 20000));
		preferences.remove("klic");
		assertThat(preferences.get("klic", "vychozi")).isEqualTo("vychozi");
		preferences.put("klic", "nova");
		assertThat(preferences.get("klic", null)).isEqualTo("nova");
	}

	@Test
	public void nullHodnota() {
		preferences.put("klic", null);
		assertThat(preferences.get("klic", "vychozi")).isNull();
	}

	@Test(expected = IllegalArgumentException.class)
	public void vyhrazenyKlic() {
		preferences.put("klic;cont0", "x");
	}

	@Test
	public void seznamSeZnakyOddelovace() {
		final List<String> toStore = ImmutableList.of("a;b", "c\\d", ";", "\\", "");
		preferences.putStringList("seznam", toStore);
		assertThat(preferences.getStringList("seznam", null)).isEqualTo(toStore);
	}

	@Test
	public void nesmyslneHodnotySeNahradiVychozimi() {
		final Mou vychoziMou = new Mou(1, 2);
		preferences.put("mou", "123");
		assertThat(preferences.getMou("mou", vychoziMou)).isEqualTo(vychoziMou);
		preferences.put("mou", "abc,def");
		assertThat(preferences.getMou("mou", vychoziMou)).isEqualTo(vychoziMou);

		final Point vychoziBod = new Point(3, 4);
		preferences.put("bod", "5");
		assertThat(preferences.getPoint("bod", vychoziBod)).isEqualTo(vychoziBod);

		final Dimension vychoziRozmer = new Dimension(800, 600);
		preferences.put("rozmer", "prazdno");
		assertThat(preferences.getDimension("rozmer", vychoziRozmer)).isEqualTo(vychoziRozmer);

		final Wgs vychoziWgs = new Wgs(50, 14);
		preferences.put("wgs", "50.1");
		assertThat(preferences.getWgs("wgs", vychoziWgs)).isEqualTo(vychoziWgs);
		preferences.put("wgs", "NaN,NaN");
		assertThat(preferences.getWgs("wgs", vychoziWgs)).isEqualTo(vychoziWgs);
	}

	@Test
	public void test_putAtom() {
		final Atom toStore = Atom.valueOf(ASada.class, "Standard");
		preferences.putAtom("jhka", toStore);
		assertThat(preferences.getAtom("jhka", null, ASada.class)).isEqualTo(toStore);
	}

	@Test
	public void test_putFile() {
		final File toStore = new File("/tmp/foobar");
		preferences.putFile("jhka", toStore);
		assertThat(preferences.getFile("jhka", null)).isEqualTo(toStore);
	}

	@Test
	public void test_putStringList() {
		final List<String> toStore = ImmutableList.of("qwert", "asdfgh", "yxcvb", "12345", "@{}^<");
		preferences.putStringList("jhka", toStore);
		assertThat(preferences.getStringList("jhka", null)).isEqualTo(toStore);
	}

	@Test
	public void test_putStringSet() {
		final Set<String> toStore = ImmutableSet.of("12345", "qwert", "asdfgh", "yxcvb", "@{}^<");
		preferences.putStringSet("jhka", toStore);
		assertThat((Iterable<String>) preferences.getStringSet("jhka", null)).isEqualTo(toStore);
	}

	@Test
	public void test_shortStringStorage() {
		preferences.put("@jhka", "FOOBAR@;\"");
		assertThat(preferences.get("@jhka", null)).isEqualTo("FOOBAR@;\"");
	}
}
