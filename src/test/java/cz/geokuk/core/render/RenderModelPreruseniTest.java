package cz.geokuk.core.render;

import org.junit.Assert;
import org.junit.Test;

public class RenderModelPreruseniTest {

	/** Bez výsledku po nedostatku paměti už je chyba ohlášená, „přerušeno uživatelem“ patří jen k tlačítku Přerušit. */
	@Test
	public void preruseniSeHlasiJenPoPreruseniUzivatelem() {
		final RenderModel model = new RenderModel();
		model.inject(event -> {});
		Assert.assertFalse(model.hlasitPreruseni());
		model.prerusRendrovani();
		Assert.assertTrue(model.hlasitPreruseni());
		Assert.assertFalse("hlásí se jednou", model.hlasitPreruseni());
	}
}
