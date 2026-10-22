package cz.geokuk.plugins.cesty;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.RepaintManager;

import org.junit.*;

import cz.geokuk.core.coord.*;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;

/** Pohyb myši nad vrstvou cest nepřekresluje celou mapu. */
public class JCestySlidePrekreslovaniTest {

	private static final Wgs STRED = new Wgs(50.005, 14.004);
	private static final Dimension OKNO = new Dimension(400, 300);

	private static class ZaznamPrekresleni extends RepaintManager {
		final List<Rectangle> oblasti = new ArrayList<>();

		@Override
		public void addDirtyRegion(final JComponent c, final int x, final int y, final int w, final int h) {
			if (c instanceof JCestySlide) {
				oblasti.add(new Rectangle(x, y, w, h));
			}
		}
	}

	private RepaintManager puvodni;
	private ZaznamPrekresleni zaznam;
	private JCestySlide slide;

	@Before
	public void setUp() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		puvodni = RepaintManager.currentManager(null);
		zaznam = new ZaznamPrekresleni();
		RepaintManager.setCurrentManager(zaznam);
		slide = new JCestySlide();
		slide.inject(new CestyModel());
		slide.setSoord(new Coord(14, STRED.toMou(), OKNO, 0));
		slide.setSize(OKNO);
		zaznam.oblasti.clear();
	}

	@After
	public void tearDown() {
		if (puvodni != null) {
			RepaintManager.setCurrentManager(puvodni);
		}
	}

	private void mys(final Wgs w) {
		final Point p = slide.getSoord().transform(w.toMou());
		slide.onEvent(new ZmenaSouradnicMysiEvent(p, w.toMou(), w.toMou()));
	}

	private Doc vytvorDoc() {
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.00, 14.00).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.01, 14.01).toMou());
		return doc;
	}

	@Test
	public void bezVyletuSePohybMysiNepreklresluje() {
		for (int i = 0; i < 100; i++) {
			mys(new Wgs(50.0 + i * 0.0001, 14.0 + i * 0.0001));
		}
		Assert.assertTrue(zaznam.oblasti.toString(), zaznam.oblasti.isEmpty());
	}

	@Test
	public void sVyletemSePrekreslujeJenPriZmeneBouska() {
		slide.onEvent(new CestyChangedEvent(vytvorDoc(), null));
		zaznam.oblasti.clear();
		// daleko od cesty
		for (int i = 0; i < 50; i++) {
			mys(new Wgs(50.005, 14.02 + i * 0.0001));
		}
		Assert.assertTrue(zaznam.oblasti.toString(), zaznam.oblasti.isEmpty());
		// nad úsekem se mění jen tvar dělicího bodu, nikdy celá komponenta
		mys(new Wgs(50.005, 14.005));
		for (int i = 0; i < 20; i++) {
			mys(new Wgs(50.005 + i * 0.0001, 14.005 + i * 0.0001));
		}
		Assert.assertFalse(zaznam.oblasti.isEmpty());
		for (final Rectangle r : zaznam.oblasti) {
			Assert.assertTrue(r.toString(), r.width < OKNO.width && r.height < OKNO.height);
		}
	}

	/** Dělicí bod se nad úsekem posouvá s myší; každý posun po témže úseku se musí překreslit. */
	@Test
	public void posunPoTemzeUsekuSePrekresli() {
		slide.onEvent(new CestyChangedEvent(vytvorDoc(), null));
		mys(new Wgs(50.005, 14.005));
		zaznam.oblasti.clear();
		mys(new Wgs(50.0051, 14.0051));
		Assert.assertFalse("posun po úseku", zaznam.oblasti.isEmpty());
	}

	/** Když myš od cesty odjede, zvýraznění musí zmizet: překreslí se celá oblast cesty. */
	@Test
	public void odjezdOdCestySmazeZvyrazneni() {
		slide.onEvent(new CestyChangedEvent(vytvorDoc(), null));
		mys(new Wgs(50.005, 14.005));
		zaznam.oblasti.clear();
		mys(new Wgs(50.005, 14.03));
		final Point a = slide.getSoord().transform(new Wgs(50.00, 14.00).toMou());
		final Point b = slide.getSoord().transform(new Wgs(50.01, 14.01).toMou());
		Rectangle vse = null;
		for (final Rectangle r : zaznam.oblasti) {
			vse = vse == null ? new Rectangle(r) : vse.union(r);
		}
		Assert.assertNotNull("odjezd od cesty", vse);
		Assert.assertTrue(vse + " má obsahovat " + a, vse.contains(a));
		Assert.assertTrue(vse + " má obsahovat " + b, vse.contains(b));
	}
}
