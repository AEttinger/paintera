package bdv.labels.labelset;


import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.type.numeric.integer.LongType;

import java.util.*;

/**
 * Created by phil on 4/6/16.
 */
public class LabelMultisetFill {


    public static void fill(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long newLabel,
            Shape shape )
    {
        long[] min = new long[labels.numDimensions()];
        long[] max = new long[labels.numDimensions()];

        labels.min( min );
        labels.max( max );

        RandomAccess<LabelMultisetType> labelsAccess = labels.randomAccess();
        labelsAccess.setPosition( seed );

        Set<Multiset.Entry<SuperVoxel>> entries = labelsAccess.get().entrySet();
        long seedLabel = -1;
        long seedCount = 0;
        for ( Multiset.Entry<SuperVoxel> e : entries )
        {
            int count = e.getCount();
            if ( count > seedCount )
            {
                seedCount = count;
                seedLabel = e.getElement().id();
            }
        }

        if ( seedCount > 0 )
            fill( labels, canvas, seed, seedLabel, newLabel, shape, min, max );

    }


    public static void fill(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long seedLabel,
            long newLabel,
            Shape shape )
    {
        long[] min = new long[labels.numDimensions()];
        long[] max = new long[labels.numDimensions()];

        labels.min( min );
        labels.max( max );

        fill( labels, canvas, seed, seedLabel, newLabel, shape, min, max );
    }


    public static void fill(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long seedLabel,
            long newLabel,
            Shape shape,
            long min[],
            long max[] )
    {

        RandomAccess<LabelMultisetType> labelsAccess = labels.randomAccess();
        RandomAccess<LongType> canvasAccess = canvas.randomAccess();

        canvasAccess.setPosition( seed );

        long startingCanvasLabel = canvasAccess.get().get();

        long[] dim = new long[seed.numDimensions()];
        seed.localize( dim );

        Point startingPoint = new Point(seed);
        ArrayList<Localizable> visitedPoints = new ArrayList<Localizable>();
        // implement with long[]?
        ArrayDeque<Localizable> openTasks = new ArrayDeque<Localizable>();
        openTasks.add( startingPoint );
        visitedPoints.add( startingPoint );

        RandomAccessible<Neighborhood<LabelMultisetType>> labelsNeighborhood = shape.neighborhoodsRandomAccessible( labels );
        RandomAccessible<Neighborhood<LongType>> canvasNeighborhood = shape.neighborhoodsRandomAccessible(canvas);
        RandomAccess<Neighborhood<LabelMultisetType>> labelsNeighborhoodAccess = labelsNeighborhood.randomAccess();
        RandomAccess<Neighborhood<LongType>> canvasNeighborhoodAccess = canvasNeighborhood.randomAccess();


        Point dummyPoint = new Point( startingPoint.numDimensions() );


        while( ! openTasks.isEmpty() )
        {
            // get point that has been waiting for the longest
            Localizable currentPoint = openTasks.remove();
            canvasAccess.setPosition( currentPoint );
            labelsAccess.setPosition( currentPoint );

            LabelMultisetType labelsValue = labelsAccess.get();
            LongType canvasValue = canvasAccess.get();

            // might change second condition here - what's the correct condition?
            if ( labelsValue.contains( seedLabel ) && canvasValue.get() != newLabel )
                canvasValue.set(newLabel);
            else
                continue;

            labelsNeighborhoodAccess.setPosition( currentPoint );

            Cursor<LabelMultisetType> labelsNeighborhoodCursor = labelsNeighborhoodAccess.get().cursor();

            while( labelsNeighborhoodCursor.hasNext() )
            {
                labelsNeighborhoodCursor.fwd();
                // dummyPoint.setPosition( labelsNeighborhoodCursor );

                boolean isInBounds = true;
                for ( int d = 0; d < min.length; ++d )
                {
                    long currentPos = dummyPoint.getLongPosition( d );
                    if ( currentPos < min[d] || currentPos > max[d] )
                    {
                        isInBounds = false;
                        break;
                    }
                }

                if ( isInBounds && ( ! visitedPoints.contains( labelsNeighborhoodCursor ) ) ) {
                    Point newTask = new Point( labelsNeighborhoodCursor );
                    openTasks.add( newTask );
                    visitedPoints.add( newTask );
                }
            }

        }

    }

    public static void fillRecursive(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long newLabel,
            Shape shape )
    {
        long[] min = new long[labels.numDimensions()];
        long[] max = new long[labels.numDimensions()];

        labels.min( min );
        labels.max( max );

        RandomAccess<LabelMultisetType> labelsAccess = labels.randomAccess();
        labelsAccess.setPosition( seed );

        Set<Multiset.Entry<SuperVoxel>> entries = labelsAccess.get().entrySet();
        long seedLabel = -1;
        long seedCount = 0;
        for ( Multiset.Entry<SuperVoxel> e : entries )
        {
            int count = e.getCount();
            if ( count > seedCount )
            {
                seedCount = count;
                seedLabel = e.getElement().id();
            }
        }

        if ( seedCount > 0 )
            fillRecursive( labels, canvas, seed, seedLabel, newLabel, shape, min, max );

    }

    public static void fillRecursive(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long seedLabel,
            long newLabel,
            Shape shape )
    {
        long[] min = new long[labels.numDimensions()];
        long[] max = new long[labels.numDimensions()];

        labels.min( min );
        labels.max( max );

        fillRecursive( labels, canvas, seed, seedLabel, newLabel, shape, min, max );
    }



    public static void fillRecursive(
            RandomAccessibleInterval< LabelMultisetType > labels,
            RandomAccessibleInterval< LongType > canvas,
            Localizable seed,
            long seedLabel,
            long newLabel,
            Shape shape,
            long min[],
            long max[] )
    {

        RandomAccess<LabelMultisetType> labelsAccess = labels.randomAccess();
        RandomAccess<LongType> canvasAccess = canvas.randomAccess();
        canvasAccess.setPosition( seed );
        canvasAccess.get().set( newLabel );

        long[] dim = new long[seed.numDimensions()];
        seed.localize( dim );


        RandomAccessible<Neighborhood<LabelMultisetType>> labelsNeighborhood = shape.neighborhoodsRandomAccessible( labels );
        RandomAccessible<Neighborhood<LongType>> canvasNeighborhood = shape.neighborhoodsRandomAccessible(canvas);
        RandomAccess<Neighborhood<LabelMultisetType>> labelsNeighborhoodAccess = labelsNeighborhood.randomAccess();
        RandomAccess<Neighborhood<LongType>> canvasNeighborhoodAccess = canvasNeighborhood.randomAccess();
        labelsNeighborhoodAccess.setPosition( seed );
        canvasNeighborhoodAccess.setPosition( seed );


        Cursor<LabelMultisetType> labelsNeighborhoodCursor = labelsNeighborhoodAccess.get().cursor();
        Cursor<LongType> canvasNeighborhoodCursor = canvasNeighborhoodAccess.get().cursor();

        for( ; labelsNeighborhoodCursor.hasNext(); )
        {
            labelsNeighborhoodCursor.fwd();
            canvasNeighborhoodCursor.fwd();
            canvasNeighborhoodCursor.localize( dim );

            boolean isInBounds  = true;
            boolean isDifferent = false;

            for ( int d = 0; d < dim.length; ++d ) {
                long currDim = dim[d];
                if (currDim < min[d] || currDim > max[d]) {
                    isInBounds = false;
                    break;
                }
                if ( !isDifferent && dim[d] == seed.getLongPosition(d))
                    continue;
                else
                    isDifferent = true;
            }

            if ( isInBounds && isDifferent )
            {
                LabelMultisetType labelNeighbor = labelsNeighborhoodCursor.get();
                LongType canvasNeighbor = canvasNeighborhoodCursor.get();

                if ((labelNeighbor.contains(seedLabel)) && canvasNeighbor.get() != newLabel) {
                    fillRecursive(labels, canvas, labelsNeighborhoodCursor, seedLabel, newLabel, shape, min, max);
                }
            }
        }

    }


    public static LabelMultisetType generateLabelMultisetTypeFromID( long id, int count )
    {
        VolatileLabelMultisetArray arr = new VolatileLabelMultisetArray(1, true);
        int[] d = arr.data;
        long[] l = arr.listData.data;
        ByteUtils.putInt( count, l, 12 );
        ByteUtils.putLong( id, l, 4 );
        return new LabelMultisetType( arr );
    }


//    public static void main(String[] args) {
//        new ImageJ();
//        long[] dim = new long[]{100, 100};
//        ArrayImg<LongType, LongArray> img = ArrayImgs.longs(dim);
//        ArrayList<LabelMultisetType> multiSetLabelsList = new ArrayList<LabelMultisetType>();
//        for(ArrayCursor<LongType> c = img.cursor(); c.hasNext(); )
//        {
//            c.fwd();
//            c.get().set( 1 + Math.min( Math.max( c.getLongPosition( 0 ) - c.getLongPosition( 1 ), -1 ), 1 ) );
//            multiSetLabelsList.add( generateLabelMultisetTypeFromID( c.get().get(), 1 ) );
//        }
//
//        ListImg<LabelMultisetType> labels = new ListImg<LabelMultisetType>(multiSetLabelsList, dim);
//
//        long fillLabel = 5;
//        ArrayImg<LongType, LongArray> canvas = ArrayImgs.longs(dim);
//        ArrayRandomAccess<LongType> canvasAccess = canvas.randomAccess();
//        for ( int i = 0; i <= dim[0] / 2; ++i )
//        {
//            canvasAccess.setPosition( dim[0]/4 + i, 0);
//            for ( int k = 0; k < 2 ;++k )
//            {
//                canvasAccess.setPosition( dim[1]/4 + k*dim[1]/2, 1 );
//                canvasAccess.get().set( fillLabel );
//            }
//        }
//
//        for ( int i = 1; i <= dim[1] / 2; ++i )
//        {
//            canvasAccess.setPosition( dim[1]/4 + i, 1);
//            for ( int k = 0; k < 2 ;++k )
//            {
//                canvasAccess.setPosition( dim[0]/4 + k*dim[0]/2, 0 );
//                canvasAccess.get().set( fillLabel );
//            }
//        }
//
//        ImageJFunctions.show( canvas.copy(), "canvas-original" );
//
//        ImageJFunctions.show( img.copy(), "labels" );
//
//        fill( labels, canvas, new Point( new long[] { 70, 30 } ), fillLabel, new DiamondShape(1) );
//
//        ImageJFunctions.show( canvas.copy(), "canvas-edited" );
//
//    }



//    public static void main(String[] args) {
//        ArrayImg<LongType, LongArray> img = ArrayImgs.longs(10, 10);
//        for(ArrayCursor<LongType> c = img.cursor(); c.hasNext(); )
//        {
//            c.fwd();
//            c.get().set( 1 + Math.min( Math.max( c.getLongPosition( 0 ) - c.getLongPosition( 1 ), -1 ), 1 ) );
////            c.get().set( c.getLongPosition( 0 ) - c.getLongPosition( 1 ) );
//        }
//
//        new ImageJ();
//        ImageJFunctions.show( img.copy(), "orig" );
//
//        Point seed = new Point( 1, 1 );
//        LongType newLabel = new LongType(5);
//        Shape shape = new DiamondShape(1);
////        shape = new RectangleShape( 1, false );
////        System.out.println( ((NumericType)new LongType(5)).equals((NumericType)new LongType(5) ) );
//        fillRecursive( img, seed, newLabel, shape );
//
//        ImageJFunctions.show( img, "filled" );
//
//    }


}
