package cz.geokuk.framework;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;

import org.junit.Test;

public class MySwingWorker0Test {

	/** Text se ukazuje v Přehledu problémů, musí říct, co se stalo. */
	@Test
	public void zpravaObsahujePricinu() {
		final MySwingWorker0<Void, Void> worker = new MySwingWorker0<Void, Void>() {
			@Override
			protected Void doInBackground() {
				return null;
			}

			@Override
			protected void donex() throws Exception {
				throw new ExecutionException(new UnsatisfiedLinkError("nativní knihovna"));
			}
		};
		try {
			worker.done();
			fail();
		} catch (final RuntimeException e) {
			assertEquals("Chyba při práci na pozadí: nativní knihovna", e.getMessage());
		}
	}

	@Test
	public void bezZpravyAsponDruhChyby() {
		assertEquals("Chyba při práci na pozadí (NullPointerException)", MySwingWorker0.popis(new NullPointerException()));
	}
}
