package cz.geokuk.plugins.kesoid.mvc;

import java.util.List;

import cz.geokuk.framework.Event0;

/** Jména databází, které při posledním načítání zamykal jiný program (prázdný seznam = žádná). */
public class ZamceneDatabazeEvent extends Event0<KesoidModel> {
	private final List<String> jmena;

	ZamceneDatabazeEvent(final List<String> jmena) {
		this.jmena = jmena;
	}

	public List<String> getJmena() {
		return jmena;
	}
}
