package cz.geokuk.plugins.vylety;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.FilterDefinition;
import cz.geokuk.plugins.kesoid.KesoidFilterModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/** Výlet drží objekty kešoidů; po jeho načtení nebo přepnutí se musí znovu filtrovat podle prahu výletu. */
public class FiltrPoZmeneVyletuTest {

	private int filtrovani;
	private final KesoidFilterModel filtr = new KesoidFilterModel();
	private final KesoidModel model = new KesoidModel() {
		@Override
		public void spustFiltrovani() {
			filtrovani++;
		}
	};

	private void prah(final EVylet prah) {
		final FilterDefinition d = new FilterDefinition();
		d.setPrahVyletu(prah);
		filtr.setFilterDefinition(d);
		model.inject(filtr);
	}

	@Test
	public void nactenyVyletSPrahemFiltrujeZnovu() {
		prah(EVylet.NEVIM);
		model.onEvent(new VyletChangeEvent(null, null, null, null));
		Assert.assertEquals(1, filtrovani);
		prah(EVylet.ANO);
		model.onEvent(new VyletChangeEvent(null, null, null, null));
		Assert.assertEquals(2, filtrovani);
	}

	@Test
	public void bezPrahuVyletuSeNefiltruje() {
		prah(EVylet.NE);
		model.onEvent(new VyletChangeEvent(null, null, null, null));
		Assert.assertEquals(0, filtrovani);
	}

	@Test
	public void zmenaJedneKeseNefiltrujeCelek() {
		prah(EVylet.NEVIM);
		model.onEvent(new VyletChangeEvent(null, new cz.geokuk.plugins.kesoid.kind.kes.Kes(), EVylet.NE, EVylet.NEVIM));
		Assert.assertEquals(0, filtrovani);
	}
}
