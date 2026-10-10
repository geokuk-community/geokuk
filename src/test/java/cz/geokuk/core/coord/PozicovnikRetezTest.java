package cz.geokuk.core.coord;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.JSlide0;
import cz.geokuk.framework.JSlidePozadi;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.JCestySlide;

/** Myš nad mapou po odstranění kříže: zoom kolečkem bere pozici myši z dalších vrstev. */
public class PozicovnikRetezTest {

	@Test
	public void poOdstraneniKrizeMysVedeDal() throws Exception {
		final Mou kriz = new Wgs(50, 14).toMou();
		final Coord soord = new Coord(12, kriz, new Dimension(800, 600), 0);
		final JPozicovnikSlide pozicovnik = new JPozicovnikSlide();
		pozicovnik.setSoord(soord);
		final JSlidePozadi pozadi = new JSlidePozadi();
		final Method addChained = JSlide0.class.getDeclaredMethod("addChained", JSlide0.class);
		addChained.setAccessible(true);
		addChained.invoke(pozicovnik, pozadi);

		pozicovnik.onEvent(new PoziceChangedEvent(new Poziceq(new Wgs(50, 14))));
		final Point uKrize = soord.transform(kriz);
		final ZmenaSouradnicMysiEvent mys = new ZmenaSouradnicMysiEvent(uKrize, kriz, null);
		pozadi.onEvent(mys);
		pozicovnik.onEvent(mys);
		Assert.assertEquals(kriz, pozicovnik.getUpravenaMys().getMou());

		pozicovnik.onEvent(new PoziceChangedEvent(new Poziceq()));
		Assert.assertNotNull("zoom kolečkem potřebuje pozici myši", pozicovnik.getUpravenaMys());
		Assert.assertEquals(kriz, pozicovnik.getUpravenaMys().getMou());
	}

	@Test
	public void poOdstraneniKrizeKlikPostaviNovyKriz() throws Exception {
		final Mou kriz = new Wgs(50, 14).toMou();
		final Coord soord = new Coord(12, kriz, new Dimension(800, 600), 0);
		final Mouable[] novaPozice = new Mouable[1];
		final PoziceModel poziceModel = new PoziceModel() {
			@Override
			public void setPozice(final Mouable mouable) {
				novaPozice[0] = mouable;
			}
		};
		final JCestySlide cesty = new JCestySlide();
		cesty.inject(new CestyModel());
		cesty.inject(poziceModel);
		final JPozicovnikSlide pozicovnik = new JPozicovnikSlide();
		pozicovnik.setSoord(soord);
		pozicovnik.inject(poziceModel);
		final JSlidePozadi pozadi = new JSlidePozadi();
		final Method addChained = JSlide0.class.getDeclaredMethod("addChained", JSlide0.class);
		addChained.setAccessible(true);
		addChained.invoke(cesty, pozicovnik);
		addChained.invoke(pozicovnik, pozadi);

		pozicovnik.onEvent(new PoziceChangedEvent(new Poziceq(new Wgs(50, 14))));
		final Point uKrize = soord.transform(kriz);
		final ZmenaSouradnicMysiEvent mys = new ZmenaSouradnicMysiEvent(uKrize, kriz, null);
		pozadi.onEvent(mys);
		pozicovnik.onEvent(mys);
		pozicovnik.onEvent(new PoziceChangedEvent(new Poziceq()));

		final MouseEvent klik = new MouseEvent(pozicovnik, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK, uKrize.x, uKrize.y, 1, false, MouseEvent.BUTTON1);
		cesty.mouseClicked(klik, null);
		Assert.assertNotNull("levý klik po odstranění kříže postaví nový kříž", novaPozice[0]);
	}
}
