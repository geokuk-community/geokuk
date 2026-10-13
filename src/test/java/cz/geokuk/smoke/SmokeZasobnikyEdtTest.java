package cz.geokuk.smoke;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Při pomalé odezvě EDT vypíše Smoke zásobníky z logu programu. */
public class SmokeZasobnikyEdtTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void vyberZasobnikyZLogu() throws Exception {
		final File log = tmp.newFile("zvenku.log");
		Files.write(log.toPath(), ("12:00 INFO start\n" + "12:01 WARN EDT stojí 1500 ms, zásobník:\n" + "\tat a.B.c(B.java:1)\n" + "\tat a.B.d(B.java:2)\n"
				+ "12:02 INFO dál\n" + "\tat nepatri.Sem(X.java:1)\n" + "12:03 INFO Zásek EDT 4100 ms: InvocationEvent\n").getBytes(StandardCharsets.UTF_8));
		final String vypis = SmokeIT.zasobnikyEdt(log);
		Assert.assertTrue(vypis, vypis.contains("EDT stojí 1500 ms"));
		Assert.assertTrue(vypis, vypis.contains("a.B.d(B.java:2)"));
		Assert.assertTrue(vypis, vypis.contains("Zásek EDT 4100 ms"));
		Assert.assertFalse(vypis, vypis.contains("nepatri.Sem"));
		Assert.assertFalse(vypis, vypis.contains("INFO start"));
	}

	@Test
	public void bezLogu() throws Exception {
		Assert.assertTrue(SmokeIT.zasobnikyEdt(new File(tmp.getRoot(), "neni.log")).contains("chybí"));
	}
}
