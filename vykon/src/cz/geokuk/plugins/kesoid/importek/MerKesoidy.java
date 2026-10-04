package cz.geokuk.plugins.kesoid.importek;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.genetika.QualAlelaNames;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mapicon.*;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.plugins.vylety.*;
import cz.geokuk.util.file.*;

/**
 * Měření kreslení kešoidů: jedno překreslení vrstvy kešoidů v okně 1400x900 s N kešemi ve výřezu, ikony (bez limitu waypointů) a tečky. Argumenty:
 * {@code ikony N1,N2,... tecky M1,M2,...}.
 */
public class MerKesoidy {

	static final Dimension OKNO = new Dimension(1400, 900);
	static final Wgs STRED = new Wgs(50.0, 14.5);
	static final int ZOOM_IKON = 14;
	static final int ZOOM_TECEK = 10;
	private static final String[] TYPY = { "Traditional Cache", "Multi-cache", "Unknown Cache", "Letterbox Hybrid", "Wherigo Cache", "Virtual Cache", "Earthcache", "Event Cache" };

	public static void main(final String[] a) throws Exception {
		System.setProperty("java.awt.headless", "true");
		final IkonBag ikony = new IkonNacitacLoader().nacti(null, true, ASada.STANDARD);
		String druh = null;
		for (final String arg : a) {
			if (arg.equals("ikony") || arg.equals("tecky")) {
				druh = arg;
				continue;
			}
			for (final String s : arg.split(",")) {
				mer(ikony, druh.equals("ikony"), Integer.parseInt(s.trim()));
			}
		}
	}

	static void mer(final IkonBag ikony, final boolean ikonami, final int n) throws Exception {
		final int zoom = ikonami ? ZOOM_IKON : ZOOM_TECEK;
		final Coord soord = new Coord(zoom, STRED.toMou(), OKNO, 0);
		KesBag kese = importuj(ikony, soord, n);
		final Runtime rt = Runtime.getRuntime();
		final JKesoidySlide slide = slide(ikony, soord, kese, ikonami);
		final BufferedImage img = new BufferedImage(OKNO.width, OKNO.height, BufferedImage.TYPE_INT_ARGB);
		kresli(slide, img); // zahřátí, ikony se přiřadí waypointům
		kresli(slide, img);
		final int opakovani = 7;
		final double[] t = new double[opakovani];
		final com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long alokovano = 0;
		for (int i = 0; i < opakovani; i++) {
			final long a0 = mx.getCurrentThreadAllocatedBytes();
			final long t0 = System.nanoTime();
			kresli(slide, img);
			t[i] = (System.nanoTime() - t0) / 1e6;
			alokovano = Math.max(alokovano, mx.getCurrentThreadAllocatedBytes() - a0);
		}
		Arrays.sort(t);
		System.gc();
		final long halda = (rt.totalMemory() - rt.freeMemory()) >> 20;
		System.out.printf(Locale.ROOT, "%s N=%d (zoom %d, ve výřezu %d): překreslení medián %.1f ms (min %.1f, max %.1f), alokace na překreslení %.1f MB, halda s daty %d MB%n",
				ikonami ? "ikony" : "tečky", n, zoom, kese.getIndexator().count(soord.getBoundingRect()), t[opakovani / 2], t[0], t[opakovani - 1], alokovano / 1048576.0, halda);
		kese = null;
	}

	static void kresli(final JKesoidySlide slide, final BufferedImage img) {
		final Graphics2D g = img.createGraphics();
		try {
			g.setClip(0, 0, OKNO.width, OKNO.height);
			slide.render(g);
		} finally {
			g.dispose();
		}
	}

	static JKesoidySlide slide(final IkonBag ikony, final Coord soord, final KesBag kese, final boolean ikonami) throws Exception {
		// Kreslení pro tisk: bez limitu waypointů a ikony se přiřadí hned; po zahřátí stejná cesta jako na obrazovce.
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
			public void setPrekrocenLimitWaypointuVeVyrezu(final boolean prekrocenLimit) {}
		});
		slide.setSoord(soord);
		slide.setSize(OKNO);
		slide.onEvent(new IkonyNactenyEvent(ikony, ASada.STANDARD));
		slide.onEvent(new FenotypPreferencesChangedEvent(QualAlelaNames.EMPTY));
		slide.onEvent(new KeskyVyfiltrovanyEvent(kese, kese));
		final Field f = JKesoidySlide.class.getDeclaredField("zobrazeni");
		f.setAccessible(true);
		f.set(slide, ikonami ? EZobrazeniKesi.IKONY : EZobrazeniKesi.TECKY);
		return slide;
	}

	/** N keší rovnoměrně náhodně ve výřezu, typy a stavy (nalezené, vlastní, neaktivní) se střídají. */
	static KesBag importuj(final IkonBag ikony, final Coord soord, final int n) throws Exception {
		final File gpx = File.createTempFile("merkesoidy", ".gpx");
		try {
			final Mou sz = soord.getMouSZ();
			final Mou jv = soord.getMouJV();
			final Random r = new Random(n);
			try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(gpx), StandardCharsets.UTF_8))) {
				w.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n");
				for (int i = 0; i < n; i++) {
					final Wgs wgs = new Mou(sz.xx + (int) (r.nextDouble() * ((long) jv.xx - sz.xx)), jv.yy + (int) (r.nextDouble() * ((long) sz.yy - jv.yy))).toWgs();
					final String typ = TYPY[i % TYPY.length];
					final int stav = i / TYPY.length % 8;
					final String sym = stav == 1 ? "Geocache Found" : "Geocache";
					final String autor = stav == 2 ? "Ja" : "Cizi";
					final boolean dostupna = stav != 3;
					w.write(String.format(Locale.ROOT, "<wpt lat=\"%.6f\" lon=\"%.6f\"><name>GC%X</name><sym>%s</sym><type>Geocache|%s</type>", wgs.lat, wgs.lon, 0x10000 + i, sym, typ));
					w.write("<groundspeak:cache available=\"" + (dostupna ? "True" : "False") + "\" archived=\"False\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">");
					w.write("<groundspeak:name>K" + i + "</groundspeak:name><groundspeak:placed_by>" + autor + "</groundspeak:placed_by><groundspeak:owner id=\"" + (stav == 2 ? 42 : 1) + "\">" + autor
							+ "</groundspeak:owner><groundspeak:type>" + typ + "</groundspeak:type><groundspeak:container>Small</groundspeak:container>"
							+ "<groundspeak:difficulty>2</groundspeak:difficulty><groundspeak:terrain>1.5</groundspeak:terrain></groundspeak:cache></wpt>\n");
				}
				w.write("</gpx>");
			}
			final cz.geokuk.framework.ProgressModel progress = new cz.geokuk.framework.ProgressModel();
			progress.inject(udalost -> {});
			final KesoidImportBuilder builder = new KesoidImportBuilder(ikony.getGenom(), new GccomNick("Ja", 42), progress, new KesoidPluginManager());
			builder.init();
			final File adresar = gpx.getParentFile();
			builder.setCurrentlyLoading(new KeFile(new FileAndTime(gpx, 0), new Root(adresar, new Root.Def(0, null, null))), true);
			try (InputStream in = new BufferedInputStream(new FileInputStream(gpx))) {
				new NacitacGpx().nacti(in, gpx.getName(), builder, null);
			}
			builder.done();
			return builder.getKesBag();
		} finally {
			gpx.delete();
		}
	}
}
