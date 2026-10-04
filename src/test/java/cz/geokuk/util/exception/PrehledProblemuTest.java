package cz.geokuk.util.exception;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

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
	public void hlaseniJdeDoPrehleduSPopisem() {
		final List<String> hlaseni = new ArrayList<>();
		final BiConsumer<String, AExcId> puvodni = FError.prijemce;
		FError.prijemce = (text, excid) -> hlaseni.add(text);
		try {
			final IOException e = new IOException("Disk je plný");
			FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, "Ukládání");
			FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Načítání");
			FExceptionDumper.dump(e, EExceptionSeverity.CATCHE, "Jen do logu");
		} finally {
			FError.prijemce = puvodni;
		}
		Assert.assertEquals(Arrays.asList("Ukládání: Disk je plný", "Načítání: Disk je plný"), hlaseni);
	}

	@Test
	public void tabulkaUkazujePopis() {
		final JErrorTable tabulka = new JErrorTable();
		tabulka.addProblem("Ukládání: Disk je plný", null);
		Assert.assertEquals(1, tabulka.tableModel.getValueAt(0, 0));
		Assert.assertEquals("Ukládání: Disk je plný", tabulka.tableModel.getValueAt(0, 2));
	}

	@Test
	public void sloupceOdpovidajiObsahu() {
		final JErrorTable tabulka = new JErrorTable();
		Assert.assertEquals("Č.", tabulka.tableModel.getColumnName(0));
		Assert.assertEquals("Hlášení", tabulka.tableModel.getColumnName(1));
	}
}
