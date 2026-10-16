package cz.geokuk.plugins.kesoid.importek;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.hledani.*;
import cz.geokuk.smoke.SyntetickeKese;

/**
 * Hledání v Kešoidy > Najdi keš nad desítkami tisíc keší načtených z GPX: pevná data, pevné dotazy, počty hledaných keší se počítají z čísla keše. Počet keší mění
 * {@code scenare.kesi} (výchozí 20 000, 0 test přeskočí).
 */
public class PraceSVelkymiDatyTest {

	private static final int POCET = Integer.getInteger("scenare.kesi", 20_000);

	@ClassRule
	public static TemporaryFolder tmp = new TemporaryFolder();

	private static KesBag bag;
	private static int waypointu;

	@BeforeClass
	public static void nacti() throws Exception {
		Assume.assumeTrue("scenare.kesi=0 test vypíná", POCET > 0);
		final File slozka = tmp.newFolder();
		waypointu = SyntetickeKese.zapis(new File(slozka, "velka.gpx"), POCET, 50.1, 14.4, 1.0);
		final MultiNacitac nacitac = new MultiNacitac(new NacitaniGpxSouboruTest.Model());
		nacitac.setRootDirs(true, slozka, null, null, Collections.emptySet());
		bag = nacitac.nacti(null, new Genom());
	}

	private static List<Nalezenec> hledej(final String vzorek, final boolean regularni) {
		final HledaciPodminka podminka = new HledaciPodminka();
		podminka.setVzorek(vzorek);
		podminka.setRegularniVyraz(regularni);
		return new Hledac(bag).hledej(podminka);
	}

	/** Počet keší s číslem 0 až POCET-1, jejichž majitel je „ownerN“ (N = číslo mod 97, jak je zapisuje generátor). */
	private static int majitele(final int... zbytky) {
		int pocet = 0;
		for (int i = 0; i < POCET; i++) {
			for (final int z : zbytky) {
				if (i % 97 == z) {
					pocet++;
				}
			}
		}
		return pocet;
	}

	@Test
	public void nacteneJsouVsechnyKese() {
		Assert.assertEquals(POCET, bag.getKesoidy().size());
		Assert.assertEquals(waypointu, bag.getWpts().size());
	}

	@Test(timeout = 60000)
	public void hledaniTextuNajdePresneTytoKese() {
		Assert.assertEquals(majitele(42), hledej("owner42", false).size());
		Assert.assertEquals("bez ohledu na velikost písmen", majitele(42), hledej("OWNER42", false).size());
		Assert.assertEquals(0, hledej("nikdyNeexistuje", false).size());
	}

	@Test(timeout = 60000)
	public void hledaniRegularnimVyrazem() {
		Assert.assertEquals(majitele(90, 91, 92, 93, 94, 95, 96), hledej("owner9[0-6]$", true).size());
	}

	@Test(timeout = 60000)
	public void hledaniVseNajdeKazdouKes() {
		final List<Nalezenec> vse = hledej(".*", true);
		Assert.assertEquals(POCET, vse.size());
		Assert.assertEquals("každá keš jednou", POCET, new HashSet<>(vse.stream().map(Nalezenec::getKes).collect(java.util.stream.Collectors.toList())).size());
	}

	/** Tabulka výsledků s keší od každé: otevření výsledku nesmí zastavit okno. */
	@Test(timeout = 120000)
	public void tabulkaSVysledkyVsechKesiSeNaplniVRozumnemCase() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final List<Nalezenec> vse = hledej(".*", true);
		final AtomicReference<JKesTable> tabulka = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> tabulka.set(new JKesTable()));
		final long zacatek = System.nanoTime();
		SwingUtilities.invokeAndWait(() -> tabulka.get().setKeslist(vse));
		final long ms = (System.nanoTime() - zacatek) / 1_000_000;
		Assert.assertNotNull("první výsledek je vybraný", tabulka.get().getCurrent());
		Assert.assertTrue("naplnění tabulky " + POCET + " výsledky trvalo " + ms + " ms", ms < 10_000);
	}
}
