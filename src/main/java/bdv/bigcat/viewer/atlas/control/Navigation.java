package bdv.bigcat.viewer.atlas.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.RemoveRotation;
import bdv.bigcat.viewer.atlas.control.navigation.Rotate;
import bdv.bigcat.viewer.atlas.control.navigation.TranslateAlongNormal;
import bdv.bigcat.viewer.atlas.control.navigation.TranslateWithinPlane;
import bdv.bigcat.viewer.atlas.control.navigation.Zoom;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import net.imglib2.realtransform.AffineTransform3D;

public class Navigation implements ToOnEnterOnExit
{

	private static final double[] FACTORS = { 1.0, 10.0, 0.1 };

	private final DoubleProperty zoomSpeed = new SimpleDoubleProperty( 1.05 );

	private final DoubleProperty translationSpeed = new SimpleDoubleProperty( 1.0 );

	private final DoubleProperty rotationSpeed = new SimpleDoubleProperty( 1.0 );

	private final BooleanProperty allowRotations = new SimpleBooleanProperty( true );

	private final GlobalTransformManager manager;

	private final KeyTracker keyTracker;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform;

	private final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform;

	public Navigation(
			final GlobalTransformManager manager,
			final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform,
			final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform,
			final KeyTracker keyTracker )
	{
		super();
		this.manager = manager;
		this.displayTransform = displayTransform;
		this.globalToViewerTransform = globalToViewerTransform;
		this.keyTracker = keyTracker;
	}

	@Override
	public Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{

				final AffineTransform3D viewerTransform = new AffineTransform3D();
				t.addTransformListener( viewerTransform::set );

				final ReadOnlyDoubleProperty mouseX = t.mouseXProperty();
				final ReadOnlyDoubleProperty mouseY = t.mouseYProperty();
				final ReadOnlyBooleanProperty isInside = t.isMouseInsideProperty();

				final DoubleBinding mouseXIfInsideElseCenterX = Bindings.createDoubleBinding( () -> isInside.get() ? mouseX.get() : t.getWidth() / 2, isInside, mouseX );
				final DoubleBinding mouseYIfInsideElseCenterY = Bindings.createDoubleBinding( () -> isInside.get() ? mouseY.get() : t.getHeight() / 2, isInside, mouseY );

				final AffineTransform3D worldToSharedViewerSpace = new AffineTransform3D();
				final AffineTransform3D globalTransform = new AffineTransform3D();
				final AffineTransform3D displayTransform = new AffineTransform3D();
				final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

				manager.addListener( globalTransform::set );

				this.displayTransform.apply( t ).addListener( tf -> {
					displayTransform.set( tf );
					this.globalToViewerTransform.apply( t ).getTransformCopy( worldToSharedViewerSpace );
					worldToSharedViewerSpace.preConcatenate( tf );
				} );

				this.globalToViewerTransform.apply( t ).addListener( tf -> {
					globalToViewerTransform.set( tf );
					this.displayTransform.apply( t ).getTransformCopy( worldToSharedViewerSpace );
					worldToSharedViewerSpace.concatenate( tf );
				} );

				final TranslateWithinPlane translateXY = new TranslateWithinPlane(
						manager,
						this.displayTransform.apply( t ),
						this.globalToViewerTransform.apply( t ),
						manager );

				final List< InstallAndRemove< Node > > iars = new ArrayList<>();

				final TranslateAlongNormal scrollDefault = new TranslateAlongNormal( translationSpeed.multiply( FACTORS[ 0 ] )::get, manager, worldToSharedViewerSpace, manager );
				final TranslateAlongNormal scrollFast = new TranslateAlongNormal( translationSpeed.multiply( FACTORS[ 1 ] )::get, manager, worldToSharedViewerSpace, manager );
				final TranslateAlongNormal scrollSlow = new TranslateAlongNormal( translationSpeed.multiply( FACTORS[ 2 ] )::get, manager, worldToSharedViewerSpace, manager );

				iars.add( EventFX.SCROLL( "translate along normal", e -> scrollDefault.scroll( -e.getDeltaY() ), event -> keyTracker.noKeysActive() ) );
				iars.add( EventFX.SCROLL( "translate along normal fast", e -> scrollFast.scroll( -e.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) ) );
				iars.add( EventFX.SCROLL( "translate along normal slow", e -> scrollSlow.scroll( -e.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL ) ) );

				iars.add( EventFX.KEY_PRESSED( "button translate along normal bck", e -> scrollDefault.scroll( +1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.COMMA ) ) );
				iars.add( EventFX.KEY_PRESSED( "button translate along normal fwd", e -> scrollDefault.scroll( -1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.PERIOD ) ) );

				iars.add( EventFX.KEY_PRESSED( "button translate along normal fast bck", e -> scrollFast.scroll( +1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.COMMA, KeyCode.SHIFT ) ) );
				iars.add( EventFX.KEY_PRESSED( "button translate along normal fast fwd", e -> scrollFast.scroll( -1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.PERIOD, KeyCode.SHIFT ) ) );

				iars.add( EventFX.KEY_PRESSED( "button translate along normal slow bck", e -> scrollSlow.scroll( +1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.COMMA, KeyCode.CONTROL ) ) );
				iars.add( EventFX.KEY_PRESSED( "button translate along normal slow fwd", e -> scrollSlow.scroll( -1 ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.PERIOD, KeyCode.CONTROL ) ) );

				iars.add( MouseDragFX.createDrag(
						"translate xy",
						e -> e.isSecondaryButtonDown() && keyTracker.noKeysActive(),
						true,
						manager,
						e -> translateXY.init(),
						( dX, dY ) -> translateXY.drag( dX, dY ),
						false ) );

				final Zoom zoom = new Zoom( zoomSpeed::get, manager, viewerTransform, manager );
				iars.add( EventFX.SCROLL(
						"zoom",
						event -> zoom.zoomCenteredAt( -event.getDeltaY(), event.getX(), event.getY() ),
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.META ) ||
								keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT ) ) );

				iars.add( EventFX.KEY_PRESSED(
						"button zoom out",
						event -> zoom.zoomCenteredAt( 1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get() ),
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.MINUS )
								|| keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.MINUS )
								|| keyTracker.areOnlyTheseKeysDown( KeyCode.DOWN ) ) );

				iars.add( EventFX.KEY_PRESSED(
						"button zoom out",
						event -> zoom.zoomCenteredAt( -1.0, mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get() ),
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.EQUALS )
								|| keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.EQUALS )
								|| keyTracker.areOnlyTheseKeysDown( KeyCode.UP ) ) );

				iars.add( rotationHandler(
						"rotate",
						allowRotations::get,
						rotationSpeed.multiply( FACTORS[ 0 ] )::get,
						globalTransform,
						displayTransform,
						globalToViewerTransform,
						manager::setTransform,
						manager,
						event -> keyTracker.noKeysActive() && event.getButton().equals( MouseButton.PRIMARY ) ) );

				iars.add( rotationHandler(
						"rotate fast",
						allowRotations::get,
						rotationSpeed.multiply( FACTORS[ 1 ] )::get,
						globalTransform,
						displayTransform,
						globalToViewerTransform,
						manager::setTransform,
						manager,
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) && event.getButton().equals( MouseButton.PRIMARY ) ) );

				iars.add( rotationHandler(
						"rotate slow",
						allowRotations::get,
						rotationSpeed.multiply( FACTORS[ 2 ] )::get,
						globalTransform,
						displayTransform,
						globalToViewerTransform,
						manager::setTransform,
						manager,
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL ) && event.getButton().equals( MouseButton.PRIMARY ) ) );

				final RemoveRotation removeRotation = new RemoveRotation( viewerTransform, globalTransform, manager::setTransform, manager );
				iars.add( EventFX.KEY_PRESSED(
						"remove rotation",
						e -> removeRotation.removeRotationCenteredAt( mouseXIfInsideElseCenterX.get(), mouseYIfInsideElseCenterY.get() ),
						e -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.Z ) ) );

				this.mouseAndKeyHandlers.put( t, iars );

			}
			this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.installInto( t ) );
		};
	}

	@Override
	public Consumer< ViewerPanelFX > getOnExit()
	{
		return t -> {
			Optional
					.ofNullable( this.mouseAndKeyHandlers.get( t ) )
					.ifPresent( hs -> hs.forEach( h -> h.removeFrom( t ) ) );
		};
	}

	private static final MouseDragFX rotationHandler(
			final String name,
			final BooleanSupplier allowRotations,
			final DoubleSupplier speed,
			final AffineTransform3D globalTransform,
			final AffineTransform3D displayTransform,
			final AffineTransform3D globalToViewerTransform,
			final Consumer< AffineTransform3D > submitTransform,
			final Object lock,
			final Predicate< MouseEvent > predicate )
	{
		final Rotate rotate = new Rotate(
				speed,
				globalTransform,
				displayTransform,
				globalToViewerTransform,
				submitTransform,
				lock );

		return new MouseDragFX( name, predicate, true, lock, false )
		{

			@Override
			public void initDrag( final MouseEvent event )
			{
				if ( allowRotations.getAsBoolean() )
				{
					rotate.initialize();
				}
				else
					abortDrag();
			}

			@Override
			public void drag( final MouseEvent event )
			{
				rotate.rotate( event.getX(), event.getY(), startX, startY );
			}
		};
	}

}
