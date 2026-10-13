package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Color;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Zvýraznění keší čtvercem pod ikonou v sadě Standard. */
public class ZvyrazneniTest {

	private static final String BILA = "255,255,255";
	private static final String ZLUTA = "255,255,0";
	private static final String ORANZOVA = "255,153,51";

	private IkonBag bag;
	private Genom g;

	private void nacti(final Zvyrazneni zvyrazneni) throws Exception {
		bag = new IkonNacitacLoader().nacti(true, ASada.STANDARD, zvyrazneni);
		g = bag.getGenom();
	}

	/** Barva čtverce pod ikonou aktivní keše s danými alelami, null = bez čtverce. */
	private String ctverec(final Alela... alely) {
		final Genotyp genotyp = g.UNIVERZALNI_DRUH.genotypVychozi().with(g.ALELA_gc).with(alely);
		for (final SkloAplikant aplikant : bag.getSada().getSkloAplikanti()) {
			if (aplikant.aplikaceSkla == EAplikaceSkla.VSE) {
				for (final Vrstva vrstva : aplikant.sklo.vrstvy) {
					if ("2ctverce".equals(vrstva.nazev)) {
						final IconDef def = vrstva.locate(genotyp);
						return def == null ? null : def.idp.properties.getProperty("fill");
					}
				}
			}
		}
		throw new AssertionError("sada Standard nemá vrstvu 2ctverce");
	}

	@Test
	public void vychoziJeTerenJakoVSade() throws Exception {
		nacti(Zvyrazneni.VYCHOZI);
		Assert.assertEquals(ZLUTA, ctverec(g.ALELA_ter45));
		Assert.assertEquals(ORANZOVA, ctverec(g.ALELA_ter50));
		Assert.assertEquals(BILA, ctverec(g.ALELA_ter40));
		Assert.assertEquals(BILA, ctverec(g.ALELA_dif50));
		Assert.assertEquals("neaktivní keš", "128,128,128", ctverec(g.ALELA_ter50, g.ALELA_dsbl));
		Assert.assertEquals("výlet má přednost", "0,255,0", ctverec(g.ALELA_ter50, g.ALELA_lovime));
		Assert.assertNull("myš nad keší", ctverec(g.ALELA_ter50, g.ALELA_mouseon));
	}

	@Test
	public void bezZvyrazneni() throws Exception {
		nacti(Zvyrazneni.BEZ);
		Assert.assertEquals(BILA, ctverec(g.ALELA_ter45));
		Assert.assertEquals(BILA, ctverec(g.ALELA_ter50));
	}

	@Test
	public void obtiznost() throws Exception {
		nacti(Zvyrazneni.OBTIZNOST);
		Assert.assertEquals(ZLUTA, ctverec(g.ALELA_dif45));
		Assert.assertEquals(ORANZOVA, ctverec(g.ALELA_dif50, g.ALELA_ter50));
		Assert.assertEquals(BILA, ctverec(g.ALELA_ter50));
	}

	/** Pravidla bez druhu a stavu platí i pro neaktivní keše; když vyhovuje víc pravidel, platí první. */
	@Test
	public void vlastniPravidla() throws Exception {
		final Zvyrazneni z = new Zvyrazneni("Moje", Arrays.asList(new Zvyrazneni.Pravidlo(Color.RED, Arrays.asList("ter50:ter", "small:velikost")),
				new Zvyrazneni.Pravidlo(Color.BLUE, Arrays.asList("ter50:ter"))));
		nacti(z);
		Assert.assertEquals("255,0,0", ctverec(g.ALELA_ter50, g.ALELA_small, g.ALELA_dsbl));
		Assert.assertEquals("0,0,255", ctverec(g.ALELA_ter50, g.ALELA_dsbl));
		Assert.assertEquals("0,0,255", ctverec(g.ALELA_ter50));
		Assert.assertEquals(BILA, ctverec(g.ALELA_small));
	}

	@Test
	public void pravidloSNeznamouAlelouSeVynecha() throws Exception {
		nacti(new Zvyrazneni("Moje", Arrays.asList(new Zvyrazneni.Pravidlo(Color.RED, Arrays.asList("neni:ter")))));
		Assert.assertEquals(BILA, ctverec(g.ALELA_ter50));
	}

	@Test
	public void vestavenaPravidlaMajiExistujiciAlely() throws Exception {
		nacti(Zvyrazneni.VYCHOZI);
		for (final Zvyrazneni z : Zvyrazneni.VESTAVENA) {
			for (final Zvyrazneni.Pravidlo p : z.getPravidla()) {
				for (final String a : p.getAlely()) {
					Assert.assertTrue(a, g.locateQualAlela(a).isPresent());
				}
			}
		}
		Assert.assertEquals(g.ALELA_actv.qualName(), "~~:stav");
	}

	@Test
	public void textovaPodoba() {
		final Zvyrazneni z = new Zvyrazneni("Moje; výběr", Arrays.asList(new Zvyrazneni.Pravidlo(new Color(1, 2, 3), Arrays.asList("Traditional Cache:sym", "ter50:ter"))));
		final Zvyrazneni zpet = Zvyrazneni.zTextu(z.naText());
		Assert.assertEquals(z, zpet);
		Assert.assertEquals("Moje, výběr", zpet.getNazev());
		for (final Zvyrazneni v : Zvyrazneni.VESTAVENA) {
			Assert.assertEquals(v, Zvyrazneni.zTextu(v.naText()));
		}
		Assert.assertEquals(Zvyrazneni.BEZ, Zvyrazneni.zTextu(Zvyrazneni.BEZ.naText()));
		Assert.assertNull(Zvyrazneni.zTextu(""));
	}
}
