package cz.geokuk.core.coord;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Uchopenec;
import cz.geokuk.framework.Event0;

public class PoziceModelTest {

	private final List<Event0<?>> udalosti = new ArrayList<>();

	private final PoziceModel model = new PoziceModel() {
		@Override
		public void fire(final Event0<?> udalost) {
			udalosti.add(udalost);
		}
	};

	private static Uchopenec objekt(final int x, final int y) {
		final Mou mou = new Mou(x, y);
		return () -> mou;
	}

	@Test
	public void objektSeStanePoziciIKdyzHoVrstvaNeuchopi() {
		final Uchopenec o = objekt(1000, 2000);
		model.setPoziceNaObjekt(o);
		assertEquals(1, udalosti.size());
		final PoziceChangedEvent e = (PoziceChangedEvent) udalosti.get(0);
		assertSame("pozice je přímo ten objekt, ne jen souřadnice", o, e.poziceq.getPoziceMouable());
		assertSame(o, model.getPoziceq().getPoziceMouable());
	}

	@Test
	public void stejnyObjektNeohlasujeZnovuJinyAno() {
		final Uchopenec o = objekt(1000, 2000);
		model.setPoziceNaObjekt(o);
		model.setPoziceNaObjekt(o);
		assertEquals(1, udalosti.size());
		model.setPoziceNaObjekt(objekt(1000, 2000));
		assertEquals("jiný objekt na stejných souřadnicích je jiná pozice", 2, udalosti.size());
	}

	@Test
	public void nullPoziciZrusi() {
		model.setPoziceNaObjekt(objekt(1, 2));
		model.setPoziceNaObjekt(null);
		assertTrue(model.getPoziceq().isNoPosition());
	}
}
