package cz.geokuk.plugins.kesoid;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.kesoid.genetika.QualAlelaNames;
import cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup;
import cz.geokuk.plugins.kesoid.mapicon.*;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.plugins.vylety.*;

/** Charakterizační snímky teček kešoidů z pevných dat; bez dlaždic a textu, aby byly stejné na všech systémech. */
public class JKesoidySlideGoldenTest {

	private static final String[] TYPY = { "Traditional Cache", "Multi-cache", "Unknown Cache", "Letterbox Hybrid", "Wherigo Cache", "Virtual Cache", "Earthcache", "Webcam Cache",
			"Event Cache", "Cache In Trash Out Event" };
	private static final Wgs STRED = new Wgs(50.10, 14.42);
	private static final Dimension OKNO = new Dimension(320, 240);

	private static KesBag kese;
	private static KesBag keseZblizka;
	private static IkonBag ikony;

	@BeforeClass
	public static void data() throws Exception {
		ikony = new IkonNacitacLoader().nacti(null, true, ASada.STANDARD);
		kese = mrizka(0.02, 0.02);
		keseZblizka = mrizka(0.003, 0.0015);
	}

	/** Řádky: obyčejné, nalezené, vlastní, neaktivní (sudé sloupce archivované); sloupce podle typu. */
	private static KesBag mrizka(final double krokLon, final double krokLat) throws Exception {
		final List<String> wpt = new ArrayList<>();
		int n = 0;
		for (int radek = 0; radek < 4; radek++) {
			for (int sloupec = 0; sloupec < TYPY.length; sloupec++) {
				final String kod = String.format("GC%04d", ++n);
				final double lat = STRED.lat + 1.5 * krokLat - radek * krokLat;
				final double lon = STRED.lon - 4.5 * krokLon + sloupec * krokLon;
				final String typ = TYPY[sloupec];
				switch (radek) {
				case 0:
					wpt.add(ImportKesiTestPristup.kesNa(kod, "Geocache", typ, "Cizí", 1, true, false, lat, lon));
					break;
				case 1:
					wpt.add(ImportKesiTestPristup.kesNa(kod, "Geocache Found", typ, "Cizí", 1, true, false, lat, lon));
					break;
				case 2:
					wpt.add(ImportKesiTestPristup.kesNa(kod, "Geocache", typ, "Ja", 42, true, false, lat, lon));
					break;
				default:
					wpt.add(ImportKesiTestPristup.kesNa(kod, "Geocache", typ, "Cizí", 1, false, sloupec % 2 == 0, lat, lon));
				}
			}
		}
		return ImportKesiTestPristup.importuj(ikony.getGenom(), wpt.toArray(new String[0]));
	}

	@Test
	public void teckyZoom11() throws Exception {
		GoldenSnimek.porovnej("tecky-z11", vykresli(11));
	}

	@Test
	public void teckyZoom10() throws Exception {
		GoldenSnimek.porovnej("tecky-z10", vykresli(10));
	}

	/** Tečky se překrývají: nalezené dospod, neaktivní pod aktivní. */
	@Test
	public void teckyZoom9() throws Exception {
		GoldenSnimek.porovnej("tecky-z9", vykresli(9));
	}

	/** Ikony podle typu a stavu, každá keš zvlášť. */
	@Test
	public void ikonyZoom14() throws Exception {
		GoldenSnimek.porovnej("ikony-z14", vykresli(14, keseZblizka, new Dimension(400, 200)));
	}

	private static BufferedImage vykresli(final int zoom) {
		return vykresli(zoom, kese, OKNO);
	}

	private static BufferedImage vykresli(final int zoom, final KesBag data, final Dimension okno) {
		final JKesoidySlide slide = new JKesoidySlide(true);
		slide.inject(new VyletModel() {
			@Override
			public EVylet get(final Kesoid kes) {
				return EVylet.NEVIM;
			}
		});
		slide.inject(new CestyModel() {
			@Override
			public boolean isOnVylet(final Mouable mouable) {
				return false;
			}
		});
		slide.inject(new KesoidModel() {
			@Override
			public void setPrekrocenLimitWaypointuVeVyrezu(final boolean prekrocenLimit, final boolean tecky, final int limit) {}
		});
		slide.setSoord(new Coord(zoom, STRED.toMou(), okno, 0));
		slide.setSize(okno);
		slide.onEvent(new IkonyNactenyEvent(ikony, ASada.STANDARD));
		slide.onEvent(new FenotypPreferencesChangedEvent(new QualAlelaNames()));
		slide.onEvent(new KeskyVyfiltrovanyEvent(data, data));
		final BufferedImage img = new BufferedImage(okno.width, okno.height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try {
			slide.render(g);
		} finally {
			g.dispose();
		}
		return img;
	}
}
