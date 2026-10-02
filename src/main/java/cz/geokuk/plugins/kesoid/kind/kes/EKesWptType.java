package cz.geokuk.plugins.kesoid.kind.kes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typy waypoint. N2které předdefinované jsou zde. Není to ale výčet typů, protože mohou být nahrávány adhok typy.
 *
 * @author Martin Veverka
 */
public enum EKesWptType {
	// CACHE,
	FINAL_LOCATION, STAGES_OF_A_MULTICACHE, QUESTION_TO_ANSWER, REFERENCE_POINT, PARKING_AREA, TRAILHEAD;

	/** Volá se při kreslení pro každý waypoint, proto bez výjimek a s pamětí už zjištěných jmen. */
	private static final Map<String, Optional<EKesWptType>> ZJISTENE = new ConcurrentHashMap<>();
	private static final int MAX_ZJISTENYCH = 10_000;

	public static EKesWptType decode(final String aKesWptTpeStr) {
		if (aKesWptTpeStr == null) {
			return null;
		}
		Optional<EKesWptType> typ = ZJISTENE.get(aKesWptTpeStr);
		if (typ == null) {
			typ = Optional.ofNullable(podleJmena(upravNaVyctovec(aKesWptTpeStr)));
			if (ZJISTENE.size() < MAX_ZJISTENYCH) {
				ZJISTENE.put(aKesWptTpeStr, typ);
			}
		}
		return typ.orElse(null);
	}

	private static EKesWptType podleJmena(final String jmeno) {
		for (final EKesWptType t : values()) {
			if (t.name().equals(jmeno)) {
				return t;
			}
		}
		return null;
	}

	private static String upravNaVyctovec(final String pp) {
		return pp.replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
	}

}
