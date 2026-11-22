/**
 *
 */
package cz.geokuk.plugins.kesoid;

import cz.geokuk.framework.*;
import cz.geokuk.plugins.vylety.EVylet;

/**
 * @author Martin Veverka
 *
 */
@Preferenceble
public class FilterDefinition implements Copyable<FilterDefinition> {

	private int prahFavorit = 0;

	private EVylet prahVyletu = EVylet.NEVIM;
	private boolean jenDoTerenuUNenalezenych = true;
	private boolean jenFinalUNalezenych = true;

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.Copyable#copy()
	 */
	@Override
	public FilterDefinition copy() {
		try {
			return (FilterDefinition) super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}


	public int getPrahFavorit() {
		return prahFavorit;
	}


	/**
	 * @return the prahVyletu
	 */
	@PreferencebleIgnore
	public EVylet getPrahVyletu() {
		return prahVyletu;
	}

	/**
	 * @return the jenDoTerenuUNenalezenych
	 */
	@PreferencebleProperty(name = "jenFinal")
	public boolean isJenDoTerenuUNenalezenych() {
		return jenDoTerenuUNenalezenych;
	}

	/**
	 * @return the jenFinalUNalezenych
	 */
	@PreferencebleProperty(name = "jenJedenUNalezenych")
	public boolean isJenFinalUNalezenych() {
		return jenFinalUNalezenych;
	}

	/**
	 * @param jenDoTerenuUNenalezenych
	 *            the jenDoTerenuUNenalezenych to set
	 */
	@PreferencebleProperty(name = "jenFinal")
	public void setJenDoTerenuUNenalezenych(final boolean jenDoTerenuUNenalezenych) {
		this.jenDoTerenuUNenalezenych = jenDoTerenuUNenalezenych;
	}

	/**
	 * @param jenFinalUNalezenych
	 *            the jenFinalUNalezenych to set
	 */
	@PreferencebleProperty(name = "jenJedenUNalezenych")
	public void setJenFinalUNalezenych(final boolean jenFinalUNalezenych) {
		this.jenFinalUNalezenych = jenFinalUNalezenych;
	}


	public void setPrahFavorit(final int prahFavorit) {
		this.prahFavorit = prahFavorit;
	}


	/**
	 * @param prahVyletu
	 *            the prahVyletu to set
	 */
	@PreferencebleIgnore
	public void setPrahVyletu(final EVylet prahVyletu) {
		this.prahVyletu = prahVyletu;
	}

}
