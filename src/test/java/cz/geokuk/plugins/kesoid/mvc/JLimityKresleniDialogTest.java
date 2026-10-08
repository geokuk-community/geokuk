package cz.geokuk.plugins.kesoid.mvc;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

import org.junit.*;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Posuvníky limitů kreslení: stupnice, vazba ikony a teček, uložení až po puštění jezdce. */
public class JLimityKresleniDialogTest {

	private final MyPreferences pref = MyPreferences.current().node("test-limity-posuvniky");
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}
	};
	private JLimityKresleniDialog dialog;

	@Before
	public void pred() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		model.inject(udalost -> {
			if (dialog != null && udalost instanceof LimityKresleniEvent) {
				dialog.onEvent((LimityKresleniEvent) udalost);
			}
		});
		SwingUtilities.invokeAndWait(() -> {
			dialog = new JLimityKresleniDialog();
			dialog.inject(model);
			dialog.initAfterInject();
		});
	}

	@After
	public void po() throws Exception {
		if (dialog != null) {
			SwingUtilities.invokeAndWait(dialog::dispose);
		}
		pref.removeNode();
	}

	@Test
	public void stupnice() {
		final int[] s = JLimityKresleniDialog.STUPNICE;
		Assert.assertEquals(40, s.length);
		Assert.assertEquals(LimityKresleni.MIN_IKON, s[0]);
		Assert.assertEquals(LimityKresleni.MAX, s[s.length - 1]);
		for (int i = 1; i < s.length; i++) {
			Assert.assertTrue(s[i] > s[i - 1]);
		}
		for (final int v : new int[] { LimityKresleni.VYCHOZI_IKON, LimityKresleni.VYCHOZI_TECEK, LimityKresleni.MIN_TECEK }) {
			Assert.assertEquals(v, s[JLimityKresleniDialog.stupen(v)]);
		}
	}

	@Test
	public void vychoziHodnotyNaPosuvnicich() {
		Assert.assertEquals(LimityKresleni.VYCHOZI_IKON, JLimityKresleniDialog.STUPNICE[dialog.jIkon.getValue()]);
		Assert.assertEquals(LimityKresleni.VYCHOZI_TECEK, JLimityKresleniDialog.STUPNICE[dialog.jTecek.getValue()]);
		Assert.assertEquals("90 000", dialog.jHodnotaIkon.getText());
		Assert.assertTrue("hodnota je vidět", dialog.jHodnotaIkon.getPreferredSize().height > 0);
	}

	@Test
	public void ulozeniAzPoPusteni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			dialog.jIkon.setValueIsAdjusting(true);
			dialog.jIkon.setValue(JLimityKresleniDialog.stupen(150_000));
			Assert.assertEquals("150 000", dialog.jHodnotaIkon.getText());
			Assert.assertEquals("při tažení se neukládá", LimityKresleni.VYCHOZI, model.getLimityKresleni());
			dialog.jIkon.setValueIsAdjusting(false);
			Assert.assertEquals(LimityKresleni.of(150_000, 300_000), model.getLimityKresleni());
		});
	}

	@Test
	public void ikonyNadTeckyPosunouTecky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			dialog.jIkon.setValue(JLimityKresleniDialog.stupen(500_000));
			Assert.assertEquals(dialog.jIkon.getValue(), dialog.jTecek.getValue());
			Assert.assertEquals(LimityKresleni.of(500_000, 500_000), model.getLimityKresleni());
		});
	}

	@Test
	public void teckyPodIkonyPosunouIkonyAleNeJdouPodMinimum() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			dialog.jTecek.setValue(JLimityKresleniDialog.stupen(70_000));
			Assert.assertEquals(LimityKresleni.of(70_000, 70_000), model.getLimityKresleni());
			dialog.jTecek.setValue(0);
			Assert.assertEquals(LimityKresleni.MIN_TECEK, JLimityKresleniDialog.STUPNICE[dialog.jTecek.getValue()]);
			Assert.assertEquals(LimityKresleni.MIN_TECEK, model.getLimityKresleni().getTecek());
		});
	}

	@Test
	public void hodnotaMimoStupniciZustaneDokudSeNepohne() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			model.setLimityKresleni(LimityKresleni.of(110_000, 300_000));
			Assert.assertEquals("110 000", dialog.jHodnotaIkon.getText());
			Assert.assertEquals(LimityKresleni.of(110_000, 300_000), model.getLimityKresleni());
		});
	}

	@Test
	public void teckyNaStupniIkonMimoStupniciStahnouIkony() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			model.setLimityKresleni(LimityKresleni.of(128_000, 300_000));
			dialog.jTecek.setValue(JLimityKresleniDialog.stupen(125_000));
			Assert.assertEquals(LimityKresleni.of(125_000, 125_000), model.getLimityKresleni());
		});
	}

	@Test
	public void ctecceHlasiSkutecnyLimit() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final javax.accessibility.AccessibleValue v = dialog.jIkon.getAccessibleContext().getAccessibleValue();
			Assert.assertEquals(LimityKresleni.VYCHOZI_IKON, v.getCurrentAccessibleValue().intValue());
			Assert.assertEquals(LimityKresleni.MIN_IKON, v.getMinimumAccessibleValue().intValue());
			Assert.assertEquals(LimityKresleni.MAX, v.getMaximumAccessibleValue().intValue());
			v.setCurrentAccessibleValue(150_000);
			Assert.assertEquals(150_000, JLimityKresleniDialog.STUPNICE[dialog.jIkon.getValue()]);
		});
	}
}
