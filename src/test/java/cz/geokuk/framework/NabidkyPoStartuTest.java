package cz.geokuk.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Nabídky, které program otevře sám, čekají na zavření modálního nebo blokujícího okna a ještě na pauzu po něm. */
public class NabidkyPoStartuTest {

	private boolean blokovano;
	private long nyni = 1_000_000;
	private final List<String> ukazane = new ArrayList<>();
	private final NabidkyPoStartu nabidky = new NabidkyPoStartu(() -> blokovano, () -> nyni, false);

	private Runnable nabidka(final String jmeno) {
		return () -> ukazane.add(jmeno);
	}

	@Test
	public void bezOtevrenehoOknaHned() {
		nabidky.pridej(nabidka("aktualizace"));
		Assert.assertEquals(Arrays.asList("aktualizace"), ukazane);
	}

	@Test
	public void cekaNaZavreniModalnihoOknaAPauzu() {
		blokovano = true;
		nabidky.pridej(nabidka("aktualizace"));
		nabidky.tik();
		nyni += 60_000;
		nabidky.tik();
		Assert.assertTrue("během optimalizace se neukáže", ukazane.isEmpty());
		blokovano = false;
		nabidky.tik();
		nyni += NabidkyPoStartu.PAUZA_MS - 1;
		nabidky.tik();
		Assert.assertTrue("pauza po zavření", ukazane.isEmpty());
		nyni += 1;
		nabidky.tik();
		Assert.assertEquals(Arrays.asList("aktualizace"), ukazane);
		Assert.assertEquals(0, nabidky.cekajicich());
	}

	@Test
	public void znovuOtevreneOknoPauzuPrerusi() {
		blokovano = true;
		nabidky.pridej(nabidka("aktualizace"));
		blokovano = false;
		nabidky.tik();
		nyni += 2000;
		blokovano = true;
		nabidky.tik();
		blokovano = false;
		nyni += 2000;
		nabidky.tik();
		nyni += 2000;
		nabidky.tik();
		Assert.assertTrue(ukazane.isEmpty());
		nyni += 1000;
		nabidky.tik();
		Assert.assertEquals(Arrays.asList("aktualizace"), ukazane);
	}

	@Test
	public void nabidkyPoJedneSPauzou() {
		nabidky.pridej(() -> {
			ukazane.add("průvodce");
			nabidky.pridej(nabidka("optimalizace"));
			// Modální okno nabídky má vlastní smyčku událostí, časovač v ní běží dál.
			nabidky.tik();
			nyni += 10_000;
			nabidky.tik();
		});
		Assert.assertEquals("druhá se neukáže uvnitř první", Arrays.asList("průvodce"), ukazane);
		nabidky.tik();
		nyni += NabidkyPoStartu.PAUZA_MS;
		nabidky.tik();
		Assert.assertEquals(Arrays.asList("průvodce", "optimalizace"), ukazane);
	}

	@Test
	public void dalsiNabidkaPoZavreniPredchoziCekaPauzu() {
		nabidky.pridej(nabidka("průvodce"));
		nabidky.pridej(nabidka("zástupce"));
		Assert.assertEquals(Arrays.asList("průvodce"), ukazane);
		nabidky.tik();
		nyni += NabidkyPoStartu.PAUZA_MS;
		nabidky.tik();
		Assert.assertEquals(Arrays.asList("průvodce", "zástupce"), ukazane);
		nyni += NabidkyPoStartu.PAUZA_MS;
		nabidky.pridej(nabidka("aktualizace"));
		Assert.assertEquals("po pauze zase hned", Arrays.asList("průvodce", "zástupce", "aktualizace"), ukazane);
	}

	@Test
	public void bezOkenNicNeblokuje() {
		Assert.assertFalse(java.awt.GraphicsEnvironment.isHeadless() && NabidkyPoStartu.jeOtevrenoBlokujiciOkno());
	}
}
