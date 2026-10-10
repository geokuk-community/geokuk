package cz.geokuk.core.render;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Dlg;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OziExplorerRenderSwingWorker extends RendererSwingWorker0 {

	private final EWhatRender whatRender;

	OziExplorerRenderSwingWorker(final EWhatRender whatRender) {
		this.whatRender = whatRender;
	}

	@Override
	protected RenderResult doInBackground() throws Exception {
		progressor.setMax(Rendrovadlo.KOLIK_PROGRESUJEME_NA_KACHLICH);
		final File dir = renderModel.getOutputFolder();

		final EImageType imageType = renderModel.getRenderSettings().getImageType();

		// TODO správně by se parametry měly spočítat v konstruktoru, aby se nemohly v rendermodelu změnit
		final Rendrovadlo rendrovadlo = factory.init(new Rendrovadlo(this));
		final RenderParams p = new RenderParams();
		p.roord = renderModel.getRoord();
		p.natacetDoSeveru = renderModel.getRenderSettings().isSrovnatDoSeveru();
		// p.resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		// TODO typ obrázku musí být určen podle toho, zda se rendruje JPG nebo PNG.
		final String imageFileName = renderModel.getRenderSettings().getPureFileName().getText();
		progressor.setText("Rendrování " + imageFileName);
		dir.mkdirs();
		final String imageShortName = imageFileName + "." + imageType;
		final File imagePathName = new File(dir, imageShortName);
		final File mapPathName = new File(dir, imageFileName + ".map");
		if (!Dlg.prepsatSoubor(imagePathName)) {
			return null;
		}
		if (whatRender == EWhatRender.OZI_EXPLORER) {
			if (!Dlg.prepsatSoubor(mapPathName)) {
				return null;
			}
		}

		progressor.setTooltip("Probíhá rendrování pro " + whatRender + " do soubor: \"" + imagePathName + "\"");
		p.pruhledne = imageType.isUmoznujePruhlednost();

		final File mapa = whatRender == EWhatRender.OZI_EXPLORER ? mapPathName : null;
		zapisVystupy(() -> rendrovadlo.rendruj(p, progressor), imageType.getType(), imagePathName, mapa, pwrt -> {
			final List<Wgs> kalibody = renderModel.spocitejKalibracniBody();
			printOziMetafile(pwrt, imageShortName, p.roord.getDim().width, p.roord.getDim().height, p.roord, renderModel.getRenderSettings().getKalibrBodu(), kalibody);
		});
		log.debug("Konec rendrování");

		final RenderResult result = new RenderResult();
		result.file = mapa != null ? mapa : imagePathName;
		return result;
	}

	interface ZapisMapy {
		void zapis(PrintWriter p) throws IOException;
	}

	/** Při chybě nebo zrušení smaže jen soubory, které tento běh začal zapisovat; dřívější výstupy zůstanou. */
	static void zapisVystupy(final Callable<BufferedImage> rendr, final String typ, final File obrazek, final File mapa, final ZapisMapy zapisMapy) throws Exception {
		final List<File> zapisovane = new ArrayList<>();
		try {
			final BufferedImage image = rendr.call();
			log.debug("Zápis obrázku [{},{}] do souboru \"{}\"", image.getWidth(), image.getHeight(), obrazek);
			zapisovane.add(obrazek);
			ImageIO.write(image, typ, obrazek);
			if (mapa != null) {
				zapisovane.add(mapa);
				try (PrintWriter pwrt = new PrintWriter(new OutputStreamWriter(new FileOutputStream(mapa), kodovaniMapy()))) {
					zapisMapy.zapis(pwrt);
					zkontrolujZapis(pwrt, mapa);
				}
			}
		} catch (final Exception e) {
			for (final File f : zapisovane) {
				f.delete();
			}
			throw e;
		}
	}

	/** OziExplorer čte .map v kódování Windows (ANSI, česky windows-1250); Java od verze 18 má výchozí UTF-8, proto native.encoding. */
	static Charset kodovaniMapy() {
		final String nativni = System.getProperty("native.encoding");
		if (nativni != null) {
			try {
				return Charset.forName(nativni);
			} catch (final IllegalArgumentException e) {
				log.warn("Neznámé kódování systému {}, soubor .map se zapíše ve výchozím.", nativni);
			}
		}
		return Charset.defaultCharset();
	}

	/** PrintWriter chyby zápisu (plný disk) nehlásí výjimkou. */
	static void zkontrolujZapis(final PrintWriter p, final File soubor) throws IOException {
		if (p.checkError()) {
			throw new IOException("Nepodařilo se zapsat soubor " + soubor);
		}
	}

	private void printOziKalibracniBod(final PrintWriter p, final int cisloBodu, final int x, final int y, final Wgs wgs) {
		p.println(oziKalibracniBod(cisloBodu, x, y, wgs));
	}

	/** Ozi chce kladné stupně a minuty s polokoulí N/S a E/W. */
	static String oziKalibracniBod(final int cisloBodu, final int x, final int y, final Wgs wgs) {
		return String.format(Locale.ENGLISH, "Point%02d,xy,%d,%d,in, deg,%s,%s, grid,,,,N", cisloBodu, x, y, stupneMinuty(wgs.lat, 'N', 'S'), stupneMinuty(wgs.lon, 'E', 'W'));
	}

	private static String stupneMinuty(final double uhel, final char kladna, final char zaporna) {
		final long tisicinyMinut = Math.round(Math.abs(uhel) * 60_000);
		return String.format(Locale.ENGLISH, "%d,%10.3f,%c", tisicinyMinut / 60_000, tisicinyMinut % 60_000 / 1000.0, uhel < 0 ? zaporna : kladna);
	}

	private void printOziMetafile(final PrintWriter p, final String fileName, final int width, final int height, final Coord cocox, final int kalibrBodu, final List<Wgs> kalibody) {
		final Wgs sz = cocox.transform(new Point(0, 0)).toWgs();
		final Wgs sv = cocox.transform(new Point(width, 0)).toWgs();
		final Wgs jz = cocox.transform(new Point(0, height)).toWgs();
		final Wgs jv = cocox.transform(new Point(width, height)).toWgs();

		p.println("OziExplorer Map Data File Version 2.2");
		p.println(fileName);
		p.println(fileName);
		p.println("1 ,Map Code,");
		p.println("WGS 84,,   0.0000,   0.0000,WGS 84");
		p.println("Reserved 1             ");
		p.println("Reserved 2");
		p.println("Magnetic Variation,,,E");
		p.println("Map Projection,Latitude/Longitude,PolyCal,No,AutoCalOnly,No,BSBUseWPX,No");
		// Pixely z výpočtu bodů, zpětná transformace z Wgs by je posunula o pixel ven z obrázku.
		final List<Point> pixely = RenderModel.kalibracniPixely(width, height, kalibrBodu);
		for (int i = 0; i < kalibody.size(); i++) {
			final Point point = pixely.get(i);
			printOziKalibracniBod(p, i + 1, point.x, point.y, kalibody.get(i));
		}
		// printOziKalibracniBod(p, 1, 0, height, jz);
		// printOziKalibracniBod(p, 2, width, 0, sv);
		p.println("Projection Setup,,,,,,,,,,");
		p.println("Map Feature = MF ; Map Comment = MC     These follow if they exist");
		p.println("Track File = TF      These follow if they exist");
		p.println("Moving Map Parameters = MM?    These follow if they exist");
		p.println("MM0,Yes");
		p.println("MMPNUM,4");
		p.println("MMPXY,1,0,0");
		p.printf("MMPXY,2,%d,0%n", width);
		p.printf("MMPXY,3,%d,%d%n", width, height);
		p.printf("MMPXY,4,0,%d%n", height);
		p.printf(Locale.ENGLISH, "MMPLL,1,  %f,%f%n", sz.lon, sz.lat);
		p.printf(Locale.ENGLISH, "MMPLL,2,  %f,%f%n", sv.lon, sv.lat);
		p.printf(Locale.ENGLISH, "MMPLL,3,  %f,%f%n", jv.lon, jv.lat);
		p.printf(Locale.ENGLISH, "MMPLL,4,  %f,%f%n", jz.lon, jz.lat);

	}

	// OziExplorer Map Data File Version 2.2
	// Maršovsko.bmp
	// Maršovsko.bmp
	// 1 ,Map Code,
	// WGS 84,, 0.0000, 0.0000,WGS 84
	// Reserved 1
	// Reserved 2
	// Magnetic Variation,,,E
	// Map Projection,Latitude/Longitude,PolyCal,No,AutoCalOnly,No,BSBUseWPX,No
	// Point01,xy, 27, 615,in, deg, 49, 15.421,N, 16, 18.341,E, grid, , , ,N
	// Point02,xy, 884, 49,in, deg, 49, 17.798,N, 16, 24.056,E, grid, , , ,N
	// Projection Setup,,,,,,,,,,
	// Map Feature = MF ; Map Comment = MC These follow if they exist
	// Track File = TF These follow if they exist
	// Moving Map Parameters = MM? These follow if they exist
	// MM0,Yes
	// MMPNUM,4
	// MMPXY,1,0,0
	// MMPXY,2,1185,0
	// MMPXY,3,1185,671
	// MMPXY,4,0,671
	// MMPLL,1, 16.302682, 49.300063
	// MMPLL,2, 16.434388, 49.300063
	// MMPLL,3, 16.434388, 49.253097
	// MMPLL,4, 16.302682, 49.253097

}