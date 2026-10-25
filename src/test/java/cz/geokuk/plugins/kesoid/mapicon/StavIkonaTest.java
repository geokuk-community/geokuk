package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.genetika.Genotyp;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.gui.JIconCheckBox;

public class StavIkonaTest {

	private static final int V = JToolbarOvladaceAlel.VYSKA_IKONY;

	private static Icon zaklad(final int velikost) {
		final BufferedImage img = new BufferedImage(velikost, velikost, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = img.createGraphics();
		g.setColor(new Color(40, 120, 220));
		g.fillOval(1, 1, velikost - 2, velikost - 2);
		g.dispose();
		return new ImageIcon(img);
	}

	private static BufferedImage obraz(final Icon ikona) {
		final BufferedImage img = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics g = img.createGraphics();
		ikona.paintIcon(null, g, 0, 0);
		g.dispose();
		return img;
	}

	private static boolean cervene(final int argb) {
		return ((argb >> 16) & 0xFF) > 170 && ((argb >> 8) & 0xFF) < 80 && (argb & 0xFF) < 80 && (argb >>> 24) > 200;
	}

	private static boolean viditelne(final int argb) {
		return (argb >>> 24) > 40;
	}

	@Test
	public void zakazJeCervenyPresCelouIkonuAArchivSeLisi() {
		for (final int velikost : new int[] { 16, 24, 48 }) {
			final BufferedImage z = obraz(StavIkona.zakaz(V));
			final BufferedImage a = obraz(StavIkona.archiv(zaklad(velikost), V));
			Assert.assertEquals(V, z.getWidth());
			Assert.assertEquals(V, z.getHeight());
			Assert.assertEquals(V, a.getWidth());
			Assert.assertEquals(V, a.getHeight());
			// Zákaz: červený kruh přes celou plochu, střed mimo čáru průhledný (žádná krychle).
			Assert.assertTrue(cervene(z.getRGB(V / 2, 1)));
			Assert.assertTrue(cervene(z.getRGB(1, V / 2)));
			Assert.assertFalse(viditelne(z.getRGB(V / 2 - 4, V / 2 - 4)));
			// Archiv: červený čtvereček včetně rohu, zákaz tam kruhem červený není.
			Assert.assertTrue(cervene(a.getRGB(V - 3, V - 10)));
			Assert.assertTrue(viditelne(a.getRGB(V - 1, V - 1)));
			Assert.assertFalse(viditelne(z.getRGB(V - 1, V - 1)));
		}
	}

	@Test
	public void duchMaViditelnyObrysAVyblednutouIkonu() {
		final BufferedImage a = obraz(StavIkona.archiv(zaklad(16), V));
		// Střed ikony je šedý a poloprůhledný, ne původní modrý.
		final int stred = a.getRGB(V / 2 - 3, V / 2 - 3);
		Assert.assertTrue(viditelne(stred));
		Assert.assertTrue((stred >>> 24) < 160);
		Assert.assertEquals((stred >> 16) & 0xFF, (stred >> 8) & 0xFF, 2);
		// Čárkovaný obrys je v okraji ikony.
		boolean obrys = false;
		for (int x = 0; x < V / 2; x++) {
			obrys |= viditelne(a.getRGB(x, 1));
		}
		Assert.assertTrue(obrys);
	}

	@Test
	public void poradiOvladacu() throws Exception {
		final IkonBag bag = new IkonBag(new Genom()) {
			@Override
			public Icon seekIkon(final Genotyp genotyp) {
				return zaklad(V);
			}
		};
		final KesBag kesky = new KesBag(bag.getGenom());
		kesky.done();
		final Factory factory = new Factory() {
			@Override
			public <T> T init(final T obj) {
				if (obj instanceof SwitchKesoidUrciteAlelyAction) {
					final SwitchKesoidUrciteAlelyAction a = (SwitchKesoidUrciteAlelyAction) obj;
					a.onEvent(new IkonyNactenyEvent(bag, ASada.STANDARD));
					a.onEvent(new KeskyNactenyEvent(kesky));
					a.initAfterEventReceiverRegistration();
				}
				return obj;
			}

			@Override
			public <T> T initNow(final T obj) {
				return init(obj);
			}
		};
		final JToolbarOvladaceAlel panel = new JToolbarOvladaceAlel(null);
		panel.inject(factory);
		SwingUtilities.invokeAndWait(() -> panel.onEvent(new KeskyNactenyEvent(kesky)));
		final List<String> poradi = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			for (final Component c : panel.getComponents()) {
				poradi.add(c instanceof JSeparator ? "|" : ((JIconCheckBox) c).getAction().getValue(Action.NAME).toString());
			}
		});
		final String s = String.join(" ", poradi);
		final Genom g = bag.getGenom();
		final int hnf = s.indexOf(g.ALELA_hnf.getDisplayName()), dpl = s.indexOf(g.ALELA_dpl.getDisplayName());
		final int dsbl = s.indexOf(g.ALELA_dsbl.getDisplayName()), arch = s.indexOf(g.ALELA_arch.getDisplayName());
		final int gc = s.indexOf(g.ALELA_gc.getDisplayName());
		final int sep1 = s.indexOf('|'), sep2 = s.indexOf('|', sep1 + 1);
		Assert.assertTrue(s, hnf >= 0 && hnf < dpl && dpl < sep1);
		Assert.assertTrue(s, sep1 < dsbl && dsbl < arch && arch < sep2);
		Assert.assertTrue(s, sep2 < gc);
	}

	@Test
	public void ikonyToolbaruMajiStejnouVelikostKromeDruhuWaypointu() {
		final Genom genom = new Genom();
		final IkonBag bag = new IkonBag(genom) {
			@Override
			public Icon seekIkon(final Genotyp genotyp) {
				return zaklad(16);
			}
		};
		final Alela[] stejne = { genom.ALELA_hnf, genom.ALELA_fnd, genom.ALELA_own, genom.ALELA_cpt, genom.ALELA_dpl, genom.ALELA_dsbl, genom.ALELA_arch, genom.ALELA_gc, genom.ALELA_mz, genom.ALELA_wp,
				genom.ALELA_nevyluste };
		for (final Alela alela : stejne) {
			final Icon ikona = StavIkona.ikonaToolbaru(bag, alela);
			Assert.assertEquals(alela.qualName(), V, ikona.getIconWidth());
			Assert.assertEquals(alela.qualName(), V, ikona.getIconHeight());
		}
		final Alela druhWaypointu = genom.symGen.getVychoziAlela();
		Assert.assertEquals(16, StavIkona.ikonaToolbaru(bag, druhWaypointu).getIconHeight());
	}

	@Test
	public void naCtverecZachovaPomerStranACentruje() {
		final BufferedImage uzka = obraz(StavIkona.naCtverec(zaklad(12), V));
		Assert.assertEquals(V, uzka.getWidth());
		Assert.assertEquals(V, uzka.getHeight());
		Assert.assertFalse(viditelne(uzka.getRGB(0, V / 2)));
		Assert.assertFalse(viditelne(uzka.getRGB(V - 1, V / 2)));
		Assert.assertTrue(viditelne(uzka.getRGB(V / 2, V / 2)));
		final Icon hotova = zaklad(V);
		Assert.assertSame(hotova, StavIkona.naCtverec(hotova, V));
		Assert.assertNull(StavIkona.naCtverec(null, V));
	}

	@Test
	public void nevylusteJeVidetPresPlochuIkony() {
		final BufferedImage n = obraz(StavIkona.nevyluste(V));
		Assert.assertEquals(V, n.getWidth());
		Assert.assertEquals(V, n.getHeight());
		Assert.assertTrue(viditelne(n.getRGB(V / 2, V / 2)));
		Assert.assertTrue(viditelne(n.getRGB(V / 2, 3)));
		Assert.assertFalse(viditelne(n.getRGB(0, 0)));
	}

	/** Snímek toolbaru do poznámek, jen když je zadána cesta. */
	@Test
	public void snimek() throws Exception {
		final String cesta = System.getProperty("snimek.stav");
		if (cesta == null) {
			return;
		}
		final Icon[] ikony = { zaklad(V), StavIkona.zakaz(V), StavIkona.archiv(zaklad(V), V), StavIkona.archiv(zaklad(48), V) };
		final BufferedImage img = new BufferedImage(ikony.length * 30 + 6, 36, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = img.createGraphics();
		g.setColor(new Color(238, 238, 238));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		for (int i = 0; i < ikony.length; i++) {
			ikony[i].paintIcon(null, g, 6 + i * 30, 6);
		}
		g.dispose();
		ImageIO.write(img, "png", new File(cesta));
		final BufferedImage velky = new BufferedImage(img.getWidth() * 8, img.getHeight() * 8, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D gv = velky.createGraphics();
		gv.drawImage(img, 0, 0, velky.getWidth(), velky.getHeight(), null);
		gv.dispose();
		ImageIO.write(velky, "png", new File(cesta.replace(".png", "-x8.png")));
	}
}
