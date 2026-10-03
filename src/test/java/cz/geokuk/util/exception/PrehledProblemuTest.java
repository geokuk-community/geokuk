package cz.geokuk.util.exception;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

/** Přehled problémů ukáže, co se dělo a proč to selhalo, ne jen technickou zprávu. */
public class PrehledProblemuTest {

	@Test
	public void popisObsahujeOkolnostIPricinu() {
		final IOException e = new IOException("Chyba při čtení \"keše.gpx\"", new IllegalStateException("Neočekávaný konec souboru"));
		Assert.assertEquals("Problém při načítání keší: Chyba při čtení \"keše.gpx\": Neočekávaný konec souboru",
				FExceptionDumper.popis("Problém při načítání keší", e));
	}

	@Test
	public void obalovaZpravaSeNeopakuje() {
		final IOException pricina = new IOException("Disk je plný");
		Assert.assertEquals("Ukládání: Disk je plný", FExceptionDumper.popis("Ukládání", new RuntimeException(pricina)));
	}

	@Test
	public void bezZpravyAsponTypChyby() {
		Assert.assertEquals("NullPointerException", FExceptionDumper.popis(null, new NullPointerException()));
	}

	@Test
	public void sloupceOdpovidajiObsahu() {
		final JErrorTable tabulka = new JErrorTable();
		Assert.assertEquals("Č.", tabulka.tableModel.getColumnName(0));
		Assert.assertEquals("Hlášení", tabulka.tableModel.getColumnName(1));
	}
}
