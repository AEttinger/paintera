package org.janelia.saalfeldlab.paintera.meshes.cache;

import com.pivovarit.function.ThrowingFunction;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.python.bouncycastle.crypto.tls.CipherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CacheUtils {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Create caches for storing lists of blocks containing a given label at each scale level.
	 *
	 * @param blocksForLabel
	 * @param makeCache
	 * @param <K>
	 * @return
	 */
	public static <K> Pair<InterruptibleFunctionAndCache<K, Interval[]>, Invalidate<K>>[] blocksForLabelCaches(
			final Function<K, Interval[]>[] blocksForLabel,
			final Function<CacheLoader<K, Interval[]>, Pair<Cache<K, Interval[]>, Invalidate<K>>> makeCache) {
		@SuppressWarnings("unchecked") final Pair<InterruptibleFunctionAndCache<K, Interval[]>, Invalidate<K>>[] caches = new Pair[blocksForLabel.length];
		for (int i = 0; i < caches.length; ++i) {
			final BlocksForLabelCacheLoader<K> loader = new BlocksForLabelCacheLoader<>(blocksForLabel[i]);
			final Pair<Cache<K, Interval[]>, Invalidate<K>> cache = makeCache.apply(loader);
			caches[i] = new ValuePair<>(new InterruptibleFunctionAndCache<>(cache.getA().unchecked(), loader), cache.getB());
		}
		return caches;
	}

	/**
	 * @param source
	 * @param getMaskGenerator Turn data into binary mask usable in marching cubes.
	 * @param makeCache        Build a {@link Cache} from a {@link CacheLoader}
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<Long>, Triple<float[], float[], int[]>>[]
	meshCacheLoaders(
			final DataSource<D, T> source,
			final LongFunction<Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<Long>, Triple<float[], float[], int[]>>, Cache<ShapeKey<Long>, Triple<float[],
					float[], int[]>>> makeCache) {
		return meshCacheLoaders(
				source,
				Stream.generate(() -> new int[]{1, 1, 1}).limit(source.getNumMipmapLevels()).toArray(int[][]::new),
				getMaskGenerator,
				makeCache
		);
	}

	/**
	 * @param source
	 * @param cubeSizes        cube sizes for marching cubes
	 * @param getMaskGenerator Turn data into binary mask usable in marching cubes.
	 * @param makeCache        Build a {@link Cache} from a {@link CacheLoader}
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> InterruptibleFunctionAndCache<ShapeKey<Long>, Triple<float[], float[], int[]>>[]
	meshCacheLoaders(
			final DataSource<D, T> source,
			final int[][] cubeSizes,
			final LongFunction<Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<Long>, Triple<float[], float[], int[]>>, Cache<ShapeKey<Long>, Triple<float[],
					float[], int[]>>> makeCache) {
		final int numMipmapLevels = source.getNumMipmapLevels();
		@SuppressWarnings("unchecked") final InterruptibleFunctionAndCache<ShapeKey<Long>, Triple<float[], float[], int[]>>[]
				caches = new InterruptibleFunctionAndCache[numMipmapLevels];

		for (int i = 0; i < numMipmapLevels; ++i) {
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, i, transform);
			final MeshCacheLoader<D> loader = new MeshCacheLoader<>(
					cubeSizes[i],
					source.getDataSource(0, i),
					getMaskGenerator,
					transform
			);
			final Cache<ShapeKey<Long>, Triple<float[], float[], int[]>> cache = makeCache.apply(loader);
			caches[i] = new InterruptibleFunctionAndCache<>(cache.unchecked(), loader);
		}

		return caches;
	}

	/**
	 * @param source
	 * @param getMaskGenerator Turn data into binary mask usable in marching cubes.
	 * @param makeCache        Build a {@link Cache} from a {@link CacheLoader}
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> Pair<
			InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Triple<float[], float[], int[]>>,
			Invalidate<ShapeKey<TLongHashSet>>>
			[]
	segmentMeshCacheLoaders(
			final DataSource<D, T> source,
			final Function<TLongHashSet, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Triple<float[], float[], int[]>>, Pair<Cache<ShapeKey<TLongHashSet>,
					Triple<float[], float[], int[]>>, Invalidate<ShapeKey<TLongHashSet>>>> makeCache) {
		return segmentMeshCacheLoaders(
				source,
				Stream.generate(() -> new int[]{1, 1, 1}).limit(source.getNumMipmapLevels()).toArray(int[][]::new),
				getMaskGenerator,
				makeCache
		);
	}

	/**
	 * @param source
	 * @param cubeSizes        cube sizes for marching cubes
	 * @param getMaskGenerator Turn data into binary mask usable in marching cubes.
	 * @param makeCache        Build a {@link Cache} from a {@link CacheLoader}
	 * @return Cascade of {@link Cache} for retrieval of mesh queried by label id.
	 */
	public static <D, T> Pair<InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Triple<float[], float[], int[]>>, Invalidate<ShapeKey<TLongHashSet>>>[]
	segmentMeshCacheLoaders(
			final DataSource<D, T> source,
			final int[][] cubeSizes,
			final Function<TLongHashSet, Converter<D, BoolType>> getMaskGenerator,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Triple<float[], float[], int[]>>, Pair<Cache<ShapeKey<TLongHashSet>,
					Triple<float[], float[], int[]>>, Invalidate<ShapeKey<TLongHashSet>>>> makeCache) {
		final int numMipmapLevels = source.getNumMipmapLevels();
		@SuppressWarnings("unchecked") Pair<InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>,
				Triple<float[], float[], int[]>>, Invalidate<ShapeKey<TLongHashSet>>>[] caches = new Pair[numMipmapLevels];

		LOG.debug("source is type {}", source.getClass());
		for (int i = 0; i < numMipmapLevels; ++i) {
			final int fi = i;
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, i, transform);
			final SegmentMeshCacheLoader<D> loader = new SegmentMeshCacheLoader<>(
					cubeSizes[i],
					() -> source.getDataSource(0, fi),
					getMaskGenerator,
					transform
			);
			final Pair<Cache<ShapeKey<TLongHashSet>, Triple<float[], float[], int[]>>, Invalidate<ShapeKey<TLongHashSet>>> cache = makeCache.apply(loader);
			caches[i] = new ValuePair<>(new InterruptibleFunctionAndCache<>(cache.getA().unchecked(), loader), cache.getB());
		}

		return caches;
	}

}
