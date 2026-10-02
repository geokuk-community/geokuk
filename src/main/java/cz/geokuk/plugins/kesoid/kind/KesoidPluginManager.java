package cz.geokuk.plugins.kesoid.kind;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.Kepodr;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.detail.JKesoidDetail0;
import cz.geokuk.plugins.kesoid.importek.GpxWpt;
import cz.geokuk.plugins.kesoid.kind.cgp.CgpPlugin;
import cz.geokuk.plugins.kesoid.kind.kes.KesPlugin;
import cz.geokuk.plugins.kesoid.kind.munzee.MunzeePlugin;
import cz.geokuk.plugins.kesoid.kind.photo.PhotoPlugin;
import cz.geokuk.plugins.kesoid.kind.waymark.WaymarkPlugin;
import cz.geokuk.plugins.kesoid.kind.simplewaypoint.SimpleWaypointGpxWptProcak;
import cz.geokuk.plugins.kesoid.kind.simplewaypoint.SimpleWaypointPlugin;
import cz.geokuk.util.procak.ProcakDispatcher;
import lombok.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Manager kesouidových pluginů.
 * Je to vstupní bod pro práci s pluginy.
 * KesoidPluginManager se injektuje těm, kteří potřebujídělat něco, co je specifické pro jednotlivé druhy kešoidů.
 * Je snaha specifikum kešoidů soustředit v jednom balíku.
 * @author Martin
 *
 */
@Slf4j
public class KesoidPluginManager {


	@Getter
	private final List<KesoidPlugin> plugins;
	private Factory factory;

	public KesoidPluginManager() {
		log.debug("Found kesoid plugins:");
		plugins = Stream.of(new KesPlugin(), new CgpPlugin(), new WaymarkPlugin(), new MunzeePlugin(), new PhotoPlugin(), new SimpleWaypointPlugin())
				.sorted( (p1, p2) -> p1.getOrder() - p2.getOrder())
				.peek(plugin -> log.debug("   {}", plugin.getClass().getName()))
				.collect(Collectors.toList());
		log.debug("Found {} kesoid plugins total.", plugins.size());
	}


	/**
	 * Zřídíme procák dispatchera, který bude jednotlivými poskytovateli publikovat waypointy do buldera.
	 * @param builder
	 * @return
	 */
	public ProcakDispatcher<GpxWpt> createGpxWptProcakDispatcher(final GpxToWptContext ctx, final GpxToWptBuilder builder) {
		return new ProcakDispatcher<>(plugins.stream()
				.map(plugin -> plugin.createGpxWptProcak(ctx,
						(gpxwpt, kepodr) -> {
							final Wpt wpt = builder.createWpt(gpxwpt, kepodr);
							wpt.setKesoidPlugin(plugin);
							return wpt;
						}))
				.filter(Objects::nonNull)
				.collect(Collectors.toList()),

				new SimpleWaypointGpxWptProcak(ctx,
						(gpxwpt, kepodr) -> {
							final Wpt wpt = builder.createWpt(gpxwpt, kepodr);
							wpt.setKesoidPlugin(sinkPlugin());
							return wpt;
						})

				);

	}

	/**
	 * Poslední z pluginů je sink plugin, do kterého se nasype to, co jiní nechtěli.
	 * @return
	 */
	private KesoidPlugin sinkPlugin() {
		return plugins.get(plugins.size() - 1);
	}

	/**
	 * Vyrobí všechna okna do dolního rohu pro kešoidy.
	 * @return
	 */
	public Map<KesoidPlugin, JKesoidDetail0> createKesoidDetails() {
		return plugins.stream()
				.collect(Collectors.toMap(Function.identity(), kesoidPlugin -> factory.init(kesoidPlugin.createDetail())));

	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	/**
	 * Vrátí objekty poskytující popisky map pro každý z kepodrů.
	 * @return
	 */
	public Map<Kepodr, PopiskyDef> getPopisekDefMap() {
		return podplugins().collect(
				Collectors.toMap(kp -> kp.getKepodr(), kp -> kp.getPlugin().getPopiskyDef(kp.kepodr))
				);
	}

	private Stream<KesoidPodplugin> podplugins() {
		return plugins.stream().flatMap(
				plugin -> plugin
				.getKepodrs().stream()
				.map(kepodr -> new KesoidPodplugin(plugin, kepodr))
				);
	}

	/** Přiřazení pluginu a poddruhu */
	@Data
	private static class KesoidPodplugin {
		final KesoidPlugin plugin;
		final Kepodr kepodr;
	}
}
