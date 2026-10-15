package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/** Klíč cache offline mapy obsahuje verzi mapsforge; po povýšení knihovny se dlaždice vykreslí znovu. */
public class VerzeVykreslovaniTest {

	@Test
	public void verzeMapsforgeOdpovidaPom() throws Exception {
		final String pom = new String(Files.readAllBytes(new File("pom.xml").toPath()), StandardCharsets.UTF_8);
		final Matcher m = Pattern.compile("<mapsforge\\.version>([^<]+)</mapsforge\\.version>").matcher(pom);
		Assert.assertTrue(m.find());
		Assert.assertTrue("Po změně mapsforge v pom.xml změnit OfflineRenderer.VERZE_VYKRESLOVANI: " + OfflineRenderer.VERZE_VYKRESLOVANI,
				OfflineRenderer.VERZE_VYKRESLOVANI.startsWith("mapsforge " + m.group(1) + "/"));
	}
}
