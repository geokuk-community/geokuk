package cz.geokuk.plugins.kesoid.importek;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;

import org.junit.Test;

import cz.geokuk.util.file.Filex;

public class MultiNacitacLoaderManagerTest {

	/** Uložená neplatná cesta ke složce cest nebo ikon nesmí zastavit načítání keší. */
	@Test
	public void neplatnaVynechanaSlozkaSePreskoci() {
		final File platna = new File("cesty").getAbsoluteFile();
		assertThat((Iterable<File>) MultiNacitacLoaderManager.vynechane(new Filex(new File("a\0b").getAbsoluteFile(), false, true), new Filex(platna, false, true)))
				.containsExactly(platna.toPath().normalize().toFile());
	}
}
