package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.ObrysPokrytiEvent;
import cz.geokuk.plugins.mapy.sprava.*;

/** Obrys pokrytí se kreslí v pixelech výřezu: uvnitř je území mapy, mimo ne. */
public class JObrysMapSlideTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void obrysVPixelechVyrezu() {
		final Coord soord = new Coord(8, new Wgs(50, 15).toMou(), new Dimension(800, 600), 0);
		final PolygonPokryti cesko = PolygonPokryti.obdelnik(48.55, 12.09, 51.06, 18.86);
		final Path2D cesta = JObrysMapSlide.cesta(soord, cesko);
		final Point praha = soord.transform(new Wgs(50.08, 14.42).toMou());
		final Point viden = soord.transform(new Wgs(48.21, 16.37).toMou());
		Assert.assertTrue(cesta.contains(praha));
		Assert.assertFalse(cesta.contains(viden));
	}

	@Test
	public void doTiskuNejde() {
		Assert.assertNull(new JObrysMapSlide().createRenderableSlide());
	}

	/** Obrysy se čtou mimo EDT; hlídání složky je bez změny map znovu nečte, dokončené stažení ano. */
	@Test(timeout = 20000)
	public void cteniMimoEdtAJenPoZmene() throws Exception {
		final BlockingQueue<Boolean> cteni = new LinkedBlockingQueue<>();
		final JObrysMapSlide[] s = new JObrysMapSlide[1];
		SwingUtilities.invokeAndWait(() -> {
			s[0] = new JObrysMapSlide();
			s[0].ctecka = (slozka, katalog) -> {
				cteni.add(SwingUtilities.isEventDispatchThread());
				return Collections.emptyList();
			};
			s[0].onEvent(new ObrysPokrytiEvent(true));
		});
		Assert.assertEquals(Boolean.FALSE, cteni.poll(10, TimeUnit.SECONDS));
		dokonciCteni(s[0]);
		SwingUtilities.invokeAndWait(() -> s[0].nacti(false));
		Assert.assertNull("bez změny map se obrysy znovu nečtou", cteni.poll(1, TimeUnit.SECONDS));
		final KatalogovaMapa cesko = KatalogMap.vestaveny().podleId("europe/czech-republic").get();
		final StavMapy stazena = StavMapyProGui.stav(cesko, StavMapy.Mistni.MAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.HOTOVO, 0, -1, null);
		SwingUtilities.invokeAndWait(() -> s[0].onEvent(new SpravaMapEvent(stazena)));
		Assert.assertEquals("po stažení mapy se obrysy čtou znovu", Boolean.FALSE, cteni.poll(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> s[0].setVisible(false));
	}

	private static void dokonciCteni(final JObrysMapSlide s) throws Exception {
		final boolean[] cte = { true };
		while (cte[0]) {
			SwingUtilities.invokeAndWait(() -> cte[0] = s.isCteni());
		}
	}

	@Test
	public void otiskSeMeniSMapamiVeSlozce() throws Exception {
		final File slozka = tmp.getRoot();
		final String prazdna = SouboryMap.otisk(slozka);
		Files.write(new File(slozka, "czech-republic.map").toPath(), new byte[10]);
		final String sMapou = SouboryMap.otisk(slozka);
		Assert.assertNotEquals(prazdna, sMapou);
		Assert.assertEquals(sMapou, SouboryMap.otisk(slozka));
		Files.write(new File(slozka, "czech-republic.map").toPath(), new byte[20]);
		Assert.assertNotEquals(sMapou, SouboryMap.otisk(slozka));
	}
}
