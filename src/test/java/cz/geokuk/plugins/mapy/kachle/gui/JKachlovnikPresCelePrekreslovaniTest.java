package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComponent;
import javax.swing.RepaintManager;

import org.junit.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.UzivatelskeMapyPristup;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.plugins.mapy.sprava.StavyMapEvent;

/** Změny vrstev a stavů map nepřekreslují celý kachlovník, jen radu a atribuci, a jen když se změnily. */
public class JKachlovnikPresCelePrekreslovaniTest {

	private static class ZaznamPrekresleni extends RepaintManager {
		final List<Rectangle> oblasti = new ArrayList<>();

		@Override
		public void addDirtyRegion(final JComponent c, final int x, final int y, final int w, final int h) {
			if (c instanceof JKachlovnikPresCele) {
				oblasti.add(new Rectangle(x, y, w, h));
			}
		}
	}

	private RepaintManager puvodni;

	@Before
	public void setUp() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		puvodni = RepaintManager.currentManager(null);
	}

	@After
	public void tearDown() {
		if (puvodni != null) {
			RepaintManager.setCurrentManager(puvodni);
		}
		UzivatelskeMapyPristup.vycisti();
	}

	@Test
	public void prekresliSeJenRadaAJenPriZmene() {
		final AtomicReference<EKaType> podklad = new AtomicReference<>(EKaType.OFFLINE_MF);
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return podklad.get();
			}

			@Override
			protected KachleModel getKachleModel() {
				return new KachleModel() {
					@Override
					public boolean jePodrobnaOfflineMapa(final Wgs bod, final int moumer) {
						return false;
					}

					@Override
					public String getAtribuceOfflineMapy() {
						return "© přispěvatelé OpenStreetMap";
					}
				};
			}
		};
		k.inject(new SpravaMapModel());
		k.setSize(800, 600);
		k.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		k.setSoord(new Coord(12, new Wgs(50.08, 14.42).toMou(), new Dimension(800, 600), 0));
		final BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		k.paintChildren(g);
		k.kresliAtribuci(g);
		final ZaznamPrekresleni zaznam = new ZaznamPrekresleni();
		RepaintManager.setCurrentManager(zaznam);

		for (int i = 0; i < 20; i++) {
			k.onEvent(new StavyMapEvent());
		}
		Assert.assertTrue("beze změny obsahu se nepřekresluje: " + zaznam.oblasti, zaznam.oblasti.isEmpty());

		podklad.set(UzivatelskeMapyPristup.sHromadnymStahovanim("bezatribuce", "http://127.0.0.1:1/{z}/{x}/{y}", 0, 18));
		k.onEvent(new StavyMapEvent());
		Assert.assertFalse("zmizela rada i atribuce", zaznam.oblasti.isEmpty());
		for (final Rectangle r : zaznam.oblasti) {
			Assert.assertTrue("jen rada a atribuce, ne celá mapa: " + r, r.height < 300);
		}
		// Uživatel nesmí vidět zbytek rady nahoře uprostřed ani starou atribuci vpravo dole.
		Assert.assertTrue("překreslí se místo rady: " + zaznam.oblasti, zaznam.oblasti.stream().anyMatch(r -> r.contains(400, 12)));
		Assert.assertTrue("překreslí se místo atribuce: " + zaznam.oblasti, zaznam.oblasti.stream().anyMatch(r -> r.contains(795, 595)));
		g.dispose();
	}
}
