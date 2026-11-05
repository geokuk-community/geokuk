package cz.geokuk.plugins.challenge;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Akce menu Kešoidy > Challenge: stav z modelu, zkratky F4 a Shift+F4. */
public class ChallengeAkceTest {

	private ChallengeModel model;
	private final ChallengeZapnoutAction zapnout = new ChallengeZapnoutAction();
	private final ChallengeDalsiAction dalsi = new ChallengeDalsiAction();
	private final ChallengePredchoziAction predchozi = new ChallengePredchoziAction();
	private final ChallengeJenKandidatiAction kandidati = new ChallengeJenKandidatiAction();

	@Before
	public void setUp() {
		model = new ChallengeModel();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				final ChallengeZmenenyEvent e = (ChallengeZmenenyEvent) event;
				zapnout.onEvent(e);
				dalsi.onEvent(e);
				predchozi.onEvent(e);
				kandidati.onEvent(e);
			}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		zapnout.inject(model);
		dalsi.inject(model);
		predchozi.inject(model);
		kandidati.inject(model);
	}

	@Test
	public void zkratkyJsouF4AShiftF4() {
		Assert.assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), dalsi.getValue(Action.ACCELERATOR_KEY));
		Assert.assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK), predchozi.getValue(Action.ACCELERATOR_KEY));
		Assert.assertNull(zapnout.getValue(Action.ACCELERATOR_KEY));
		Assert.assertNull(kandidati.getValue(Action.ACCELERATOR_KEY));
	}

	@Test
	public void zapnoutPrepinaModelAStavSeVraciZModelu() {
		Assert.assertFalse(zapnout.isSelected());
		zapnout.onSlectedChange(true);
		Assert.assertTrue(model.jeZapnuto());
		Assert.assertTrue(zapnout.isSelected());
		model.setZapnuto(false);
		Assert.assertFalse(zapnout.isSelected());
	}

	@Test
	public void dalsiAPredchoziAFiltrJsouDostupneJenSViditelnymPruhem() {
		Assert.assertFalse(dalsi.isEnabled());
		Assert.assertFalse(predchozi.isEnabled());
		Assert.assertFalse(kandidati.isEnabled());
		model.setZapnuto(true);
		Assert.assertFalse("zapnuto, ale bez aktivní challenge", dalsi.isEnabled());
		model.aktivuj(Sablony.cesko360());
		Assert.assertTrue(dalsi.isEnabled());
		Assert.assertTrue(predchozi.isEnabled());
		Assert.assertTrue(kandidati.isEnabled());
		model.setZapnuto(false);
		Assert.assertFalse(dalsi.isEnabled());
		Assert.assertFalse(kandidati.isEnabled());
	}

	@Test
	public void jenKandidatiZapneFiltrPrvniAktivniChallengeAVypneHo() {
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		Assert.assertEquals(Sablony.cesko360(), model.hlavni());
		kandidati.onSlectedChange(true);
		Assert.assertEquals(Sablony.cesko360(), model.jenKandidatiPro());
		Assert.assertTrue(kandidati.isSelected());
		kandidati.onSlectedChange(false);
		Assert.assertNull(model.jenKandidatiPro());
		Assert.assertFalse(kandidati.isSelected());
	}

	@Test
	public void jenKandidatiBezAktivniChallengeNicNezapne() {
		kandidati.onSlectedChange(true);
		Assert.assertNull(model.jenKandidatiPro());
		Assert.assertFalse(kandidati.isSelected());
	}

	@Test
	public void hlavniJePrvniAktivni() {
		Assert.assertNull(model.hlavni());
		model.aktivuj(Sablony.cesko360());
		Assert.assertEquals(Sablony.cesko360(), model.hlavni());
		model.deaktivuj(Sablony.cesko360());
		Assert.assertNull(model.hlavni());
	}
}
