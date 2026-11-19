package cz.geokuk.plugins.kesoid.hledani;

import static org.junit.Assert.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;

public class HledacTest {

	@Test
	public void textBezOhleduNaVelikostADiakritiku() {
		final Nalezenec nal = new Hledac.Porovnavac("zámek", false).porovnej("Pod ZAMKEM a u ZÁMKU");
		assertNull(nal);
		final Nalezenec zamku = new Hledac.Porovnavac("zamku", false).porovnej("Pod hradem u ZÁMKU");
		assertEquals(13, zamku.getPoc());
		assertEquals(18, zamku.getKon());
		assertNull(new Hledac.Porovnavac("hrad", false).porovnej("Pod zámkem"));
	}

	@Test
	public void regularniVyrazBezOhleduNaVelikostADiakritiku() {
		final Nalezenec nal = new Hledac.Porovnavac("z.mk[uy]", true).porovnej("Pod hradem u ZÁMKU");
		assertEquals(13, nal.getPoc());
		assertEquals(18, nal.getKon());
	}

	@Test
	public void velkaPismenaVRegularnimVyrazuMajiVyznam() {
		assertEquals("\\D je ne-číslice", 3, new Hledac.Porovnavac("\\D+", true).porovnej("123abc").getPoc());
		assertEquals("\\S je ne-mezera", 2, new Hledac.Porovnavac("\\S", true).porovnej("  x").getPoc());
		assertEquals("\\W je ne-písmeno", 3, new Hledac.Porovnavac("\\W", true).porovnej("abc-d").getPoc());
		assertEquals("\\Q…\\E je doslovný text", 3, new Hledac.Porovnavac("\\Qa.b\\E", true).porovnej("axbA.B").getPoc());
	}

	private static Kesoid kesoid(final String identifier, final String... prohledavanci) {
		final Kesoid k = mock(Kesoid.class);
		when(k.getIdentifier()).thenReturn(identifier);
		when(k.getProhledavanci()).thenReturn(prohledavanci);
		return k;
	}

	private static List<Nalezenec> hledej(final String vzorek, final boolean regularni, final Kesoid... kesoidy) {
		final KesBag bag = mock(KesBag.class);
		when(bag.getKesoidy()).thenReturn(Arrays.asList(kesoidy));
		final HledaciPodminka podm = new HledaciPodminka();
		podm.setVzorek(vzorek);
		podm.setRegularniVyraz(regularni);
		return new Hledac(bag).hledej(podm);
	}

	@Test
	public void odkazNeboKodNajdeKesoidPresne() {
		final Kesoid a = kesoid("GC19ACP", "Nazev GC19ACP x");
		final Kesoid b = kesoid("GCBZJGC", "Jiny GCBZJGC");
		for (final String vzorek : new String[] { "https://coord.info/GCBZJGC_nazev", "GCBZJGC", "https://www.geocaching.com/seek/cache_details.aspx?wp=GCBZJGC" }) {
			final List<Nalezenec> nalezeno = hledej(vzorek, false, a, b);
			assertEquals(vzorek, 1, nalezeno.size());
			assertSame(b, nalezeno.get(0).getKes());
			assertEquals("GCBZJGC", nalezeno.get(0).getKdeNalezeno());
			assertEquals(0, nalezeno.get(0).getPoc());
			assertEquals(7, nalezeno.get(0).getKon());
		}
	}

	@Test
	public void kodKterehoVDatechNeniSeHledaJakoText() {
		final Kesoid a = kesoid("GC19ACP", "Pod hradem GC12 OC12 se hleda");
		final List<Nalezenec> nalezeno = hledej("OC12", false, a);
		assertEquals("text vypadající jako kód, který v datech není, se hledá jako text", 1, nalezeno.size());
		assertEquals(16, nalezeno.get(0).getPoc());
		assertEquals(0, hledej("https://coord.info/GCZZZZZ", false, a).size());
	}

	@Test
	public void regularniVyrazSeHledaJakoDriv() {
		final Kesoid a = kesoid("GC19ACP", "Nazev GC19ACP x");
		final List<Nalezenec> nalezeno = hledej("GC19ACP", true, a);
		assertEquals(1, nalezeno.size());
		assertEquals("při regulárním výrazu se hledá v textech jako dřív, ne podle kódu", 6, nalezeno.get(0).getPoc());
	}

	@Test
	public void zastavkaAdventureLabSeNajdePresneAHoleKodNajdeZaklad() {
		final Kesoid zaklad = kesoid("GC5PGVC", "Lab GC5PGVC");
		final Kesoid zastavka = kesoid("GC5PGVC-3", "Lab GC5PGVC-3 zastavka");
		assertSame(zastavka, hledej("GC5PGVC-3", false, zaklad, zastavka).get(0).getKes());
		assertSame(zaklad, hledej("GC5PGVC", false, zaklad, zastavka).get(0).getKes());
		assertEquals(1, hledej("GC5PGVC", false, zaklad, zastavka).size());
		assertSame("zastávka, která v datech není, najde základ", zaklad, hledej("GC5PGVC-9", false, zaklad, zastavka).get(0).getKes());
		assertEquals("GC5PGVC-3", hledej("https://example.org/ GC5PGVC-3", false, zaklad, zastavka).get(0).getKdeNalezeno());
	}
}
