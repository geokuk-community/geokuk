package cz.geokuk.plugins.kesoid.mapicon;

import java.util.*;

import org.junit.*;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Otevření filtru nad daty bez některých alel nesmí ty alely z filtru vyhodit. */
public class JVyberIkon0Test {

	@Test
	public void nevykreslenaVybranaAlelaZustane() {
		final List<QualAlelaNames> zmeny = new ArrayList<>();
		final JVyberIkon0 vyber = new JVyberIkon0(false, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean shouldEnable(final Alela alela) {
				return true;
			}

			@Override
			protected boolean shouldRender(final Alela alela) {
				return false;
			}

			@Override
			protected boolean shouldRender(final Gen gen) {
				return false;
			}

			@Override
			protected void zmenaVyberu(final Set<Alela> aVybraneAlely) {
				zmeny.add(jmenaVcetneNevykreslenych(aVybraneAlely));
			}
		};
		final QualAlelaNames nechtene = new QualAlelaNames("arch:stav", "dsbl:stav", "fnd:vztah");
		vyber.refresh(new IkonBag(), nechtene, null);
		Assert.assertEquals(Collections.singletonList(nechtene), zmeny);
	}

	/** Ikony ve výběru jsou ikony toolbaru (stav, zákaz, vztah) zmenšené na stranu dialogu. */
	@Test
	public void ikonyVeVyberuJsouIkonyToolbaruVMensiVelikosti() {
		final Genom genom = new Genom();
		final java.awt.image.BufferedImage zakladni = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		final IkonBag bag = new IkonBag(genom) {
			@Override
			public javax.swing.Icon seekIkon(final Genotyp genotyp) {
				return new javax.swing.ImageIcon(zakladni);
			}
		};
		final JVyberIkon0 vyber = new JVyberIkon0(false, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean shouldEnable(final Alela alela) {
				return true;
			}

			@Override
			protected boolean shouldRender(final Alela alela) {
				return true;
			}

			@Override
			protected boolean shouldRender(final Gen gen) {
				return gen != genom.symGen;
			}

			@Override
			protected void zmenaVyberu(final Set<Alela> aVybraneAlely) {}
		};
		vyber.refresh(bag, new QualAlelaNames(), null);
		final java.util.List<javax.swing.JLabel> popisky = new ArrayList<>();
		sber(vyber, popisky);
		Assert.assertFalse(popisky.isEmpty());
		for (final javax.swing.JLabel l : popisky) {
			Assert.assertNotNull(l.getIcon());
			Assert.assertEquals(JVyberIkon0.VELIKOST_IKONY, l.getIcon().getIconWidth());
			Assert.assertEquals(JVyberIkon0.VELIKOST_IKONY, l.getIcon().getIconHeight());
		}
	}

	private static void sber(final java.awt.Container c, final java.util.List<javax.swing.JLabel> popisky) {
		for (final java.awt.Component k : c.getComponents()) {
			if (k instanceof javax.swing.JLabel && ((javax.swing.JLabel) k).getIcon() != null) {
				popisky.add((javax.swing.JLabel) k);
			}
			if (k instanceof java.awt.Container) {
				sber((java.awt.Container) k, popisky);
			}
		}
	}
}
