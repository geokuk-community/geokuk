package cz.geokuk.plugins.refbody;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.kesoid.ToastEvent;
import cz.geokuk.plugins.kesoid.ToastModel;

public class TadyJsemDomaActionTest {

	@Test
	public void akceMaNazevSVypustkou() {
		final TadyJsemDomaAction akce = new TadyJsemDomaAction();
		Assert.assertEquals("Jsem doma…", akce.getValue(javax.swing.Action.NAME));
		Assert.assertTrue(String.valueOf(akce.getValue(javax.swing.Action.SHORT_DESCRIPTION)), String.valueOf(akce.getValue(javax.swing.Action.SHORT_DESCRIPTION)).startsWith("Nastaví aktuální pozici jako domov."));
	}

	@Test
	public void dotazNazyvaDomovAUkazeDosavadni() {
		final String dotaz = TadyJsemDomaAction.textDotazu(new Wgs(49.8, 14.9), new Wgs(50.0, 14.4));
		Assert.assertEquals("Nastavit domov na " + new Wgs(49.8, 14.9) + "? Dosavadní domov: " + new Wgs(50.0, 14.4), dotaz);
		Assert.assertTrue(dotaz, dotaz.startsWith("Nastavit domov na N49°"));
	}

	@Test
	public void poUlozeniDomovaUkazeToast() {
		final List<Event0<?>> zpravy = new ArrayList<>();
		final ToastModel toast = new ToastModel();
		toast.inject((EventFirer) zpravy::add);
		final RefbodyModel model = mock(RefbodyModel.class);
		final TadyJsemDomaAction akce = new TadyJsemDomaAction();
		akce.inject(model);
		akce.inject(toast);
		akce.ulozDomov();
		verify(model).setHc(null);
		Assert.assertEquals(1, zpravy.size());
		Assert.assertEquals("Domov nastaven", ((ToastEvent) zpravy.get(0)).getText());
	}
}
