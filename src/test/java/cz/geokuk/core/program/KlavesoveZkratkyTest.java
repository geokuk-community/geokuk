package cz.geokuk.core.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.*;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.junit.Test;

import cz.geokuk.plugins.mapy.PodkladAction;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/**
 * Každá klávesová zkratka v menu smí patřit jen jedné akci.
 */
public class KlavesoveZkratkyTest {

	@Test
	public void zadnaZkratkaNeniPrirazenaDvakrat() throws Exception {
		final Map<KeyStroke, List<String>> podleZkratky = new LinkedHashMap<>();
		final Akce akce = new Akce();
		for (final Field f : Akce.class.getFields()) {
			final Object hodnota = f.get(akce);
			if (hodnota instanceof Action) {
				pridej(podleZkratky, (Action) hodnota, f.getName());
			}
		}
		for (final EKaType ka : EKaType.values()) {
			pridej(podleZkratky, new PodkladAction(ka), "mapa " + ka.getNazev());
		}
		assertTrue("Našlo se jen " + podleZkratky.size() + " zkratek", podleZkratky.size() > 40);

		final List<String> kolize = new ArrayList<>();
		podleZkratky.forEach((zkratka, akceSeZkratkou) -> {
			if (akceSeZkratkou.size() > 1) {
				kolize.add(zkratka + ": " + akceSeZkratkou);
			}
		});
		assertTrue("Kolize klávesových zkratek:\n" + String.join("\n", kolize), kolize.isEmpty());
	}

	/** Shift+←/→ jsou ◀ ▶ plánovače, Ctrl+Shift+šipky skok na kandidáta; žádná z nich není u jiné akce programu. */
	@Test
	public void sipkyPlanovaceMajiSvojeZkratky() throws Exception {
		final Akce akce = new Akce();
		final int shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
		final int ctrlShift = java.awt.event.InputEvent.CTRL_DOWN_MASK | shift;
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, shift), akce.challengePredchoziAction.getValue(Action.ACCELERATOR_KEY));
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, shift), akce.challengeDalsiAction.getValue(Action.ACCELERATOR_KEY));
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, ctrlShift), akce.challengeSkokSever.getValue(Action.ACCELERATOR_KEY));
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, ctrlShift), akce.challengeSkokVychod.getValue(Action.ACCELERATOR_KEY));
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, ctrlShift), akce.challengeSkokJih.getValue(Action.ACCELERATOR_KEY));
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, ctrlShift), akce.challengeSkokZapad.getValue(Action.ACCELERATOR_KEY));
		final Set<String> planovac = new HashSet<>(Arrays.asList("challengePredchoziAction", "challengeDalsiAction", "challengeSkokSever", "challengeSkokVychod", "challengeSkokJih", "challengeSkokZapad"));
		for (final Field f : Akce.class.getFields()) {
			final Object hodnota = f.get(akce);
			if (hodnota instanceof Action && !planovac.contains(f.getName())) {
				final KeyStroke z = (KeyStroke) ((Action) hodnota).getValue(Action.ACCELERATOR_KEY);
				if (z != null) {
					assertTrue(f.getName() + " má zkratku plánovače " + z, (z.getModifiers() & shift) == 0 || z.getKeyCode() < java.awt.event.KeyEvent.VK_LEFT || z.getKeyCode() > java.awt.event.KeyEvent.VK_DOWN);
				}
			}
		}
	}

	@Test
	public void zobrazovaniKesoiduMaZkratkuF5() {
		assertEquals(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0), new Akce().kesoidyOnoffAction.getValue(Action.ACCELERATOR_KEY));
	}

	/** F5 v menu skutečně přepne zobrazování kešoidů (ne jen že akce zkratku má). */
	@Test
	public void f5PrepneZobrazovaniKesoidu() {
		final List<Boolean> nastaveni = new ArrayList<>();
		final cz.geokuk.plugins.kesoid.mvc.KesoidModel model = new cz.geokuk.plugins.kesoid.mvc.KesoidModel() {
			@Override
			public void setOnoff(final boolean onoff) {
				nastaveni.add(onoff);
			}
		};
		final cz.geokuk.plugins.kesoid.mvc.KesoidyOnoffAction akce = new cz.geokuk.plugins.kesoid.mvc.KesoidyOnoffAction();
		akce.inject(model);
		akce.setSelected(true);
		final javax.swing.JCheckBoxMenuItem polozka = new javax.swing.JCheckBoxMenuItem();
		akce.join(polozka);
		try {
			final KeyStroke f5 = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0);
			final Object klic = polozka.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(f5);
			assertTrue("F5 je navázána na položku menu", klic != null);
			polozka.getActionMap().get(klic).actionPerformed(new java.awt.event.ActionEvent(polozka, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
			assertEquals(java.util.Arrays.asList(false), nastaveni);
			polozka.getActionMap().get(klic).actionPerformed(new java.awt.event.ActionEvent(polozka, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
			assertEquals(java.util.Arrays.asList(false, true), nastaveni);
		} finally {
			// nic k uvolnění, položka menu je lehká komponenta bez okna
		}
	}

	private static void pridej(final Map<KeyStroke, List<String>> podleZkratky, final Action akce, final String jmeno) {
		final KeyStroke zkratka = (KeyStroke) akce.getValue(Action.ACCELERATOR_KEY);
		if (zkratka != null) {
			podleZkratky.computeIfAbsent(zkratka, k -> new ArrayList<>()).add(jmeno);
		}
	}
}
