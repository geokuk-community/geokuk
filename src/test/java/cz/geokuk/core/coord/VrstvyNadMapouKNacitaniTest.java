package cz.geokuk.core.coord;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.cesty.JCestySlide;
import cz.geokuk.plugins.kesoid.JKesoidySlide;
import cz.geokuk.plugins.kesoidkruhy.JZvyraznovaciKruhySlide;
import cz.geokuk.plugins.kesoidpopisky.JPopiskySlide;
import cz.geokuk.plugins.mrizky.JMrizkaDd;

/** Které vrstvy se při prvním načítání mapy nekreslí: body, popisky, kruhy a cesty ano, ostatní (třeba mřížka) ne. */
public class VrstvyNadMapouKNacitaniTest {

	@Test
	public void vrstvyNadMapouCekajiNaNacteniMapy() {
		Assert.assertTrue("body keší a waypointů", ((JSingleSlide0) new JKesoidySlide(true)).jeNadMapouKNacitani());
		Assert.assertTrue("popisky", ((JSingleSlide0) new JPopiskySlide()).jeNadMapouKNacitani());
		Assert.assertTrue("zvýrazňovací kruhy", ((JSingleSlide0) new JZvyraznovaciKruhySlide()).jeNadMapouKNacitani());
		Assert.assertTrue("cesty", ((JSingleSlide0) new JCestySlide()).jeNadMapouKNacitani());
	}

	@Test
	public void ostatniVrstvyCekajiNe() {
		Assert.assertFalse(((JSingleSlide0) new JMrizkaDd()).jeNadMapouKNacitani());
	}
}
