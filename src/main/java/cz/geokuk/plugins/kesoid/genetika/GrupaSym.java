package cz.geokuk.plugins.kesoid.genetika;

import java.util.*;

import cz.geokuk.util.lang.FString;

public class GrupaSym implements Grupa {
	static final String IMPLICITNI_GRUPA_NAME = "other!";

	private final String grupaName;
	private String displayName;

	private final Set<Alela> alely = new LinkedHashSet<>();

	public GrupaSym(final String grupaName) {
		this.grupaName = grupaName;
		displayName = grupaName;
	}

	public synchronized void add(final Alela alela) {
		alely.add(alela);
	}

	/**
	 * @return the alely
	 */
	@Override
	public Set<Alela> getAlely() {
		return Collections.unmodifiableSet(alely);
	}

	@Override
	public String getDisplayName() {
		return FString.isEmpty(displayName) ? grupaName : displayName;
	}

	public String name() {
		return grupaName;
	}

	public void setDisplayName(final String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return grupaName;
	}

}
