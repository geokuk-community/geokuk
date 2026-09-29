package cz.geokuk.plugins.kesoid.importek;

import static cz.geokuk.plugins.kesoid.importek.ImportKesiTest.*;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;

/**
 * GPX z různých programů a ručně upravené soubory obsahují ledacos. Jedna podivná keš nesmí shodit import celého souboru a ostatní keše se
 * musí načíst.
 */
@RunWith(Parameterized.class)
public class ImportNeporadnychDatTest {

	private static final String VZOR = kes("GC9999", "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", "");

	@Parameters(name = "{0}")
	public static Collection<Object[]> pripady() {
		final List<Object[]> p = new ArrayList<>();
		// obtížnost a terén
		for (final String h : new String[] { "", "0", "6", "1,5", "x", "-1", "2.25", " 2 ", "NaN", "99999999999" }) {
			p.add(new Object[] { "obtížnost '" + h + "'", "<groundspeak:difficulty>2</groundspeak:difficulty>", "<groundspeak:difficulty>" + h + "</groundspeak:difficulty>" });
			p.add(new Object[] { "terén '" + h + "'", "<groundspeak:terrain>1.5</groundspeak:terrain>", "<groundspeak:terrain>" + h + "</groundspeak:terrain>" });
		}
		p.add(new Object[] { "bez obtížnosti", "<groundspeak:difficulty>2</groundspeak:difficulty>", "" });
		p.add(new Object[] { "bez terénu", "<groundspeak:terrain>1.5</groundspeak:terrain>", "" });
		// velikost a typ
		for (final String v : new String[] { "", "Virtual", "Not chosen", "Unknown", "Nano", "micro" }) {
			p.add(new Object[] { "velikost '" + v + "'", "<groundspeak:container>Small</groundspeak:container>", "<groundspeak:container>" + v + "</groundspeak:container>" });
		}
		for (final String t : new String[] { "", "Mystery Cache", "Lab Cache", "Geocaching HQ", "Project APE Cache", "Mega-Event Cache", "GPS Adventures Exhibit", "Neznámý typ" }) {
			p.add(new Object[] { "typ '" + t + "'", "<type>Geocache|Traditional Cache</type>", "<type>Geocache|" + t + "</type>" });
			p.add(new Object[] { "groundspeak typ '" + t + "'", "<groundspeak:type>Traditional Cache</groundspeak:type>", "<groundspeak:type>" + t + "</groundspeak:type>" });
		}
		p.add(new Object[] { "type bez svislítka", "<type>Geocache|Traditional Cache</type>", "<type>Geocache</type>" });
		p.add(new Object[] { "bez type", "<type>Geocache|Traditional Cache</type>", "" });
		p.add(new Object[] { "sym neznámý", "<sym>Geocache</sym>", "<sym>Něco</sym>" });
		p.add(new Object[] { "bez sym", "<sym>Geocache</sym>", "" });
		// souřadnice
		for (final String s : new String[] { "lat=\"50,1\"", "lat=\"\"", "lat=\"91\"", "lat=\"-0\"", "lat=\"1e1\"" }) {
			p.add(new Object[] { "souřadnice " + s, "lat=\"50.1\"", s });
		}
		p.add(new Object[] { "délka 180", "lon=\"14.4\"", "lon=\"180\"" });
		p.add(new Object[] { "délka -181", "lon=\"14.4\"", "lon=\"-181\"" });
		// stav a jména
		p.add(new Object[] { "available prázdné", "available=\"True\"", "available=\"\"" });
		p.add(new Object[] { "available malými", "available=\"True\"", "available=\"true\"" });
		p.add(new Object[] { "archived ano", "archived=\"False\"", "archived=\"yes\"" });
		p.add(new Object[] { "prázdný název", "<groundspeak:name>Keš GC9999</groundspeak:name>", "<groundspeak:name></groundspeak:name>" });
		p.add(new Object[] { "bez názvu", "<groundspeak:name>Keš GC9999</groundspeak:name>", "" });
		p.add(new Object[] { "emoji a entity v názvu", "<groundspeak:name>Keš GC9999</groundspeak:name>", "<groundspeak:name>Keš 😀 &amp; &lt;b&gt; ​</groundspeak:name>" });
		p.add(new Object[] { "owner id text", "<groundspeak:owner id=\"1\">", "<groundspeak:owner id=\"abc\">" });
		p.add(new Object[] { "owner bez id", "<groundspeak:owner id=\"1\">", "<groundspeak:owner>" });
		p.add(new Object[] { "prázdný owner", "<groundspeak:owner id=\"1\">Cizí</groundspeak:owner>", "<groundspeak:owner id=\"1\"></groundspeak:owner>" });
		p.add(new Object[] { "bez groundspeak:cache", VZOR.substring(VZOR.indexOf("<groundspeak:cache"), VZOR.indexOf("</groundspeak:cache>") + "</groundspeak:cache>".length()), "" });
		p.add(new Object[] { "kód malými", "<name>GC9999</name>", "<name>gc9999</name>" });
		p.add(new Object[] { "kód s mezerou", "<name>GC9999</name>", "<name> GC9999 </name>" });
		p.add(new Object[] { "prázdný kód", "<name>GC9999</name>", "<name></name>" });
		p.add(new Object[] { "krátký kód", "<name>GC9999</name>", "<name>GC</name>" });
		// rozšíření GeoGetu
		for (final String h : new String[] { "", "abc", "120%", "-5%", "80 %", "80.5%" }) {
			p.add(new Object[] { "hodnocení '" + h + "'", "</groundspeak:cache>", "</groundspeak:cache><gpxg:GeogetExtension xmlns:gpxg=\"https://www.geoget.cz/GpxExtensions/v2\"><gpxg:Tags>"
					+ "<gpxg:Tag Category=\"Hodnoceni\">" + h + "</gpxg:Tag><gpxg:Tag Category=\"BestOf\">" + h + "</gpxg:Tag><gpxg:Tag Category=\"favorites\">" + h + "</gpxg:Tag></gpxg:Tags></gpxg:GeogetExtension>" });
		}
		p.add(new Object[] { "čas nesmysl", "<name>GC9999</name>", "<time>včera</time><name>GC9999</name>" });
		p.add(new Object[] { "čas s časovou zónou", "<name>GC9999</name>", "<time>2012-05-01T00:00:00.000+02:00</time><name>GC9999</name>" });
		return p;
	}

	private final String co;
	private final String cim;

	public ImportNeporadnychDatTest(final String popis, final String co, final String cim) {
		this.co = co;
		this.cim = cim;
	}

	@Test
	public void ostatniKeseSeNactou() throws Exception {
		Assert.assertTrue("Vzor neobsahuje " + co, VZOR.contains(co));
		final String divna = VZOR.replace(co, cim);
		final KesBag bag = importuj(gpx(kes("GC1111", "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", ""), divna,
				kes("GC2222", "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", "")));
		final Set<String> kody = new HashSet<>();
		for (final Kesoid k : bag.getKesoidy()) {
			kody.add(k.getIdentifier());
		}
		Assert.assertTrue("Načteno jen " + kody, kody.contains("GC1111") && kody.contains("GC2222"));
	}
}
