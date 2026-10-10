package cz.geokuk.plugins.kesoid.mapicon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/** Rozklad jména souboru ikony: stejné výsledky jako dřívější výraz na všech ikonách v programu a bez zdlouhavého zpětného hledání. */
public class IconDefNacitacJmenoTest {

	/** Výraz před úpravou, jen pro porovnání (A-z omylem zahrnovalo i _ a [\]^`). */
	private static final Pattern PUVODNI = Pattern.compile(
			"([a-z0-9]+!)*([^_]*)((?:(?:_[ěščřžýáíéóúůďťňĎŇŤŠČŘŽÝÁÍÉÚŮa-zA-z -]+)|(?:_[^xyp][a-z0-9-]+))*)(_x-?[0-9]+)*(_y-?[0-9]+)*(_p[0-9])*\\.([a-z]+)");

	private static List<String> jmenaIkon() throws IOException {
		try (Stream<Path> soubory = Files.walk(Paths.get("src/main/resources/img/map"))) {
			return soubory.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).map(j -> j.startsWith("_.") ? j.substring(1) : j).distinct().sorted()
					.collect(Collectors.toList());
		}
	}

	private static String rozklad(final Matcher m) {
		if (m == null) {
			return "nevyhovuje";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 7; i++) {
			sb.append(i).append('=').append(i == 3 ? Arrays.stream(m.group(3).split("_")).filter(s -> !s.isEmpty()).collect(Collectors.toList()) : m.group(i)).append(' ');
		}
		return sb.toString();
	}

	private static Matcher puvodni(final String jmeno) {
		final Matcher m = PUVODNI.matcher(jmeno);
		return m.matches() ? m : null;
	}

	@Test
	public void vsechnyIkonyProgramuStejne() throws IOException {
		final List<String> jmena = jmenaIkon();
		Assert.assertTrue(jmena.size() > 500);
		for (final String jmeno : jmena) {
			Assert.assertEquals(jmeno, rozklad(puvodni(jmeno)), rozklad(IconDefNacitac.rozloz(jmeno)));
		}
	}

	@Test
	public void okrajovaJmenaStejne() {
		for (final String jmeno : new String[] { "a.png", "a_b.png", "a_x5.png", "a_x-5_y7_p2.png", "a_xyz_x5.png", "a_x5_abc.png", "g1!g2!traditional_typ-tradi_x-3.gif", "a_p5.png", "a.b.png",
				"a_Velký vůz-ano.png", "a_c1-2_d.png", "a_.png", "a_x.png", "a_y-.png", "a_p.png", "a.PNG", "_a.png", "x!.png", "a_b c_x1.properties" }) {
			Assert.assertEquals(jmeno, rozklad(puvodni(jmeno)), rozklad(IconDefNacitac.rozloz(jmeno)));
		}
	}

	@Test
	public void dlouheNevyhovujiciJmenoBezZdrzeni() {
		final StringBuilder sb = new StringBuilder("a");
		for (int i = 0; i < 40; i++) {
			sb.append("_ab");
		}
		final long start = System.nanoTime();
		Assert.assertNull(IconDefNacitac.rozloz(sb.append("_x5!").toString()));
		final StringBuilder grupy = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			grupy.append("a!");
		}
		Assert.assertNull(IconDefNacitac.rozloz(grupy.append('_').toString()));
		Assert.assertTrue((System.nanoTime() - start) / 1_000_000 < 500);
	}
}
