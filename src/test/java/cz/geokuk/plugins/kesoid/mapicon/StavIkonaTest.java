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

	private static boolean cervenaCara(final int argb) {
		return ((argb >> 16) & 0xFF) > 170 && ((argb >> 8) & 0xFF) < 80 && (argb & 0xFF) < 80 && (argb >>> 24) > 100;
	}

	private static boolean viditelne(final int argb) {
		return (argb >>> 24) > 40;
	}

	@Test
	public void zakazJeCervenyPresSedouIkonuAArchivSeLisi() {
		for (final int velikost : new int[] { 16, 24, 48 }) {
			final BufferedImage z = obraz(StavIkona.zakaz(zaklad(velikost), V));
			final BufferedImage a = obraz(StavIkona.archiv(zaklad(velikost), V));
			Assert.assertEquals(V, z.getWidth());
			Assert.assertEquals(V, z.getHeight());
			Assert.assertEquals(V, a.getWidth());
			Assert.assertEquals(V, a.getHeight());
			// Zákaz: červený kruh přes celou plochu, uvnitř šedá ikona.
			Assert.assertTrue(cervenaCara(z.getRGB(V / 2, 2)));
			Assert.assertTrue(cervenaCara(z.getRGB(2, V / 2)));
			final int uvnitr = z.getRGB(V / 2 - 3, V / 2 - 3);
			Assert.assertTrue(viditelne(uvnitr));
			Assert.assertEquals((uvnitr >> 16) & 0xFF, (uvnitr >> 8) & 0xFF, 2);
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
	public void naCtverecSVyplniNechaRezervuKolemObsahu() {
		final Icon ikona = StavIkona.naCtverec(zaklad(V), V, 0.8);
		Assert.assertEquals(V, ikona.getIconWidth());
		final BufferedImage n = obraz(ikona);
		Assert.assertFalse(viditelne(n.getRGB(0, V / 2)));
		Assert.assertFalse(viditelne(n.getRGB(1, V / 2)));
		Assert.assertTrue(viditelne(n.getRGB(V / 2, V / 2)));
	}

	@Test
	public void nevylusteJeDilekSkladanky() {
		final BufferedImage n = obraz(StavIkona.nevyluste(V));
		Assert.assertEquals(V, n.getWidth());
		Assert.assertEquals(V, n.getHeight());
		Assert.assertTrue(viditelne(n.getRGB(V / 2, V / 2)));
		Assert.assertTrue(viditelne(n.getRGB(V / 2 - 2, 6)));
		Assert.assertFalse(viditelne(n.getRGB(0, 0)));
	}

	/** Snímek toolbaru do poznámek, jen když je zadána cesta. */
	@Test
	public void snimek() throws Exception {
		final String cesta = System.getProperty("snimek.stav");
		if (cesta == null) {
			return;
		}
		final Icon[] ikony = { zaklad(V), StavIkona.zakaz(zaklad(V), V), StavIkona.archiv(zaklad(V), V), StavIkona.archiv(zaklad(48), V) };
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

	private static Icon plnyCtverec(final int velikost) {
		final BufferedImage img = new BufferedImage(velikost, velikost, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = img.createGraphics();
		g.setColor(new Color(40, 120, 220));
		g.fillRect(0, 0, velikost, velikost);
		g.dispose();
		return new ImageIcon(img);
	}

	private static IkonBag bagSPlnymCtvercem() {
		return new IkonBag(new Genom()) {
			@Override
			public Icon seekIkon(final Genotyp genotyp) {
				return plnyCtverec(V);
			}
		};
	}

	/** Nehledané, Nalezené a Moje vlastní mají kolem obsahu rezervu, ostatní ikony ne. */
	@Test
	public void rezervuKolemObsahuMajiJenVztahy() {
		final IkonBag bag = bagSPlnymCtvercem();
		final Genom g = bag.getGenom();
		for (final Alela a : new Alela[] { g.ALELA_hnf, g.ALELA_fnd, g.ALELA_own }) {
			final BufferedImage n = obraz(StavIkona.ikonaToolbaru(bag, a));
			Assert.assertFalse(a.qualName(), viditelne(n.getRGB(0, V / 2)));
			Assert.assertTrue(a.qualName(), viditelne(n.getRGB(V / 2, V / 2)));
		}
		for (final Alela a : new Alela[] { g.ALELA_cpt, g.ALELA_dpl, g.ALELA_gc, g.ALELA_wp }) {
			Assert.assertTrue(a.qualName(), viditelne(obraz(StavIkona.ikonaToolbaru(bag, a)).getRGB(0, V / 2)));
		}
	}

	/** Disablovaná i Archivovaný z ikonaToolbaru mají svůj symbol, ne ikonu sady. */
	@Test
	public void disablovanaAArchivovanyMajiSymbolPresIkonaToolbaru() {
		final IkonBag bag = bagSPlnymCtvercem();
		final Genom g = bag.getGenom();
		final BufferedImage z = obraz(StavIkona.ikonaToolbaru(bag, g.ALELA_dsbl));
		Assert.assertTrue(cervenaCara(z.getRGB(V / 2, 2)));
		final BufferedImage a = obraz(StavIkona.ikonaToolbaru(bag, g.ALELA_arch));
		Assert.assertTrue(cervene(a.getRGB(V - 3, V - 10)));
		final BufferedImage obycejna = obraz(StavIkona.ikonaToolbaru(bag, g.ALELA_gc));
		Assert.assertFalse(cervenaCara(obycejna.getRGB(V / 2, 2)));
		Assert.assertFalse(cervene(obycejna.getRGB(V - 3, V - 10)));
	}

	/** Šikmá čára zákazu vede přes střed ikony. */
	@Test
	public void zakazMaSikmouCaruPresStred() {
		final BufferedImage z = obraz(StavIkona.zakaz(zaklad(V), V));
		Assert.assertTrue(cervenaCara(z.getRGB(V / 2, V / 2)));
		Assert.assertTrue(cervenaCara(z.getRGB(V / 2 - 4, V / 2 + 4)));
		Assert.assertFalse("mimo čáru je šedá keš", cervenaCara(z.getRGB(V / 2 - 4, V / 2 - 4)));
	}

	/** Nečtvercová ikona se zvětší na výšku, vycentruje a po stranách zůstane průhledná. */
	@Test
	public void naCtverecCentrujeNeCtvercovouIkonu() {
		final BufferedImage uzka = new BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = uzka.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, 10, 20);
		g.dispose();
		final BufferedImage n = obraz(StavIkona.naCtverec(new ImageIcon(uzka), V));
		Assert.assertEquals(V, n.getWidth());
		int vlevo = 0;
		while (vlevo < V && !viditelne(n.getRGB(vlevo, V / 2))) {
			vlevo++;
		}
		int vpravo = 0;
		while (vpravo < V && !viditelne(n.getRGB(V - 1 - vpravo, V / 2))) {
			vpravo++;
		}
		Assert.assertTrue("okraje po stranách: " + vlevo + " / " + vpravo, vlevo >= 4);
		Assert.assertTrue(Math.abs(vlevo - vpravo) <= 1);
		Assert.assertTrue(viditelne(n.getRGB(V / 2, 0)));
		Assert.assertTrue(viditelne(n.getRGB(V / 2, V - 1)));
	}

	/** Oddělovač je vidět jen před viditelnou skupinou, která má před sebou jinou viditelnou; žádný na začátku ani na konci. */
	@Test
	public void oddelovaceSkryvajiPrazdneSkupiny() throws Exception {
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
		SwingUtilities.invokeAndWait(() -> {
			for (final Component c : panel.getComponents()) {
				if (c instanceof JSeparator) {
					Assert.assertFalse("bez viditelných ovladačů se po načtení keší oddělovače skryjí", c.isVisible());
				}
			}
		});
		final java.lang.reflect.Method skryj = JToolbarOvladaceAlel.class.getDeclaredMethod("skryjPrazdneOddelovace");
		skryj.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			final Component[] komponenty = panel.getComponents();
			// Skupiny ovladačů mezi oddělovači.
			final List<List<Component>> skupiny = new ArrayList<>();
			final List<Component> oddelovace = new ArrayList<>();
			skupiny.add(new ArrayList<>());
			for (final Component c : komponenty) {
				if (c instanceof JSeparator) {
					oddelovace.add(c);
					skupiny.add(new ArrayList<>());
				} else {
					skupiny.get(skupiny.size() - 1).add(c);
				}
			}
			final int m = skupiny.size();
			Assert.assertTrue("aspoň tři skupiny: " + m, m >= 4);
			for (int maska = 0; maska < (1 << m); maska++) {
				for (int k = 0; k < m; k++) {
					for (final Component c : skupiny.get(k)) {
						c.setVisible((maska & (1 << k)) != 0 && c == skupiny.get(k).get(0));
					}
				}
				try {
					skryj.invoke(panel);
				} catch (final ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				boolean videlJsem = false;
				for (int k = 0; k < m; k++) {
					final boolean skupinaVidet = (maska & (1 << k)) != 0 && !skupiny.get(k).isEmpty();
					if (k >= 1) {
						Assert.assertEquals("maska " + Integer.toBinaryString(maska) + " oddělovač před skupinou " + k, videlJsem && skupinaVidet, oddelovace.get(k - 1).isVisible());
					}
					videlJsem |= skupinaVidet;
				}
			}
		});
	}
}
