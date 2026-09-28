package cz.geokuk.core.napoveda;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.program.FConst;

public class DiagnostikaTest {

	@Test
	public void textObsahujeVerziAUdalosti() {
		Diagnostika.zaznamenej("první");
		Diagnostika.zaznamenejChybu("chyba stahování");
		for (int i = 0; i < 60; i++) {
			Diagnostika.zaznamenej("událost " + i);
		}
		final String text = Diagnostika.text();
		Assert.assertTrue(text.startsWith("Geokuk " + FConst.VERSION));
		Assert.assertTrue(text.contains("událost 59"));
		Assert.assertFalse(text.contains("první"));
		Assert.assertTrue(text.contains("chyba stahování"));
	}

	@Test
	public void domovskaSlozkaSeZkrati() {
		Assert.assertEquals("~" + File.separator + "geokuk", Diagnostika.bezDomova(new File(FConst.HOME_DIR, "geokuk")));
	}
}
