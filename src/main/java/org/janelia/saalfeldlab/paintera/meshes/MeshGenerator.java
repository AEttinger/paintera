package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMeshView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

/**
 * @author Philipp Hanslovsky
 */
public class MeshGenerator<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final T id;

	private final InterruptibleFunction<T, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks = FXCollections.observableHashMap();

	private final IntegerProperty preferredScaleLevel = new SimpleIntegerProperty(0);

	private final IntegerProperty highestScaleLevel = new SimpleIntegerProperty(0);

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty(0);

	private final BooleanProperty changed = new SimpleBooleanProperty(false);

	private final ObservableValue<Color> color;

	private final ObservableValue<Color> colorWithAlpha;

	private final Group root;

	private final Group meshesGroup;

	private final Group blocksGroup;

	private final ViewFrustum viewFrustum;

	private final BooleanProperty isEnabled = new SimpleBooleanProperty(true);

	private final ReadOnlyBooleanProperty showBlockBoundaries;

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final ObjectProperty<CompletableFuture<Void>> activeFuture = new SimpleObjectProperty<>();

	private final ObjectProperty<MeshGeneratorJobManager<T>.ManagementTask> activeTask = new SimpleObjectProperty<>();

	private final IntegerProperty numPendingTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty numCompletedTasks = new SimpleIntegerProperty(0);

	private final MeshGeneratorJobManager<T> manager;

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty(0.5);

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty(5);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final ObjectProperty<DrawMode> drawMode = new SimpleObjectProperty<>(DrawMode.FILL);

	private final ObjectProperty<CullFace> cullFace = new SimpleObjectProperty<>(CullFace.FRONT);

	private final DoubleProperty inflate = new SimpleDoubleProperty(1.0);

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	public MeshGenerator(
			final DataSource<?, ?> source,
			final ViewFrustum viewFrustum,
			final T segmentId,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final ObservableIntegerValue color,
			final int preferredScaleLevel,
			final int highestScaleLevel,
			final int meshSimplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final ExecutorService managers,
			final ExecutorService workers,
			final ReadOnlyBooleanProperty showBlockBoundaries)
	{
		super();
		this.source = source;
		this.id = segmentId;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.color = Bindings.createObjectBinding(() -> fromInt(color.get()), color);
		this.managers = managers;
		this.workers = workers;
		this.showBlockBoundaries = showBlockBoundaries;

		this.manager = new MeshGeneratorJobManager<>(
				this.source,
				this.meshesAndBlocks,
				this.managers,
				this.workers,
				this.numPendingTasks,
				this.numCompletedTasks
			);

		this.colorWithAlpha = Bindings.createObjectBinding(
				() -> this.color.getValue().deriveColor(
						0,
						1.0,
						1.0,
						this.opacity.get()
				                                       ),
				this.color,
				this.opacity
		                                                  );

		this.changed.addListener((obs, oldv, newv) -> {if (newv) updateMeshes();});
		this.changed.addListener((obs, oldv, newv) -> changed.set(false));

		this.preferredScaleLevel.set(preferredScaleLevel);
		this.preferredScaleLevel.addListener((obs, oldv, newv) -> changed.set(true));

		this.highestScaleLevel.set(highestScaleLevel);
		this.highestScaleLevel.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshSimplificationIterations.set(meshSimplificationIterations);
		this.meshSimplificationIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingLambda.set(smoothingLambda);
		this.smoothingLambda.addListener((obs, oldv, newv) -> changed.set(true));

		this.smoothingIterations.set(smoothingIterations);
		this.smoothingIterations.addListener((obs, oldv, newv) -> changed.set(true));

		this.meshesGroup = new Group();
		this.blocksGroup = new Group();
		this.root = new Group(meshesGroup, blocksGroup);

		this.viewFrustum = viewFrustum;

		this.isEnabled.addListener((obs, oldv, newv) -> {
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				synchronized (this.meshesAndBlocks)
				{
					if (newv)
					{
						final List<MeshView> meshes = new ArrayList<>();
						for (final Pair<MeshView, Node> meshAndBlock : meshesAndBlocks.values())
							meshes.add(meshAndBlock.getA());
						meshesGroup.getChildren().setAll(meshes);
					}
					else
					{
						meshesGroup.getChildren().clear();
					}
					updateBlocksGroup();
				}
			});
		});

		this.showBlockBoundaries.addListener(obs -> updateBlocksGroup());

		this.meshesAndBlocks.addListener((MapChangeListener<ShapeKey<T>, Pair<MeshView, Node>>) change -> {
			if (change.wasRemoved())
			{
				final MeshView meshRemoved = change.getValueRemoved().getA();
				((PhongMaterial) meshRemoved.getMaterial()).diffuseColorProperty().unbind();
				meshRemoved.visibleProperty().unbind();
				meshRemoved.drawModeProperty().unbind();
				meshRemoved.cullFaceProperty().unbind();
				meshRemoved.scaleXProperty().unbind();
				meshRemoved.scaleYProperty().unbind();
				meshRemoved.scaleZProperty().unbind();

				final PolygonMeshView blockOutlineRemoved = (PolygonMeshView) change.getValueRemoved().getB();
				blockOutlineRemoved.visibleProperty().unbind();
				blockOutlineRemoved.scaleXProperty().unbind();
				blockOutlineRemoved.scaleYProperty().unbind();
				blockOutlineRemoved.scaleZProperty().unbind();
				((PhongMaterial) blockOutlineRemoved.getMaterial()).diffuseColorProperty().unbind();
			}
			else
			{
				final MeshView meshAdded = change.getValueAdded().getA();
				((PhongMaterial) meshAdded.getMaterial()).diffuseColorProperty().bind(this.colorWithAlpha);
				meshAdded.visibleProperty().bind(this.isVisible);
				meshAdded.drawModeProperty().bind(this.drawMode);
				meshAdded.cullFaceProperty().bind(this.cullFace);
				meshAdded.scaleXProperty().bind(this.inflate);
				meshAdded.scaleYProperty().bind(this.inflate);
				meshAdded.scaleZProperty().bind(this.inflate);

				final PolygonMeshView blockOutlineAdded = (PolygonMeshView) change.getValueAdded().getB();
				blockOutlineAdded.visibleProperty().bind(this.isVisible);
				blockOutlineAdded.scaleXProperty().bind(this.inflate);
				blockOutlineAdded.scaleYProperty().bind(this.inflate);
				blockOutlineAdded.scaleZProperty().bind(this.inflate);
				((PhongMaterial) blockOutlineAdded.getMaterial()).diffuseColorProperty().bind(this.colorWithAlpha);
			}

			if (change.wasRemoved())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					meshesGroup.getChildren().remove(change.getValueRemoved().getA());
					blocksGroup.getChildren().remove(change.getValueRemoved().getB());
				});
			}
			else if (change.wasAdded())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					if (this.isEnabled.get())
					{
						if (!meshesGroup.getChildren().contains(change.getValueAdded().getA()))
							meshesGroup.getChildren().add(change.getValueAdded().getA());

						if (this.showBlockBoundaries.get())
						{
							if (!blocksGroup.getChildren().contains(change.getValueAdded().getB()))
								blocksGroup.getChildren().add(change.getValueAdded().getB());
						}
					}
				});
			}
		});
	}

	public void update()
	{
		this.changed.set(true);
	}

	public synchronized void interrupt()
	{
		if (isInterrupted.get())
		{
			LOG.debug("MeshGenerator for {} has already been interrupted", id);
			return;
		}

		LOG.debug("Interrupting rendering tasks for {}", id);
		isInterrupted.set(true);
		Optional.ofNullable(activeFuture.get()).ifPresent(future -> future.cancel(true));
		Optional.ofNullable(activeTask.get()).ifPresent(task -> task.interrupt());
		activeFuture.set(null);
		activeTask.set(null);
	}

	private synchronized void updateMeshes()
	{
		if (isInterrupted.get())
		{
			LOG.debug("MeshGenerator for {} has been interrupted, ignoring update request", id);
			return;
		}

		final Pair<MeshGeneratorJobManager<T>.ManagementTask, CompletableFuture<Void>> taskAndFuture = manager.submit(
				source,
				id,
				viewFrustum,
				preferredScaleLevel.intValue(),
				highestScaleLevel.intValue(),
				meshSimplificationIterations.intValue(),
				smoothingLambda.doubleValue(),
				smoothingIterations.intValue(),
				blockListCache,
				meshCache
			);

		this.activeTask.set(taskAndFuture.getA());
		this.activeFuture.set(taskAndFuture.getB());
	}

	private void updateBlocksGroup()
	{
		synchronized (meshesAndBlocks)
		{
			if (isEnabled.get() && showBlockBoundaries.get())
			{
				final List<Node> blockBoundaryMeshes = new ArrayList<>();
				for (final Pair<MeshView, Node> meshAndBlock : meshesAndBlocks.values())
					blockBoundaryMeshes.add(meshAndBlock.getB());
				blocksGroup.getChildren().setAll(blockBoundaryMeshes);
			}
			else
			{
				blocksGroup.getChildren().clear();
			}
		}
	}

	private static final Color fromInt(final int argb)
	{
		return Color.rgb(ARGBType.red(argb), ARGBType.green(argb), ARGBType.blue(argb), 1.0);
	}

	public T getId()
	{
		return id;
	}

	public Node getRoot()
	{
		return this.root;
	}

	public BooleanProperty isEnabledProperty()
	{
		return this.isEnabled;
	}

	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	public IntegerProperty smoothingIterationsProperty()
	{
		return smoothingIterations;
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return smoothingLambda;
	}

	public IntegerProperty preferredScaleLevelProperty()
	{
		return this.preferredScaleLevel;
	}

	public IntegerProperty highestScaleLevelProperty()
	{
		return this.highestScaleLevel;
	}

	public ObservableIntegerValue numPendingTasksProperty()
	{
		return this.numPendingTasks;
	}

	public ObservableIntegerValue numCompletedTasksProperty()
	{
		return this.numCompletedTasks;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	public ObjectProperty<DrawMode> drawModeProperty()
	{
		return this.drawMode;
	}

	public ObjectProperty<CullFace> cullFaceProperty()
	{
		return this.cullFace;
	}

	public DoubleProperty inflateProperty()
	{
		return this.inflate;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public void bindTo(final MeshSettings meshSettings)
	{
		LOG.debug("Binding to {}", meshSettings);
		opacityProperty().bind(meshSettings.opacityProperty());
		preferredScaleLevelProperty().bind(meshSettings.preferredScaleLevelProperty());
		highestScaleLevelProperty().bind(meshSettings.highestScaleLevelProperty());
		meshSimplificationIterationsProperty().bind(meshSettings.simplificationIterationsProperty());
		cullFaceProperty().bind(meshSettings.cullFaceProperty());
		drawModeProperty().bind(meshSettings.drawModeProperty());
		smoothingIterationsProperty().bind(meshSettings.smoothingIterationsProperty());
		smoothingLambdaProperty().bind(meshSettings.smoothingLambdaProperty());
		inflateProperty().bind(meshSettings.inflateProperty());
		isVisible.bind(meshSettings.isVisibleProperty());
	}

	public void unbind()
	{
		LOG.debug("Unbinding mesh generator");
		opacityProperty().unbind();
		preferredScaleLevelProperty().unbind();
		highestScaleLevelProperty().unbind();
		meshSimplificationIterationsProperty().unbind();
		cullFaceProperty().unbind();
		drawModeProperty().unbind();
			smoothingIterationsProperty().unbind();
		smoothingLambdaProperty().unbind();
		inflateProperty().unbind();
		isVisible.unbind();
	}
}
