package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import javax.swing.JLabel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.Poziceq;
import cz.geokuk.core.coord.ZmenaSouradnicMysiEvent;
import cz.geokuk.core.coordinates.Wgs;

/** Ikona směru ve stavovém řádku se vyrábí jen při změně úhlu. */
public class JStatusBarSmerTest {

	@Test
	public void ikonaSeNemeniDokudSeNezmeniUhel() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final Constructor<PoziceChangedEvent> k = PoziceChangedEvent.class.getDeclaredConstructor(Poziceq.class);
		k.setAccessible(true);
		radek.onEvent(k.newInstance(new Poziceq(new Wgs(50.0, 14.0))));
		final Field f = JStatusBar.class.getDeclaredField("azimutSmer");
		f.setAccessible(true);
		final JLabel smer = (JLabel) f.get(radek);

		radek.onEvent(new ZmenaSouradnicMysiEvent(new Point(0, 0), new Wgs(50.01, 14.0).toMou(), null));
		final Object ikona = smer.getIcon();
		Assert.assertNotNull(ikona);
		for (int i = 2; i < 30; i++) {
			radek.onEvent(new ZmenaSouradnicMysiEvent(new Point(i, i), new Wgs(50.0 + i * 0.01, 14.0).toMou(), null));
			Assert.assertSame("stejný úhel, stejná ikona", ikona, smer.getIcon());
		}
		radek.onEvent(new ZmenaSouradnicMysiEvent(new Point(1, 1), new Wgs(50.0, 14.1).toMou(), null));
		Assert.assertNotSame("jiný úhel, nová ikona", ikona, smer.getIcon());
	}
}
