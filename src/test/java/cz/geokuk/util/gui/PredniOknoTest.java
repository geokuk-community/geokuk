package cz.geokuk.util.gui;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PredniOknoTest {

	private static final class Zaznam implements PredniOkno.Povrch {
		final List<String> kroky = new ArrayList<>();
		boolean ikona;
		RuntimeException selhaniNahore;
		RuntimeException selhani;

		@Override
		public boolean jeIkonizovane() {
			return ikona;
		}

		@Override
		public void obnov() {
			kroky.add("obnov");
		}

		@Override
		public void nastavVzdyNahore(final boolean nahore) {
			kroky.add("nahore=" + nahore);
			if (nahore && selhaniNahore != null) {
				throw selhaniNahore;
			}
		}

		@Override
		public void doPopredi() {
			kroky.add("popredi");
			if (selhani != null) {
				throw selhani;
			}
		}

		@Override
		public void pozadejFocus() {
			kroky.add("focus");
		}
	}

	@Test
	public void oknoSeDostaneDoPopredi() {
		final Zaznam z = new Zaznam();
		PredniOkno.predAktivuj(z);
		assertEquals(Arrays.asList("nahore=true", "popredi", "focus", "nahore=false"), z.kroky);
	}

	@Test
	public void ikonizovaneOknoSeNejdrivObnovi() {
		final Zaznam z = new Zaznam();
		z.ikona = true;
		PredniOkno.predAktivuj(z);
		assertEquals(Arrays.asList("obnov", "nahore=true", "popredi", "focus", "nahore=false"), z.kroky);
	}

	@Test
	public void vzdyNahoreSeVratiIKdyzPrenesenSelze() {
		final Zaznam z = new Zaznam();
		z.selhani = new IllegalStateException("selhalo");
		try {
			PredniOkno.predAktivuj(z);
			fail("výjimka se nemá spolknout");
		} catch (final IllegalStateException e) {
			assertEquals("selhalo", e.getMessage());
		}
		assertEquals("okno nesmí zůstat napořád nahoře", "nahore=false", z.kroky.get(z.kroky.size() - 1));
	}

	@Test
	public void vzdyNahoreSeVratiIKdyzJehoNastaveniSelze() {
		final Zaznam z = new Zaznam();
		z.selhaniNahore = new IllegalStateException("nahoře");
		try {
			PredniOkno.predAktivuj(z);
			fail("výjimka se nemá spolknout");
		} catch (final IllegalStateException e) {
			assertEquals("nahoře", e.getMessage());
		}
		assertEquals(Arrays.asList("nahore=true", "nahore=false"), z.kroky);
	}
}
