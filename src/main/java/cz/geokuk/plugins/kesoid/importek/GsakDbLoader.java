package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.*;
import cz.geokuk.plugins.kesoid.kind.kes.EKesType;
import cz.geokuk.plugins.kesoid.mvc.GsakParametryNacitani;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.lang.ATimestamp;
import cz.geokuk.util.lang.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads data from a GSAK database.
 * <p>
 * Information about GSAK DB schema - reverse engineered.
 *
 * @since ISSUE#48 [2016-04-01, Bohusz]
 */
@Slf4j
public class GsakDbLoader extends Nacitac0 {

	private static int PROGRESS_VAHA_CACHES = 1;
	private static int PROGRESS_VAHA_WAYPOINTS = 16;
	private static int PROGRESS_VAHA_TAGS = 18;

	@SuppressWarnings("unused")
	private static final String ISO_DATE_FORMAT_TEMPLATE = "%d-%02d-%02dT00:00:00.000";
	private static final Pattern ISO_TIME = Pattern.compile("[0-2]?\\d:[0-5]\\d");

	private static final ImmutableSet<String> SUPPORTED_FILE_EXTENSIONS = ImmutableSet.of("db3");
	private static final ImmutableSet<String> EXPECTED_TABLES = ImmutableSet.of("CacheMemo", "Caches");

	private final Supplier<GsakParametryNacitani> parametryNačítání;

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////  Public  /////
	public GsakDbLoader(final Supplier<GsakParametryNacitani> aGsakParametryNacitani) {
		parametryNačítání = aGsakParametryNacitani;
	}

	@Override
	protected void nacti(final File aDbFile, final IImportBuilder aBuilder, final Future<?> aFuture, final ProgressModel aProgressModel) throws IOException {
		try (GsakDao dao = new GsakDao(aDbFile)) {
			if (!dao.schemaMatches()) {
				throw new IllegalArgumentException("DB schema doesn't match, cannot load from file " + aDbFile);
			}
			final int pocet = dao.cacheCount() * PROGRESS_VAHA_CACHES + dao.waypointCount() * PROGRESS_VAHA_WAYPOINTS + dao.tagCount() * PROGRESS_VAHA_TAGS;
			final Progressor progressor = aProgressModel.start(pocet, "Loading " + aDbFile.toString());
			// Vlastní hodnoty před kešemi, keš si je přebírá už při přidání.
			// Bez nich se keše dají zobrazit, jejich poškození nesmí připravit uživatele o celou databázi.
			Map<String, Map<String, String>> vlastniHodnoty;
			try {
				vlastniHodnoty = loadCustomValues(dao, aFuture, progressor);
			} catch (final SQLException e) {
				if (DatabazeJinehoProgramu.jeZamcena(e)) {
					throw e;
				}
				FExceptionDumper.dump(new IOException("Vlastní hodnoty z databáze \"" + aDbFile + "\" nejde přečíst, keše se načtou bez nich. Databáze je asi poškozená, spusťte v GSAKu její údržbu.", e),
						EExceptionSeverity.DISPLAY, "Poškozená databáze GSAKu");
				vlastniHodnoty = new HashMap<>();
			}
			loadCaches(aDbFile, dao, aBuilder, vlastniHodnoty, aFuture, progressor);
			loadWaypoints(dao, aBuilder, aFuture, progressor);
			progressor.finish();
		} catch (final SQLException e) {
			if (String.valueOf(e.getMessage()).contains("no such collation sequence:")) {
				// Modální okno by tu zastavilo načítání všech dat, než ho uživatel zavře.
				log.warn("Nestandardní řazení v {}", aDbFile, e);
				throw new IOException("Databáze GSAKu \"" + aDbFile + "\" obsahuje nestandardní řazení, a proto se nenačetla. Mělo by stačit ji v GSAKu na chvíli vybrat jako aktivní, GSAK ji opraví.");
			}
			throw new IOException("Unable to load from " + aDbFile, e);
		}
	}

	@Override
	protected void nacti(final ZipFile zipFile, final ZipEntry zipEntry, final IImportBuilder builder, final Future<?> f, final ProgressModel aProgressModel) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	boolean umiNacist(final File file) {
		if (SUPPORTED_FILE_EXTENSIONS.contains(Files.getFileExtension(file.getAbsolutePath().toLowerCase()))) {
			return dbFileContains(file, EXPECTED_TABLES);
		}
		return false;
	}

	@Override
	boolean umiNacist(final ZipEntry zipEntry) {
		// JDBC can't access zipped files.
		return false;
	}

	//------------------------------------------------------------------------------------------------------  implementation  -----

	private void loadCaches(final File aDbFile, final GsakDao aDao, final IImportBuilder aBuilder, final Map<String, Map<String, String>> aVlastniHodnoty, final Future<?> aFuture, final Progressor aProgressor)
			throws SQLException, IOException {
		final ATimestamp startTime = ATimestamp.now();
		final Preskocene preskocene = new Preskocene("keš");
		final Counter čítač = new Counter();

		aDao.forEachCache(parametryNačítání.get().getCasNalezu(), record -> {
			if (isCancelled(aFuture)) {
				return false;
			}
			aProgressor.addProgress(PROGRESS_VAHA_CACHES);
			try {
				//
				// Příprava dat:
				final EGsakCacheType cacheType = EGsakCacheType.fromGsakCode(record.CacheType);
				final Wgs coordinates = record.Latitude == 0.0 && record.Longitude == 0.0 ? null : new Wgs(record.Latitude, record.Longitude);
				final Wgs original = record.LatOriginal == 0.0 && record.LonOriginal == 0.0 ? null : new Wgs(record.LatOriginal, record.LonOriginal);
				//
				// Plnění dat:
				final GpxWpt cache = new GpxWpt();
				{
					cache.name = record.Code;
					cache.sym = cacheType.getGeokukSymbol();
					cache.wgs = isCorrected(original, coordinates) ? original : coordinates;
					cache.time = record.PlacedDate;

					final Groundspeak groundspeak = new Groundspeak();
					{
						groundspeak.ownerid = record.OwnerId;
						groundspeak.name = record.Name;
						groundspeak.owner = intern(record.OwnerName);
						groundspeak.placedBy = intern(record.PlacedBy);
						groundspeak.type = cacheType.toGroundspeakName();
						groundspeak.container = intern(record.Container);
						groundspeak.difficulty = intern(record.Difficulty);
						groundspeak.terrain = intern(record.Terrain);
						groundspeak.country = intern(record.Country);
						groundspeak.state = intern(record.State);
						groundspeak.hintZDatabaze = HintZDatabaze.dotahovac(aDbFile, HintZDatabaze.GSAK, record.Code);
						groundspeak.archived = record.Archived;
						groundspeak.availaible = !record.TempDisabled;
					}
					cache.groundspeak = groundspeak;
					cache.desc = String.format("%s by %s (%s / %s)", cache.groundspeak.name, cache.groundspeak.placedBy, cache.groundspeak.difficulty, cache.groundspeak.terrain);
					cache.link.href = "http://coord.info/" + cache.name;
					cache.link.text = String.format("%s by %s", cache.groundspeak.name, cache.groundspeak.placedBy);

					if (!StringUtils.isBlank(record.FoundByMeDate)) {
						cache.sym = "Geocache Found";
						cache.gpxg.found = record.FoundByMeDate;
						final String time = _getFoundByMeTimeField(record.values);
						if (!StringUtils.isBlank(time)) {
							cache.gpxg.found += "T" + time;
						}
					}
					cache.gpxg.favorites = record.FavPoints;
					cache.gpxg.elevation = record.Elevation;
					cache.gpxg.czkraj = record.State;
					cache.gpxg.czokres = record.County;
					//                gpxWpt.gpxg.bestOf = ???;
					//                gpxWpt.gpxg.hodnoceni = ???;
					//                gpxWpt.gpxg.hodnoceniPocet = ???;
					//                gpxWpt.gpxg.znamka = ???;
				}
				final Map<String, String> vlastni = aVlastniHodnoty.get(record.Code);
				if (vlastni != null) {
					prevezmiVlastniHodnoty(vlastni, cache);
				}
				aBuilder.addGpxWpt(cache);
				//
				// Corrected Coordinates:
				if (isCorrected(original, coordinates)) {
					final GpxWpt correctedCoordinateWaypoint = new GpxWpt();
					{
						correctedCoordinateWaypoint.wgs = coordinates;
						correctedCoordinateWaypoint.name = "##" + record.Code.substring(2);
						correctedCoordinateWaypoint.sym = "Final Location";
						correctedCoordinateWaypoint.desc = "Final (" + record.Name + ")";
					}
					aBuilder.addGpxWpt(correctedCoordinateWaypoint);
				}
				//
				čítač.inc();
			} catch (final RuntimeException e) {
				preskocene.preskoc(record.Code, e);
			}
			return true;
		});

		aProgressor.finish();
		preskocene.ohlas();
		logResult("Geocaches", startTime, čítač.getCount());
	}

	private void loadWaypoints(final GsakDao aDao, final IImportBuilder aBuilder, final Future<?> aFuture, final Progressor aProgressor) throws SQLException, IOException {
		final ATimestamp startTime = ATimestamp.now();
		final Preskocene preskocene = new Preskocene("waypoint");
		final Counter čítač = new Counter();

		aDao.forEachWaypoint(record -> {
			if (isCancelled(aFuture)) {
				return false;
			}
			aProgressor.addProgress(PROGRESS_VAHA_WAYPOINTS);
			try {
				final GpxWpt childWaypoint = new GpxWpt();
				{
					childWaypoint.wgs = new Wgs(record.cLat, record.cLon);
					childWaypoint.name = record.cCode;
					childWaypoint.sym = record.cType;
					childWaypoint.desc = record.cName;
				}
				aBuilder.addGpxWpt(childWaypoint);
				čítač.inc();
			} catch (final RuntimeException e) {
				preskocene.preskoc(record.cCode, e);
			}
			return true;
		});

		aProgressor.finish();
		preskocene.ohlas();
		logResult("Waypoints", startTime, čítač.getCount());
	}

	private Map<String, Map<String, String>> loadCustomValues(final GsakDao aDao, final Future<?> aFuture, final Progressor aProgressor) throws SQLException, IOException {
		final ATimestamp startTime = ATimestamp.now();
		final Counter čítač = new Counter();
		final Map<String, Map<String, String>> vysledek = new HashMap<>();

		aDao.forEachCustomValue(record -> {
			if (isCancelled(aFuture)) {
				return false;
			}
			aProgressor.addProgress(PROGRESS_VAHA_TAGS);
			vysledek.put(record.get(GsakDao.CACHE_CODE_KEY), record);
			čítač.inc();
			return true;
		});

		aProgressor.finish();
		logResult("Custom Values", startTime, čítač.getCount());
		return vysledek;
	}

	private void prevezmiVlastniHodnoty(final Map<String, String> record, final GpxWpt cache) {
		record.entrySet().stream().forEach(e -> cache.gpxg.putUserTag(e.getKey(), Objects.toString(e.getValue())));
		//
		if (!StringUtils.isBlank(cache.gpxg.found) && !cache.gpxg.found.contains("T")) {
			final String time = _getFoundByMeTimeField(record);
			if (!StringUtils.isBlank(time)) {
				cache.gpxg.found += "T" + time;
			}
		}
	}

	//-------------------------------------------------------------------------------------------------------------  utility  -----

	private static boolean isCancelled(final Future<?> aFuture) {
		return aFuture != null && aFuture.isCancelled();
	}

	private boolean isCorrected(final Wgs aOriginalCoordinates, final Wgs aCurrentCoordinates) {
		if (aOriginalCoordinates == null || aCurrentCoordinates == null) {
			return false;
		}
		return !aCurrentCoordinates.equals(aOriginalCoordinates);
	}

	private String _getFoundByMeTimeField(final Map<String, ?> values) {
		for (final String name : parametryNačítání.get().getCasNalezu()) {
			final Object value = values.get(name);
			final Matcher matcher = ISO_TIME.matcher(Objects.toString(value, ""));
			if (matcher.find()) {
				return matcher.group(0);
			}
		}
		return null;
	}

	private void logResult(final String nazev, final ATimestamp startTime, final int pocet) {
		final double trvani = ATimestamp.now().diff(startTime);
		log.info("{} {} loaded in {} s, it is {} items/s. ", pocet, nazev, trvani / 1000.0, pocet * 1000 / trvani);
	}

	// Friendly, aby mohlo být použito i v GeogetLoader.
	static boolean dbFileContains(final File aFile, final Set<String> aExpectedTables) {
		try (GsakDao dao = new GsakDao(aFile)) {
			return dao.containsTables(aExpectedTables);
		} catch (IOException | SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private static class Counter {
		private int iCount;

		public int inc() {
			return ++iCount;
		}

		public int getCount() {
			return iCount;
		}
	}

	private static final EKesType OTHER = EKesType.LOCATIONLESS_REVERSE;
	private static final EKesType EKESTYPE_WAYMARK = OTHER;
	private static final EKesType EKESTYPE_MAZE_EXHIBIT = OTHER;
	private static final EKesType EKESTYPE_LAB_CACHE = OTHER;
	private static final EKesType EKESTYPE_BLOCK_PARTY = OTHER;
	private static final EKesType EKESTYPE_OTHER = OTHER;
	private static final EKesType EKESTYPE_GIGA_EVENT = OTHER;
	private static final EKesType EKESTYPE_GROUNDSPEAK_HQ = OTHER;
	private static final EKesType EKESTYPE_BENCHMARK = OTHER;
	private static final EKesType EKESTYPE_LF_EVENT = OTHER;
	private static final EKesType EKESTYPE_LF_CELEBRATION = OTHER;
	private static final EKesType EKESTYPE_PROJECT_APE = OTHER;

	private enum EWaypointType {
		GEOCACHE("Geocache"), WAYMARK("Waymark"), MUNZEE("Munzee"), FLAGSTACK("Flagstack"), OTHER(null);
		private String geokukSymbol;

		private EWaypointType(final String aGeokukSymbol) {
			geokukSymbol = aGeokukSymbol;
		}

		public String getGeokukSymbol() {
			return geokukSymbol;
		};
	}

	private enum EGsakCacheType {
		// @formatter:off
		/*
		   <groundspeak:type>Traditional Cache</groundspeak:type>
		   <groundspeak:type>Multi-cache</groundspeak:type>
		   <groundspeak:type>Letterbox Hybrid</groundspeak:type>
		   <groundspeak:type>Cache In Trash Out Event</groundspeak:type>
		   <groundspeak:type>Event Cache</groundspeak:type>
		   <groundspeak:type>Locationless (Reverse) Cache</groundspeak:type>
		   <groundspeak:type>Virtual Cache</groundspeak:type>
		   <groundspeak:type>Webcam Cache</groundspeak:type>
		   <groundspeak:type>Unknown Cache</groundspeak:type>
		   <groundspeak:type>Benchmark</groundspeak:type>
		   <groundspeak:type>Other</groundspeak:type>
		   <groundspeak:type>Earthcache</groundspeak:type>
		   <groundspeak:type>Project APE Cache</groundspeak:type>
		   <groundspeak:type>Mega-Event Cache</groundspeak:type>
		   <groundspeak:type>GPS Adventures Exhibit</groundspeak:type>
		   <groundspeak:type>Wherigo Cache</groundspeak:type>
		   <groundspeak:type>Waymark</groundspeak:type>
		   <groundspeak:type>Lost and Found Event Caches</groundspeak:type>
		   <groundspeak:type>Groundspeak HQ Cache</groundspeak:type>
		   <groundspeak:type>Groundspeak Lost and Found Celebration</groundspeak:type>
		   <groundspeak:type>Groundspeak Block Party</groundspeak:type>
		   <groundspeak:type>Giga-Event Cache</groundspeak:type>
		   <groundspeak:type>Lab Cache</groundspeak:type>
		 */
		A(EKESTYPE_PROJECT_APE,					"Project APE Cache",                     	EWaypointType.GEOCACHE),
		B(EKesType.LETTERBOX_HYBRID,			"Letterbox Hybrid",                     	EWaypointType.GEOCACHE),
		C(EKesType.CACHE_IN_TRASH_OUT_EVENT,	"Cache In Trash Out Event",                 EWaypointType.GEOCACHE),
		D(EKESTYPE_LF_CELEBRATION,				"Groundspeak Lost and Found Celebration",	EWaypointType.GEOCACHE),
		E(EKesType.EVENT,						"Event Cache",                     			EWaypointType.GEOCACHE),
		F(EKESTYPE_LF_EVENT,					"Lost and Found Event Caches",              EWaypointType.GEOCACHE),
		G(EKESTYPE_BENCHMARK,					"Benchmark",                       	        EWaypointType.GEOCACHE),
		H(EKESTYPE_GROUNDSPEAK_HQ,				"Groundspeak HQ Cache",                     EWaypointType.GEOCACHE),
		I(EKesType.WHERIGO,						"Wherigo Cache",                            EWaypointType.GEOCACHE),
		J(EKESTYPE_GIGA_EVENT,					"Giga-Event Cache",                      	EWaypointType.GEOCACHE),
		L(EKesType.LOCATIONLESS_REVERSE,		"Locationless (Reverse) Cache",             EWaypointType.GEOCACHE),
		M(EKesType.MULTI,						"Multi-cache",                              EWaypointType.GEOCACHE),
		O(EKESTYPE_OTHER,						"Other",                                    EWaypointType.OTHER),
		P(EKESTYPE_BLOCK_PARTY,					"Groundspeak Block Party",                  EWaypointType.GEOCACHE),
		Q(EKESTYPE_LAB_CACHE,					"Lab Cache",                                EWaypointType.GEOCACHE),
		R(EKesType.EARTHCACHE,					"Earthcache",                               EWaypointType.GEOCACHE),
		T(EKesType.TRADITIONAL,					"Traditional Cache",                        EWaypointType.GEOCACHE),
		U(EKesType.UNKNOWN,						"Unknown Cache",                            EWaypointType.GEOCACHE),
		V(EKesType.VIRTUAL,						"Virtual Cache",                            EWaypointType.GEOCACHE),
		W(EKesType.WEBCAM,						"Webcam Cache",                             EWaypointType.GEOCACHE),
		X(EKESTYPE_MAZE_EXHIBIT,				"GPS Adventures Exhibit",                   EWaypointType.GEOCACHE),
		Y(EKESTYPE_WAYMARK,						"Waymark",                                  EWaypointType.WAYMARK),
		Z(EKesType.MEGA_EVENT,					"Mega-Event Cache",                         EWaypointType.GEOCACHE),
		;
		// @formatter:off

		private static final EGsakCacheType UNRECOGNIZED = O;

		private final EKesType iGeokukCacheType;
		private final String iGroundspeakName;
		private EWaypointType iWaypointType;

		private EGsakCacheType(final EKesType aGeokukCacheType, final String aGroundspeakName, final EWaypointType aWaypointType) {
			iGeokukCacheType = aGeokukCacheType;
			iGroundspeakName = aGroundspeakName;
			iWaypointType=aWaypointType;
		}
		public static EGsakCacheType fromGsakCode(final String aGsakCode) {
			return Arrays.stream(EGsakCacheType.values())//
					.filter(v -> v.name().equalsIgnoreCase(aGsakCode))//
					.findFirst()
					.orElse(UNRECOGNIZED)
					;
		}
		@SuppressWarnings("unused")
		public EKesType toGeokukType() {
			return iGeokukCacheType;
		}
		public String toGroundspeakName() {
			return iGroundspeakName;
		}
		public String getGeokukSymbol() {
			return iWaypointType.getGeokukSymbol();
		};
	}

	//---------------------------------------------------------------------------------------------------------  data access  -----

	private static class GsakDao implements Closeable {
		public static final String CACHE_CODE_KEY = "Code";

		private static final String CACHE_COUNT = "SELECT COUNT(*) FROM Caches";
		private static final String WAYPOINT_COUNT = "SELECT COUNT(*) FROM Waypoints";
		private static final String TAG_COUNT = "SELECT COUNT(*) FROM Caches";
		private static final String SELECT_CACHES = "SELECT * FROM Caches";
		private static final String SELECT_WAYPOINTS = "SELECT * FROM Waypoints";
		private static final String SELECT_CUSTOMVALUES = "SELECT * FROM Custom";

		private final Connection iConnection;
		private final Statement iStatement;

		public GsakDao(final File aSqliteDatabaseFile) throws SQLException {
			iConnection = DatabazeJinehoProgramu.otevri(aSqliteDatabaseFile);
			iStatement = iConnection.createStatement();
		}

		@Override
		public void close() throws IOException {
			try {
				try {
					iStatement.close();
				} finally {
					iConnection.close();
				}
			} catch (final SQLException e) {
				throw new IOException("Closing DAO failure.", e);
			}
		}

		public int cacheCount() throws SQLException {
			return count(CACHE_COUNT);
		}

		public int waypointCount() throws SQLException {
			return containsTables(Collections.singleton("Waypoints")) ? count(WAYPOINT_COUNT) : 0;
		}

		public int tagCount() throws SQLException {
			return count(TAG_COUNT);
		}

		/** Hodnoty sloupců z {@code aSloupceHodnot} dostane keš navíc v {@link AllValues#values}. */
		public boolean forEachCache(final Set<String> aSloupceHodnot, final Function<GsakCache, Boolean> aAction) throws SQLException {
			return forEach(SELECT_CACHES, GsakCache::new, aSloupceHodnot, aAction);
		}

		public boolean forEachWaypoint(final Function<GsakWaypoint, Boolean> aAction) throws SQLException {
			// Waypointy a vlastní hodnoty starší GSAK mít nemusí.
			return !containsTables(Collections.singleton("Waypoints")) || forEach(SELECT_WAYPOINTS, GsakWaypoint::new, Collections.emptySet(), aAction);
		}

		public boolean forEachCustomValue(final Function<Map<String, String>, Boolean> aAction) throws SQLException {
			return !containsTables(Collections.singleton("Custom")) || forEach(SELECT_CUSTOMVALUES, LinkedHashMap::new, Collections.emptySet(), aAction);
		}

		public boolean schemaMatches() throws SQLException {
			return containsTables(EXPECTED_TABLES);
		}

		public boolean containsTables(final Set<String> aExpectedTables) throws SQLException {
			final Set<String> tables = new HashSet<>();
			try (ResultSet rs = iStatement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
				while (rs.next()) {
					tables.add(rs.getString(1));
				}
			}
			final boolean tablesMissing = aExpectedTables.stream()//
					.filter(expected -> !tables.contains(expected))//
					.findFirst()//
					.isPresent()//
					;
			return !tablesMissing;
		}

		//--------------------------------------------------------------------------------------

		private int count(final String aSqlStatement) throws SQLException {
			try (ResultSet rs = iStatement.executeQuery(aSqlStatement)) {
				return rs.getInt(1);
			}
		}

		private <T> boolean forEach(final String aSelectStatement, final Supplier<T> aRecordFactory, final Set<String> aSloupceHodnot, final Function<T, Boolean> aAction) throws SQLException {
			try (ResultSet rs = iStatement.executeQuery(aSelectStatement)) {
				PrevodRadku prevod = null;
				while (rs.next()) {
					final T record = aRecordFactory.get();
					if (prevod == null) {
						prevod = new PrevodRadku(rs, record, aSloupceHodnot);
					}
					prevod.nacti(rs, record);
					if (!aAction.apply(record)) {
						return false;
					}
				}
			}
			return true;
		}

		/** Převod řádku na záznam, sloupce a pole se hledají jednou pro celý dotaz. */
		private final class PrevodRadku {
			private final Field[] pole;
			private final int[] sloupcePoli;
			private final int[] sloupceHodnot;
			private final String[] klice;

			PrevodRadku(final ResultSet aResultSet, final Object aRecord, final Set<String> aSloupceHodnot) throws SQLException {
				final List<String> sloupce = columnNames(aResultSet);
				final Map<String, Integer> indexy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				for (int i = sloupce.size() - 1; i >= 0; i--) {
					indexy.put(sloupce.get(i), i + 1);
				}
				final List<Field> nalezenaPole = new ArrayList<>();
				final List<Integer> nalezeneSloupce = new ArrayList<>();
				if (!(aRecord instanceof Map)) {
					// Sloupec, který starší GSAK nemá, zůstane na výchozí hodnotě.
					for (final Field f : aRecord.getClass().getDeclaredFields()) {
						final Integer index = indexy.get(f.getName());
						if (Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()) && index != null) {
							nalezenaPole.add(f);
							nalezeneSloupce.add(index);
						}
					}
				}
				pole = nalezenaPole.toArray(new Field[0]);
				sloupcePoli = nalezeneSloupce.stream().mapToInt(Integer::intValue).toArray();
				final List<Integer> hodnoty = new ArrayList<>();
				final List<String> nalezeneKlice = new ArrayList<>();
				if (aRecord instanceof Map || aRecord instanceof AllValues) {
					for (int i = 0; i < sloupce.size(); i++) {
						final String sloupec = sloupce.get(i);
						if (aRecord instanceof Map || aSloupceHodnot.stream().anyMatch(sloupec::equalsIgnoreCase)) {
							hodnoty.add(i + 1);
							nalezeneKlice.add(sloupec.equalsIgnoreCase("cCode") ? CACHE_CODE_KEY : sloupec);
						}
					}
				}
				sloupceHodnot = hodnoty.stream().mapToInt(Integer::intValue).toArray();
				klice = nalezeneKlice.toArray(new String[0]);
			}

			void nacti(final ResultSet aResultSet, final Object aRecord) throws SQLException {
				for (int i = 0; i < pole.length; i++) {
					final Field f = pole[i];
					final int sloupec = sloupcePoli[i];
					final Class<?> type = f.getType();
					try {
						if (type == String.class) {
							f.set(aRecord, aResultSet.getString(sloupec));
						} else if (type == boolean.class) {
							f.setBoolean(aRecord, aResultSet.getInt(sloupec) == 1);
						} else if (type == int.class) {
							f.setInt(aRecord, aResultSet.getInt(sloupec));
						} else if (type == double.class) {
							f.setDouble(aRecord, aResultSet.getDouble(sloupec));
						} else if (type == BigDecimal.class) {
							f.set(aRecord, aResultSet.getBigDecimal(sloupec));
						} else {
							throw new IllegalStateException("Unknon type " + type.getSimpleName() + " of field " + f.getName());
						}
					} catch (final IllegalAccessException e) {
						throw new RuntimeException("Error reading " + aRecord.getClass().getSimpleName(), e);
					}
				}
				if (sloupceHodnot.length > 0) {
					@SuppressWarnings("unchecked")
					final Map<String, Object> map = aRecord instanceof Map ? (Map<String, Object>) aRecord : ((AllValues) aRecord).values;
					for (int i = 0; i < sloupceHodnot.length; i++) {
						final Object value = aResultSet.getObject(sloupceHodnot[i]);
						if (value != null && !(value instanceof String && StringUtils.isBlank((String) value))) {
							map.put(klice[i], value);
						}
					}
				}
			}
		}

		private List<String> columnNames(final ResultSet aResultSet) throws SQLException {
			final List<String> result = new LinkedList<>();
			final ResultSetMetaData md = aResultSet.getMetaData();
			final int počet = md.getColumnCount();
			for (int i = 1; i <= počet; i++) {
				result.add(md.getColumnName(i));
			}
			return result;
		}

	}

	private static class GsakCache extends AllValues {
		public String Code;
		public String Name;
		//          public double Distance;
		public String PlacedBy;
		public boolean Archived;
		//          public String Bearing;
		//          public String CacheId;
		public String CacheType;
		//          public String Changed;
		public String Container;
		public String County;
		public String Country;
		//          public double Degrees;
		public String Difficulty;
		//          public boolean DNF;
		//          public String DNFDate;
		//          public int Found;
		//          public int FoundCount;
		public String FoundByMeDate;
		//          public boolean FTF;
		//          public boolean HasCorrected;
		//          public boolean HasTravelBug;
		//          public boolean HasUserNote;
		//          public String LastFoundDate;
		//          public String LastGPXDate;
		//          public String LastLog;
		//          public String LastUserDate;
		public double Latitude;
		//          public String Lock;
		//          public String LongHtm;
		public double Longitude;
		//          public int MacroFlag;
		//          public String MacroSort;
		//          public int NumberOfLogs;
		public int OwnerId;
		public String OwnerName;
		public String PlacedDate;
		//          public boolean ShortHtm;
		//          public String SmartName;
		//          public boolean SmartOverride;
		//          public String Source;
		public String State;
		//          public String Symbol;
		public boolean TempDisabled;
		public String Terrain;
		//          public String UserData;
		//          public String User2;
		//          public String User3;
		//          public String User4;
		//          public boolean UserFlag;
		//          public String UserNoteDate;
		//          public int UserSort;
		//          public boolean Watch;
		//          public boolean IsOwner;
		public double LatOriginal;
		public double LonOriginal;
		//          public String Created;
		//          public String Status;
		//          public String Color;
		//          public boolean ChildLoad;
		//          public String LinkedTo;
		//          public boolean GetPolyFlag;
		public int Elevation;
		//          public String Resolution;
		//          public String GcNote;
		//          public boolean IsPremium;
		//          public String Guid;
		public int FavPoints;

		// TABLE CacheMemo
		//          public String LongDescription;
		//          public String ShortDescription;
		//          public String Url;
		//          public String Hints;
		//          public String UserNote;
		//          public String TravelBugs;
		//
	}

	public static class GsakWaypoint extends AllValues {
		public String cParent;
		public String cCode;
		public String cPrefix;
		public String cName;
		public String cType;
		public double cLat;
		public double cLon;
		public boolean cByuser;
		public String cDate;
		public boolean cFlag;
		public boolean sB1;
	}

	public static abstract class AllValues {
		public final Map<String, Object> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}
}
