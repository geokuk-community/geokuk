package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
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

	/** Obrys v pixelech se při dílčích překresleních nepřepočítává; po změně výřezu ano. */
	@Test
	public void obrysSeDrziVCachiDokudSeNezmeniVyrez() {
		final JObrysMapSlide s = slide(8);
		final PolygonPokryti cesko = PolygonPokryti.obdelnik(48.55, 12.09, 51.06, 18.86);
		final Path2D prvni = s.cestaZCache(cesko);
		Assert.assertSame(prvni, s.cestaZCache(cesko));
		s.setSoord(new Coord(8, new Wgs(50, 16).toMou(), new Dimension(400, 300), 0));
		final Path2D druha = s.cestaZCache(cesko);
		Assert.assertNotSame(prvni, druha);
		Assert.assertNotEquals(prvni.getBounds2D(), druha.getBounds2D());
	}

	private static JObrysMapSlide slide(final int zoom) {
		final JObrysMapSlide s = new JObrysMapSlide();
		s.setSize(400, 300);
		s.setSoord(new Coord(zoom, new Wgs(50, 15).toMou(), new Dimension(400, 300), 0));
		return s;
	}

	private static BufferedImage nakresli(final JObrysMapSlide s) {
		final BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		s.paintComponent(g);
		g.dispose();
		return img;
	}

	private static int alfa(final BufferedImage img, final int x, final int y) {
		return img.getRGB(x, y) >>> 24;
	}

	private static boolean neprazdny(final BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if (alfa(img, x, y) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static final List<PolygonPokryti> MAPA = Collections.singletonList(PolygonPokryti.obdelnik(49.97, 14.95, 50.03, 15.05));

	/** Ve výchozím stavu se nekreslí nic, ani když jsou mapy ve složce. */
	@Test
	public void vychoziStavNekresliNic() {
		final JObrysMapSlide s = slide(6);
		s.setObrysy(MAPA);
		Assert.assertFalse(neprazdny(nakresli(s)));
		Assert.assertFalse(s.isVisible());
	}

	/** Trvalý obrys je jen čára bez výplně okolí, do Z7 včetně; od Z8 nic. */
	@Test
	public void trvalyObrysJenDoZ7TenkouCarou() {
		final JObrysMapSlide s = slide(JObrysMapSlide.NEJVETSI_ZOOM);
		s.setObrysy(MAPA);
		s.trvaly = true;
		final BufferedImage z7 = nakresli(s);
		Assert.assertTrue(neprazdny(z7));
		Assert.assertEquals("mimo mapu čisto", 0, alfa(z7, 2, 2));
		Assert.assertEquals("uvnitř mapy čisto", 0, alfa(z7, 200, 150));
		final JObrysMapSlide z8 = slide(JObrysMapSlide.NEJVETSI_ZOOM + 1);
		z8.setObrysy(MAPA);
		z8.trvaly = true;
		Assert.assertFalse(neprazdny(nakresli(z8)));
	}

	/** Najetí na řádek ukáže obrys jen té mapy, i v podrobném zoomu; odjetí ho skryje. */
	@Test
	public void obrysMapyNadKterouJeMys() {
		final JObrysMapSlide s = slide(11);
		s.setObrysy(MAPA);
		s.trvaly = true;
		Assert.assertFalse("trvalý obrys od Z8 nic", neprazdny(nakresli(s)));
		s.trvaly = false;
		s.zvyrazni("europe/liechtenstein");
		Assert.assertTrue(s.isVisible());
		Assert.assertNotNull(s.pokrytiZvyrazneni);
		s.setSoord(new Coord(11, new Wgs(47.14, 9.52).toMou(), new Dimension(400, 300), 0));
		Assert.assertTrue(neprazdny(nakresli(s)));
		s.zvyrazni(null);
		Assert.assertFalse(s.isVisible());
		Assert.assertFalse(neprazdny(nakresli(s)));
	}

	/** Obrys stahované mapy je vidět po dobu stahování a po dokončení zmizí. */
	@Test
	public void obrysStahovaneMapy() {
		final JObrysMapSlide s = slide(11);
		s.setSoord(new Coord(11, new Wgs(47.14, 9.52).toMou(), new Dimension(400, 300), 0));
		final KatalogovaMapa lichtenstejnsko = KatalogMap.vestaveny().podleId("europe/liechtenstein").get();
		s.onEvent(new SpravaMapEvent(StavMapyProGui.stav(lichtenstejnsko, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.STAHUJE, 5, 100, null)));
		Assert.assertTrue(s.isVisible());
		Assert.assertTrue(neprazdny(nakresli(s)));
		s.onEvent(new SpravaMapEvent(StavMapyProGui.stav(lichtenstejnsko, StavMapy.Mistni.MAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.HOTOVO, 0, -1, null)));
		Assert.assertFalse(s.isVisible());
		Assert.assertFalse(neprazdny(nakresli(s)));
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

	/** Hlídání složky při nečinnosti složku neprochází, nový soubor s mapou ano. */
	@Test(timeout = 30000)
	public void hlidaniSlozkyBezZmenyNeprochazi() throws Exception {
		final File slozka = tmp.getRoot();
		final JObrysMapSlide[] s = new JObrysMapSlide[1];
		SwingUtilities.invokeAndWait(() -> {
			s[0] = new JObrysMapSlide();
			s[0].slozkaMapZdroj = () -> slozka;
			s[0].ctecka = (sl, katalog) -> Collections.emptyList();
			s[0].onEvent(new ObrysPokrytiEvent(true));
		});
		dokonciCteni(s[0]);
		SwingUtilities.invokeAndWait(() -> s[0].nacti(false));
		dokonciCteni(s[0]);
		final long kontrol = s[0].kontrol;
		for (int i = 0; i < 20; i++) {
			SwingUtilities.invokeAndWait(() -> s[0].nacti(false));
		}
		dokonciCteni(s[0]);
		Assert.assertEquals("bez změny se složka nekontroluje", kontrol, s[0].kontrol);
		Files.write(new File(slozka, "czech-republic.map").toPath(), new byte[10]);
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (s[0].kontrol == kontrol && System.nanoTime() < konec) {
			SwingUtilities.invokeAndWait(() -> s[0].nacti(false));
			Thread.sleep(20);
		}
		Assert.assertTrue("nová mapa vyvolá kontrolu", s[0].kontrol > kontrol);
		dokonciCteni(s[0]);
		SwingUtilities.invokeAndWait(() -> s[0].setVisible(false));
	}
}
