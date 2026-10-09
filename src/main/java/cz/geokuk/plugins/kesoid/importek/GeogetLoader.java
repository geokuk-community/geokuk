package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.zip.*;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.ProgressModel;
import cz.geokuk.framework.Progressor;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.lang.ATimestamp;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads data from a GeoGet database.
 *
 * <p>
 * Information about GeoGet DB schema: http://geoget.ararat.cz/doku.php/user:databaze
 */
@Slf4j
public class GeogetLoader extends Nacitac0 {



	private static int PROGRESS_VAHA_CACHES = 1;
	private static int PROGRESS_VAHA_WAYPOINTS = 16;
	private static int PROGRESS_VAHA_TAGS = 18;

	private static final String[] SLOUPCE_GEOCACHE = { "x as lat", "y as lon", "name", "author", "cachetype", "cachesize", "difficulty", "terrain", "cachestatus", "gs_ownerid", "dthidden",
			"country", "state", "dtfound" };

	/** Bez nich by keše skončily bez kódu nebo na souřadnicích 0, 0. */
	private static final Map<String, List<String>> POVINNE_SLOUPCE = ImmutableMap.of("geocache", Arrays.asList("id", "x", "y"), "waypoint", Arrays.asList("id", "x", "y"));

	private static final String GEOGET_CACHES_COUNT = "SELECT count(*) FROM geocache";

	private static final String[] SLOUPCE_WAYPOINT = { "x as lat", "y as lon", "prefixid", "wpttype", "name" };

	private static final String GEOGET_WAYPOINTS_COUNT = "SELECT count(*) FROM waypoint";


	private static final ImmutableSet<String> SUPPORTED_FILE_EXTENSIONS = ImmutableSet.of("db3");
	private static final ImmutableSet<String> EXPECTED_TABLES = ImmutableSet.of("geolist", "geocache", "waypoint", "geotag", "geotagcategory", "geotagvalue");

	private static final ImmutableMap<String, String> ID_PREFIX_TO_SYM = ImmutableMap.of("GC", "Geocache", "WM", "Waymark", "MU", "Geocache");

	/** Kategorie čtených tagů. Tagy se pak čtou podle klíče kategorie, bez spojení s tabulkou kategorií na každém řádku. */
	private static final String GEOGET_TAG_CATEGORIES_QUERY = "SELECT c.key, c.value FROM geotagcategory c WHERE (c.value IN ('favorites', 'Elevation', 'Hodnoceni-Pocet', 'Hodnoceni', 'BestOf', 'Znamka') or c.value like '"
			+ PREFIX_USERDEFINOANYCH_GENU + "%')";

	@Override
	protected void nacti(final File file, final IImportBuilder builder, final Future<?> future, final ProgressModel aProgressModel) throws IOException {
		if (!GsakDbLoader.dbFileContains(file, EXPECTED_TABLES, DatabazeJinehoProgramu.CEKANI_NA_ZAMEK_MS)) {
			throw new IllegalArgumentException("Cannot load from file " + file);
		}
		try (Connection c = DatabazeJinehoProgramu.otevri(file); Statement statement = c.createStatement()) {
			DatabazeJinehoProgramu.zkontrolujSloupce(statement, file, "GeoGetu", POVINNE_SLOUPCE, Collections.emptySet());
			final int pocet = count(statement, GEOGET_CACHES_COUNT) * PROGRESS_VAHA_CACHES + count(statement, GEOGET_WAYPOINTS_COUNT) * PROGRESS_VAHA_WAYPOINTS;
			final Progressor progressor = zahajPrubeh(aProgressModel, pocet, file.toString());
			// Tagy před kešemi, keš si hodnoty přebírá už při přidání.
			// Bez tagů a popisů se keše dají zobrazit, jejich poškození nesmí připravit uživatele o celou databázi.
			Map<String, Gpxg> tagy;
			try {
				final Map<Integer, String> kategorie = kategorieTagu(statement);
				final String klice = Joiner.on(',').join(kategorie.keySet());
				progressor.setMax(pocet + (kategorie.isEmpty() ? 0 : count(statement, "SELECT count(*) FROM geotag WHERE ptrkat IN (" + klice + ")")) * PROGRESS_VAHA_TAGS);
				tagy = kategorie.isEmpty() ? new HashMap<>() : loadTags(statement, kategorie, klice, future, progressor);
			} catch (final SQLException e) {
				ohlasPoskozeni(file, "Tagy (hodnocení, favority)", e);
				tagy = new HashMap<>();
			}
			loadCaches(file, statement, builder, tagy, future, progressor);
			loadWaypoints(statement, builder, future, progressor);
			progressor.finish();
		} catch (final SQLException e) {
			throw new IOException("Unable to load from " + file, e);
		}
	}

	@Override
	protected void nacti(final ZipFile zipFile, final ZipEntry zipEntry, final IImportBuilder builder, final Future<?> f, final ProgressModel aProgressModel) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	boolean umiNacist(final File file) {
		if (SUPPORTED_FILE_EXTENSIONS.contains(Files.getFileExtension(file.getAbsolutePath().toLowerCase()))) {
			// LATER: Metodu by chtělo dát někem jinam, pro minimalizaci zásahů do cizího kódu zatím řeším takto. [2016-04-05, BRoz]
			return GsakDbLoader.dbFileContains(file, EXPECTED_TABLES);
		}
		return false;
	}

	@Override
	boolean umiNacist(final ZipEntry zipEntry) {
		// JDBC can't access zipped files.
		return false;
	}

	private static void ohlasPoskozeni(final File file, final String co, final SQLException e) throws SQLException {
		if (DatabazeJinehoProgramu.jeZamcena(e)) {
			throw e;
		}
		FExceptionDumper.dump(new IOException(co + " z databáze \"" + file + "\" nejde přečíst, keše se načtou bez nich. Databáze je asi poškozená, spusťte v GeoGetu její údržbu.", e),
				EExceptionSeverity.DISPLAY, "Poškozená databáze GeoGetu");
	}

	private int count(final Statement statement, final String countQuery) throws SQLException {
		try (ResultSet rs = statement.executeQuery(countQuery)) {
			return rs.getInt(1);
		}
	}

	private String formatDateTime(final int yyyymmddDate) {
		final int day = yyyymmddDate % 100;
		final int month = yyyymmddDate / 100 % 100;
		final int year = yyyymmddDate / 10000;
		return year + (month < 10 ? "-0" : "-") + month + (day < 10 ? "-0" : "-") + day + "T00:00:00.000";
	}

	private void loadCaches(final File file, final Statement statement, final IImportBuilder builder, final Map<String, Gpxg> tagy, final Future<?> future, final Progressor progressor) throws SQLException, IOException {
		final ATimestamp startTime = ATimestamp.now();
		final Preskocene preskocene = new Preskocene("keš");
		int citac = 0;
		final String dotaz = "SELECT geocache.id as id, " + DatabazeJinehoProgramu.vyber(statement, "geocache", SLOUPCE_GEOCACHE) + " FROM geocache";
		try (ResultSet rs = statement.executeQuery(dotaz)) {
			while (rs.next()) {
				if (future != null && future.isCancelled()) {
					return;
				}
				progressor.addProgress(PROGRESS_VAHA_CACHES);
				final String kod = rs.getString("id");
				try {
					final GpxWpt gpxWpt = new GpxWpt();
					gpxWpt.wgs = new Wgs(rs.getDouble("lat"), rs.getDouble("lon"));
					gpxWpt.name = kod;
					if (gpxWpt.name != null && gpxWpt.name.length() > 1) {
						final String prefix = gpxWpt.name.substring(0, 2);
						final String sym = ID_PREFIX_TO_SYM.get(prefix);
						if (sym != null) {
							gpxWpt.sym = sym;
						}
					}

					gpxWpt.time = formatDateTime(rs.getInt("dthidden"));

					final Groundspeak groundspeak = new Groundspeak();
					groundspeak.ownerid = rs.getInt("gs_ownerid");
					groundspeak.name = rs.getString("name");
					groundspeak.placedBy = intern(rs.getString("author"));
					groundspeak.owner = intern(groundspeak.placedBy);
					groundspeak.type = intern(rs.getString("cachetype"));
					groundspeak.container = intern(rs.getString("cachesize"));
					groundspeak.difficulty = intern(rs.getString("difficulty"));
					groundspeak.terrain = intern(rs.getString("terrain"));
					groundspeak.country = intern(rs.getString("country"));
					groundspeak.state = intern(rs.getString("state"));
					groundspeak.hintZDatabaze = HintZDatabaze.dotahovac(file, HintZDatabaze.GEOGET, kod);

					final int cacheStatus = rs.getInt("cachestatus");
					switch (cacheStatus) {
					case 0:
						groundspeak.archived = false;
						groundspeak.availaible = true;
						break;
					case 1:
						groundspeak.availaible = false;
						groundspeak.archived = false;
						break;
					case 2:
						groundspeak.archived = true;
						groundspeak.availaible = false;
						break;
					}

					gpxWpt.groundspeak = groundspeak;
					gpxWpt.desc = gpxWpt.groundspeak.name + " by " + gpxWpt.groundspeak.placedBy + " (" + gpxWpt.groundspeak.difficulty + " / " + gpxWpt.groundspeak.terrain + ")";

					gpxWpt.link.href = "http://coord.info/" + gpxWpt.name;
					gpxWpt.link.text = gpxWpt.groundspeak.name + " by " + gpxWpt.groundspeak.placedBy;

					final long dtfound = rs.getLong("dtfound");
					if (dtfound != 0) {
						gpxWpt.sym = "Geocache Found";
						gpxWpt.gpxg.found = Long.toString(dtfound);
					}

					final Gpxg tagyKese = tagy.get(gpxWpt.name);
					if (tagyKese != null) {
						prevezmiTagy(tagyKese, gpxWpt.gpxg);
					}

					builder.addGpxWpt(gpxWpt);
					citac++;
				} catch (final RuntimeException e) {
					preskocene.preskoc(kod, e);
				}
			}
		} finally {
			progressor.finish();
			preskocene.ohlas();
			logResult("Geocaches", startTime, citac);
		}
		preskocene.ohlasVetsinuKesi(citac, file, "GeoGetu");
	}

	private static void prevezmiTagy(final Gpxg z, final Gpxg kam) {
		kam.favorites = z.favorites;
		kam.elevation = z.elevation;
		kam.bestOf = z.bestOf;
		kam.hodnoceni = z.hodnoceni;
		kam.hodnoceniPocet = z.hodnoceniPocet;
		kam.znamka = z.znamka;
		kam.userTags.putAll(z.userTags);
	}

	private static Map<Integer, String> kategorieTagu(final Statement statement) throws SQLException {
		final Map<Integer, String> kategorie = new LinkedHashMap<>();
		try (ResultSet rs = statement.executeQuery(GEOGET_TAG_CATEGORIES_QUERY)) {
			while (rs.next()) {
				kategorie.put(rs.getInt(1), rs.getString(2));
			}
		}
		return kategorie;
	}

	private Map<String, Gpxg> loadTags(final Statement statement, final Map<Integer, String> kategorie, final String klice, final Future<?> future, final Progressor progressor)
			throws SQLException {
		final ATimestamp startTime = ATimestamp.now();
		final Preskocene preskocene = new Preskocene("tag");
		final Map<String, Gpxg> tagy = new HashMap<>();
		int citac = 0;
		// Unární + nechá SQLite projít geotag popořadě místo hledání po kategoriích v indexu.
		try (ResultSet rs = statement.executeQuery("SELECT t.id, t.ptrkat, v.value FROM geotag t LEFT JOIN geotagvalue v ON t.ptrvalue = v.key WHERE +t.ptrkat IN (" + klice + ")")) {
			while (rs.next()) {
				if (future != null && future.isCancelled()) {
					return tagy;
				}
				progressor.addProgress(PROGRESS_VAHA_TAGS);

				final String name = rs.getString(1);
				final String category = kategorie.get(rs.getInt(2));
				final String value = rs.getString(3);
				if (name == null || category == null || value == null) {
					continue;
				}
				final Gpxg gpxg = tagy.computeIfAbsent(name, k -> new Gpxg());

				try {
					if (category.startsWith(PREFIX_USERDEFINOANYCH_GENU)) {
						gpxg.putUserTag(category.substring(PREFIX_USERDEFINOANYCH_GENU.length()), value);
					} else {
						switch (category) {
						case "favorites":
							gpxg.favorites = Integer.parseInt(value);
							break;
						case "Elevation":
							gpxg.elevation = Integer.parseInt(value);
							break;
						case "BestOf":
							gpxg.bestOf = Integer.parseInt(value);
							break;
						case "Hodnoceni":
							if (!value.isEmpty()) {
								gpxg.hodnoceni = Integer.parseInt(value.substring(0, value.length() - 1)); // odříznout procenta
							}
							break;
						case "Hodnoceni-Pocet":
							if (!value.isEmpty()) {
								gpxg.hodnoceniPocet = Integer.parseInt(value.substring(0, value.length() - 1)); // odříznout x
							}
							break;
						case "Znamka":
							gpxg.znamka = Integer.parseInt(value);
							break;
						default:
							log.warn("Unknown tag category: {}", category);
						}
					}
				} catch (final NumberFormatException e) {
					preskocene.preskoc(name + "/" + category, e);
				}
				citac++;
			}
		} finally {
			progressor.finish();
			preskocene.ohlas();
			logResult("Tags", startTime, citac);
		}
		return tagy;
	}

	private void loadWaypoints(final Statement statement, final IImportBuilder builder, final Future<?> future, final Progressor progressor) throws SQLException {
		final ATimestamp startTime = ATimestamp.now();
		final Preskocene preskocene = new Preskocene("waypoint");
		int citac = 0;
		try (ResultSet rs = statement.executeQuery("SELECT id, " + DatabazeJinehoProgramu.vyber(statement, "waypoint", SLOUPCE_WAYPOINT) + " FROM waypoint")) {
			while (rs.next()) {
				if (future != null && future.isCancelled()) {
					return;
				}
				progressor.addProgress(PROGRESS_VAHA_WAYPOINTS);
				final String parentId = rs.getString("id");
				try {
					final GpxWpt gpxWpt = new GpxWpt();
					gpxWpt.wgs = new Wgs(rs.getDouble("lat"), rs.getDouble("lon"));
					if (parentId != null && parentId.length() > 1) {
						final String suffix = parentId.substring(2);
						gpxWpt.name = rs.getString("prefixid") + suffix;
					}
					gpxWpt.sym = rs.getString("wpttype");
					gpxWpt.desc = rs.getString("name");
					builder.addGpxWpt(gpxWpt);
					citac++;
				} catch (final RuntimeException e) {
					preskocene.preskoc(parentId, e);
				}
			}
		} finally {
			progressor.finish();
			preskocene.ohlas();
			logResult("Waypoints", startTime, citac);
		}
	}

	private void logResult(final String nazev, final ATimestamp startTime, final int pocet) {
		final double trvani = ATimestamp.now().diff(startTime);
		log.info("{} {} loaded in {} s, it is {} items/s. ", pocet, nazev, trvani / 1000.0, pocet * 1000 / trvani);
	}

	/** Kódy keší a waypointů; jméno waypointu je prefix a stejná přípona jako kód keše. */
	@Override
	Collection<String> jmenaPredem(final File file) throws SQLException {
		return DatabazeJinehoProgramu.jmena(file, "SELECT id FROM geocache UNION SELECT id FROM waypoint");
	}
}
