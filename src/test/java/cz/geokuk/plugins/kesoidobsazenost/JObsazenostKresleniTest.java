package cz.geokuk.plugins.kesoidobsazenost;

import java.awt.*;
import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.mvc.KeskyNactenyEvent;
import cz.geokuk.util.index2d.BoundingRect;
import cz.geokuk.util.index2d.Indexator;

/** Obsazenost se kreslí jako jedna plocha jedné barvy, i v hustě obsazeném území. */
public class JObsazenostKresleniTest {

	private static final int SIRKA = 800;
	private static final int VYSKA = 600;
	private static final Color BARVA = new Color(128, 128, 128, 50);

	private final Coord coord = new Coord(17, new Wgs(50, 15).toMou(), new Dimension(SIRKA, VYSKA), 0);

	/** Načtené keše: jen index waypointů, víc obsazenost nepotřebuje. */
	private static final class NacteneKese extends KesBag {
		private Indexator<Wpt> index = new Indexator<>(BoundingRect.ALL);

		NacteneKese() {
			super(new Genom());
		}

		@Override
		public void add(final Wpt wpt) {
			index = index.add(wpt.getMou().xx, wpt.getMou().yy, wpt);
		}

		@Override
		public void done() {}

		@Override
		public Indexator<Wpt> getIndexator() {
			return index;
		}
	}

	/** Keš posunutá o dx, dy px od středu okna. */
	private Wpt kes(final KesBag bag, final int dx, final int dy, final EKesStatus status) {
		final Kes kes = new Kes();
		kes.setStatus(status);
		final Wpt wpt = new Wpt();
		wpt.setSym(Wpt.TRADITIONAL_CACHE);
		final Mou mou = coord.transform(new Point(SIRKA / 2 + dx, VYSKA / 2 + dy));
		wpt.setWgs(mou.toWgs());
		kes.addWpt(wpt);
		bag.add(wpt);
		return wpt;
	}

	private BufferedImage nakresli(final KesBag bag) {
		final JObsazenost s = new JObsazenost();
		s.setSoord(coord);
		s.onEvent(new ObsazenostPreferencesChangeEvent(nastaveni()));
		bag.done();
		s.onEvent(new KeskyNactenyEvent(bag));
		final BufferedImage img = new BufferedImage(SIRKA, VYSKA, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		s.paintComponent(g);
		g.dispose();
		return img;
	}

	private static ObsazenostSettings nastaveni() {
		final ObsazenostSettings o = new ObsazenostSettings();
		o.setColor(BARVA);
		return o;
	}

	private int polomer() {
		return (int) (coord.getPixluNaMetr() * 161);
	}

	@Test
	public void prekryvMaStejnouBarvuJakoJedenKruh() {
		final KesBag bag = new NacteneKese();
		final int r = polomer();
		kes(bag, -r / 2, 0, EKesStatus.ACTIVE);
		kes(bag, r / 2, 0, EKesStatus.ACTIVE);
		final BufferedImage img = nakresli(bag);
		final int jeden = img.getRGB(SIRKA / 2 - r, VYSKA / 2);
		final int prekryv = img.getRGB(SIRKA / 2, VYSKA / 2);
		Assert.assertEquals("v kruhu je barva obsazenosti", BARVA.getAlpha(), (jeden >>> 24) & 0xFF, 1);
		Assert.assertEquals("překryv netmavne", Integer.toHexString(jeden), Integer.toHexString(prekryv));
		Assert.assertEquals("mimo kruhy nic", 0, img.getRGB(5, 5));
	}

	/** Hustá skupina (víc keší, než by okno pokrylo dvakrát) se nakreslí. */
	@Test
	public void hustaSkupinaSeKresli() {
		final KesBag bag = new NacteneKese();
		final int r = polomer();
		for (int i = 0; i < 400; i++) {
			kes(bag, (i % 20) * 40 - 380, (i / 20) * 30 - 280, EKesStatus.ACTIVE);
		}
		Assert.assertTrue("předpoklad: kruhy by okno pokryly víc než dvakrát", 400 * Math.PI * r * r > 2.0 * SIRKA * VYSKA);
		Assert.assertEquals(BARVA.getAlpha(), (nakresli(bag).getRGB(SIRKA / 2, VYSKA / 2) >>> 24) & 0xFF, 1);
	}

	@Test
	public void archivovanaKesNeobsazuje() {
		final KesBag bag = new NacteneKese();
		kes(bag, 0, 0, EKesStatus.ARCHIVED);
		Assert.assertEquals(0, nakresli(bag).getRGB(SIRKA / 2, VYSKA / 2));
	}

	/** Při zvětšení obrazovky (např. 150 %) se maska kreslí v plném rozlišení a sedí na místo. */
	@Test
	public void zvetseniObrazovky() {
		final KesBag bag = new NacteneKese();
		kes(bag, 0, 0, EKesStatus.ACTIVE);
		final JObsazenost s = new JObsazenost();
		s.setSoord(coord);
		s.onEvent(new ObsazenostPreferencesChangeEvent(nastaveni()));
		bag.done();
		s.onEvent(new KeskyNactenyEvent(bag));
		final BufferedImage img = new BufferedImage(SIRKA * 3 / 2, VYSKA * 3 / 2, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.scale(1.5, 1.5);
		s.paintComponent(g);
		g.dispose();
		final int r = polomer();
		Assert.assertNotEquals("střed kruhu", 0, img.getRGB(SIRKA * 3 / 4, VYSKA * 3 / 4));
		Assert.assertNotEquals("těsně uvnitř okraje", 0, img.getRGB((int) ((SIRKA / 2 + r - 2) * 1.5), VYSKA * 3 / 4));
		Assert.assertEquals("těsně vně okraje", 0, img.getRGB((int) ((SIRKA / 2 + r + 3) * 1.5), VYSKA * 3 / 4));
	}

	/** Maska se mezi kresleními znovu používá; kruhy z předchozího kreslení v ní nezůstanou. */
	@Test
	public void maskaSeMeziKreslenimiSmaze() {
		final JObsazenost s = new JObsazenost();
		s.setSoord(coord);
		s.onEvent(new ObsazenostPreferencesChangeEvent(nastaveni()));
		final KesBag prvni = new NacteneKese();
		kes(prvni, -250, 0, EKesStatus.ACTIVE);
		s.onEvent(new KeskyNactenyEvent(prvni));
		final BufferedImage img1 = new BufferedImage(SIRKA, VYSKA, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g1 = img1.createGraphics();
		s.paintComponent(g1);
		g1.dispose();
		Assert.assertNotEquals(0, img1.getRGB(SIRKA / 2 - 250, VYSKA / 2));
		final KesBag druha = new NacteneKese();
		kes(druha, 250, 0, EKesStatus.ACTIVE);
		s.onEvent(new KeskyNactenyEvent(druha));
		final BufferedImage img2 = new BufferedImage(SIRKA, VYSKA, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img2.createGraphics();
		s.paintComponent(g2);
		g2.dispose();
		Assert.assertEquals(0, img2.getRGB(SIRKA / 2 - 250, VYSKA / 2));
		Assert.assertNotEquals(0, img2.getRGB(SIRKA / 2 + 250, VYSKA / 2));
	}
}
