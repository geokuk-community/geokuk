package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.Poziceq;
import cz.geokuk.core.coordinates.Wgs;

/** Dokud myš nevjede nad mapu, stavový řádek neukazuje vzdálenost od pozice. */
public class JStatusBarOdPoziceTest {

	@Test
	public void bezMysiNeniVzdalenost() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final Constructor<PoziceChangedEvent> k = PoziceChangedEvent.class.getDeclaredConstructor(Poziceq.class);
		k.setAccessible(true);
		radek.onEvent(k.newInstance(new Poziceq(new Wgs(50.08, 14.42))));
		final Field f = JStatusBar.class.getDeclaredField("odPozice");
		f.setAccessible(true);
		Assert.assertFalse(((JPanel) f.get(radek)).isVisible());
	}
}
