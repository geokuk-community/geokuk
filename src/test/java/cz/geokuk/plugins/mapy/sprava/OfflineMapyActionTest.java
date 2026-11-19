package cz.geokuk.plugins.mapy.sprava;

import org.junit.Assert;
import org.junit.Test;

/** Mapy > Správce offline map… požádá model o otevření panelu. */
public class OfflineMapyActionTest {

	@Test
	public void akceOtevrePanel() {
		final int[] otevreno = { 0 };
		final OfflineMapyAction akce = new OfflineMapyAction();
		akce.inject(new SpravaMapModel() {
			@Override
			public void otevriPanel() {
				otevreno[0]++;
			}
		});
		akce.actionPerformed(null);
		Assert.assertEquals(1, otevreno[0]);
		Assert.assertEquals("Správce offline map…", akce.getValue(javax.swing.Action.NAME));
	}
}
