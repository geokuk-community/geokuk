package cz.geokuk.plugins.mapy.sprava;

import java.util.EnumMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Rezim;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Bunka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;
import cz.geokuk.plugins.mapy.sprava.NabidkaRadku.Druh;
import cz.geokuk.plugins.mapy.sprava.NabidkaRadku.Okolnosti;

/** Dvě pozice tlačítek podle tabulky z návrhu: text se nemění, velikost říká, co se opravdu stáhne. */
public class NabidkaRadkuTest {

	private static final long MB = 1_000_000L;

	private static Bunka bunka(final Cast c, final StavBunky s, final long velikostMb, final long stazenoMb) {
		return new Bunka(c, s, s == StavBunky.NENI ? null : c.name(), velikostMb * MB, stazenoMb * MB, null, null);
	}

	private static Bunka chyba(final Cast c, final long velikostMb) {
		return new Bunka(c, StavBunky.CHYBA, c.name(), velikostMb * MB, 0, "Server neodpovídá", null);
	}

	private static NabidkaRadku nabidka(final Okolnosti ok, final Bunka... bunky) {
		final Map<Cast, Bunka> m = new EnumMap<>(Cast.class);
		for (final Bunka b : bunky) {
			m.put(b.getCast(), b);
		}
		return NabidkaRadku.pro(m::get, ok);
	}

	private static NabidkaRadku nabidka(final Bunka... bunky) {
		return nabidka(new Okolnosti(-1, null), bunky);
	}

	/** Mapa 606, trasy 25, vrstevnice 206, stínování 140 MB. */
	private static Bunka mapa(final StavBunky s) {
		return bunka(Cast.MAPA, s, 606, s == StavBunky.STAZENO || s == StavBunky.NOVEJSI ? 606 : 0);
	}

	private static Bunka trasy(final StavBunky s) {
		return bunka(Cast.TRASY, s, 25, 0);
	}

	private static Bunka vrstevnice(final StavBunky s) {
		return bunka(Cast.VRSTEVNICE, s, 206, 0);
	}

	private static Bunka stinovani(final StavBunky s) {
		return bunka(Cast.STINOVANI, s, 140, 0);
	}

	@Test
	public void nicNestazeno() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.KE_STAZENI), trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.KE_STAZENI), stinovani(StavBunky.KE_STAZENI));
		Assert.assertEquals("Stáhnout mapu (606 MB)", n.getMapa().getText());
		Assert.assertEquals(Druh.TLACITKO, n.getMapa().getDruh());
		Assert.assertEquals("Stáhnout mapu a vrstvy (977 MB)", n.getVrstvy().getText());
		Assert.assertEquals(Rezim.STAHNOUT, n.getVrstvy().getRezim());
		Assert.assertTrue(n.getVrstvy().getTooltip(), n.getVrstvy().getTooltip().contains("mapa 606 MB") && n.getVrstvy().getTooltip().contains("stínování 140 MB"));
		Assert.assertTrue(n.getMapa().isPovoleno() && n.getVrstvy().isPovoleno());
	}

	@Test
	public void mapaStazenaVrstvyChybi() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.STAZENO), trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.KE_STAZENI), stinovani(StavBunky.KE_STAZENI));
		Assert.assertEquals("✔ Mapa stažená", n.getMapa().getText());
		Assert.assertEquals(Druh.HOTOVO, n.getMapa().getDruh());
		Assert.assertEquals("stažená mapa: jen vrstvy a jejich velikost", "Stáhnout vrstvy (371 MB)", n.getVrstvy().getText());
		Assert.assertEquals(Rezim.STAHNOUT, n.getVrstvy().getRezim());
	}

	@Test
	public void vrstvyStazenyMapaNe() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.KE_STAZENI), trasy(StavBunky.STAZENO), vrstevnice(StavBunky.STAZENO), stinovani(StavBunky.STAZENO));
		Assert.assertEquals("Stáhnout mapu (606 MB)", n.getMapa().getText());
		Assert.assertEquals("Stáhnout mapu a vrstvy (606 MB)", n.getVrstvy().getText());
	}

	@Test
	public void viceStazeno() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.STAZENO), trasy(StavBunky.STAZENO), vrstevnice(StavBunky.STAZENO), stinovani(StavBunky.STAZENO));
		Assert.assertEquals(Druh.HOTOVO, n.getMapa().getDruh());
		Assert.assertEquals("✔ Mapa i vrstvy stažené", n.getVrstvy().getText());
		Assert.assertEquals(Druh.HOTOVO, n.getVrstvy().getDruh());
	}

	@Test
	public void novejsiMapa() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.NOVEJSI), trasy(StavBunky.STAZENO), vrstevnice(StavBunky.STAZENO), stinovani(StavBunky.STAZENO));
		Assert.assertEquals("Aktualizovat mapu (606 MB)", n.getMapa().getText());
		Assert.assertEquals("Aktualizovat mapu a vrstvy (606 MB)", n.getVrstvy().getText());
		Assert.assertEquals(Rezim.STAHNOUT_A_AKTUALIZOVAT, n.getVrstvy().getRezim());
	}

	@Test
	public void novejsiVrstvaMapaAktualni() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.STAZENO), trasy(StavBunky.NOVEJSI), vrstevnice(StavBunky.STAZENO), stinovani(StavBunky.STAZENO));
		Assert.assertEquals("✔ Mapa stažená", n.getMapa().getText());
		Assert.assertEquals("Aktualizovat mapu a vrstvy (25 MB)", n.getVrstvy().getText());
	}

	@Test
	public void staziSeObePozice() {
		final Bunka bezi = bunka(Cast.MAPA, StavBunky.STAHUJE, 606, 224);
		final NabidkaRadku n = nabidka(bezi, trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.KE_STAZENI), stinovani(StavBunky.KE_STAZENI));
		Assert.assertEquals(Druh.PRUBEH, n.getMapa().getDruh());
		Assert.assertEquals("Stahuje se 36 %", n.getMapa().getText());
		Assert.assertSame(n.getMapa(), n.getVrstvy());
	}

	@Test
	public void stahujeSeVrstvaMapyStazene() {
		final Bunka bezi = bunka(Cast.TRASY, StavBunky.STAHUJE, 25, 5);
		final NabidkaRadku n = nabidka(mapa(StavBunky.STAZENO), bezi, vrstevnice(StavBunky.KE_STAZENI), stinovani(StavBunky.KE_STAZENI));
		Assert.assertEquals("Stahuje se 20 %", n.getMapa().getText());
	}

	@Test
	public void overujeSe() {
		final Bunka bezi = new Bunka(Cast.MAPA, StavBunky.STAHUJE, "MAPA", 606 * MB, 485 * MB, null, null, true);
		final NabidkaRadku n = nabidka(bezi, trasy(StavBunky.KE_STAZENI));
		Assert.assertEquals("Ověřuji 80 %", n.getMapa().getText());
	}

	@Test
	public void prerusene() {
		final NabidkaRadku n = nabidka(bunka(Cast.MAPA, StavBunky.KE_STAZENI, 606, 206), trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.KE_STAZENI), stinovani(StavBunky.KE_STAZENI));
		Assert.assertEquals("Dokončit stažení (400 MB)", n.getMapa().getText());
		Assert.assertEquals("Stáhnout mapu a vrstvy (771 MB)", n.getVrstvy().getText());
	}

	@Test
	public void chybaStazeni() {
		final NabidkaRadku n = nabidka(chyba(Cast.MAPA, 606), trasy(StavBunky.KE_STAZENI));
		Assert.assertEquals("Zkusit znovu (606 MB)", n.getMapa().getText());
		Assert.assertEquals("Server neodpovídá", n.getMapa().getTooltip());
		Assert.assertEquals("Stáhnout mapu a vrstvy (631 MB)", n.getVrstvy().getText());
	}

	@Test
	public void bezVrstev() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.KE_STAZENI), bunka(Cast.TRASY, StavBunky.NENI, 0, 0), bunka(Cast.VRSTEVNICE, StavBunky.NENI, 0, 0),
				bunka(Cast.STINOVANI, StavBunky.NENI, 0, 0));
		Assert.assertEquals("Stáhnout mapu (606 MB)", n.getMapa().getText());
		Assert.assertEquals(Druh.NENI, n.getVrstvy().getDruh());
		Assert.assertEquals("Vrstvy zatím nejsou", n.getVrstvy().getText());
	}

	@Test
	public void nevejdeSeNaDisk() {
		final NabidkaRadku n = nabidka(new Okolnosti(900 * MB, null), mapa(StavBunky.KE_STAZENI), trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.KE_STAZENI),
				stinovani(StavBunky.KE_STAZENI));
		Assert.assertTrue("mapa se vejde", n.getMapa().isPovoleno());
		Assert.assertFalse("mapa a vrstvy se nevejdou", n.getVrstvy().isPovoleno());
		Assert.assertEquals("Na disku je volných jen 900 MB, potřeba asi 977 MB.", n.getVrstvy().getTooltip());
	}

	@Test
	public void stahujeSeJinaMapa() {
		final NabidkaRadku n = nabidka(new Okolnosti(-1, "Polsko"), mapa(StavBunky.KE_STAZENI), trasy(StavBunky.KE_STAZENI));
		Assert.assertFalse(n.getMapa().isPovoleno());
		Assert.assertFalse(n.getVrstvy().isPovoleno());
		Assert.assertEquals("Až se dokončí stahování mapy Polsko.", n.getMapa().getTooltip());
		Assert.assertEquals("text zůstává stálý", "Stáhnout mapu (606 MB)", n.getMapa().getText());
	}

	@Test
	public void chybejiciMaPrednostPredNovejsi() {
		final NabidkaRadku n = nabidka(mapa(StavBunky.NOVEJSI), trasy(StavBunky.KE_STAZENI), vrstevnice(StavBunky.STAZENO), stinovani(StavBunky.STAZENO));
		Assert.assertEquals("Stáhnout mapu a vrstvy (25 MB)", n.getVrstvy().getText());
		Assert.assertEquals(Rezim.STAHNOUT, n.getVrstvy().getRezim());
	}

	@Test
	public void staciPresneTolikMista() {
		final NabidkaRadku n = nabidka(new Okolnosti(606 * MB, null), mapa(StavBunky.KE_STAZENI), trasy(StavBunky.STAZENO));
		Assert.assertTrue(n.getMapa().isPovoleno());
	}

	/** Region stažené země: tlačítka zůstanou, ale jsou vypnutá s vysvětlením. */
	@Test
	public void regionStazeneZemeMaVypnutaTlacitka() {
		final NabidkaRadku n = NabidkaRadku.pro(c -> new Bunka(c, c == Cast.MAPA ? StavBunky.KE_STAZENI : StavBunky.NENI, "x", 100_000_000, 0, null, null), NabidkaRadku.Okolnosti.ZADNE)
				.zakazanoVCeleZemi();
		org.junit.Assert.assertEquals(NabidkaRadku.Druh.TLACITKO, n.getMapa().getDruh());
		org.junit.Assert.assertFalse(n.getMapa().isPovoleno());
		org.junit.Assert.assertEquals(NabidkaRadku.V_CELE_ZEME_NEJDE, n.getMapa().getTooltip());
	}
}
