package cz.geokuk.plugins.mapy.kachle;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.Prefe;

/** Zapnutí stínovaného reliéfu ČÚZK se ukládá a ohlásí událostí. */
public class ReliefCuzkNastaveniTest {

	private final List<ReliefCuzkEvent> udalosti = new ArrayList<>();
	private KachleModel model;
	private boolean puvodni;

	@Before
	public void setUp() {
		model = new KachleModel() {
			@Override
			public void fire(final Event0<?> event) {
				if (event instanceof ReliefCuzkEvent) {
					udalosti.add((ReliefCuzkEvent) event);
				}
			}
		};
		model.inject(new Prefe());
		puvodni = model.isReliefCuzk();
		model.setReliefCuzk(false);
		udalosti.clear();
	}

	@After
	public void tearDown() {
		model.setReliefCuzk(puvodni);
	}

	@Test
	public void zapnutiSeUloziAOhlasi() {
		model.setReliefCuzk(true);
		Assert.assertTrue(model.isReliefCuzk());
		Assert.assertEquals(1, udalosti.size());
		Assert.assertTrue(udalosti.get(0).isZapnuto());
		model.setReliefCuzk(true);
		Assert.assertEquals("beze změny bez události", 1, udalosti.size());
		model.setReliefCuzk(false);
		Assert.assertFalse(udalosti.get(1).isZapnuto());
	}
}
