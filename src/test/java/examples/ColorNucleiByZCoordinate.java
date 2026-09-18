package examples;

import net.imagej.ImageJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;
import org.embl.mobie.lib.color.ColoringModels;
import org.embl.mobie.lib.color.MoBIEColoringModel;
import org.embl.mobie.lib.color.NumericAnnotationColoringModel;
import org.embl.mobie.lib.color.lut.LUTs;
import org.embl.mobie.lib.serialize.display.SegmentationDisplay;
import org.embl.mobie.lib.table.AnnotationTableModel;
import org.embl.mobie.lib.view.ViewManager;

import java.util.List;

/**
 * Opens a MoBIE project and renders the nuclei segmentation as a numeric
 * annotation, coloured by the per-nucleus z-coordinate using the viridis LUT.
 *
 * Usage (program arguments):
 *   [branch]          -> project branch (default "main")
 *   [view]            -> view to open (default "nuclei")
 *   [z-column]        -> numeric z-coordinate column (default "anchor_z")
 *
 * This mirrors the GUI's Color > Color by column, driven programmatically.
 */
public class ColorNucleiByZCoordinate
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";

	/** View that contains the nuclei segmentation display. */
	public static final String VIEW = "nuclei";

	/** Substring (case-insensitive) used to find the nuclei segmentation display. */
	public static final String SEGMENTATION_DISPLAY = "nuclei";

	/** Default z-coordinate column; auto-detected if it doesn't match. */
	public static final String Z_COLUMN = "anchor_z";

	public static void main( String[] args ) throws Exception
	{
		final String branch = args.length > 0 ? args[ 0 ] : "main";
		final String view = args.length > 1 ? args[ 1 ] : VIEW;
		final String zColumn = args.length > 2 ? args[ 2 ] : Z_COLUMN;

		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		final MoBIESettings settings = new MoBIESettings()
				.gitProjectBranch( branch )
				.view( view );

		final MoBIE moBIE = new MoBIE( PROJECT, settings );

		colorNucleiByZCoordinate( moBIE, zColumn );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static void colorNucleiByZCoordinate( MoBIE moBIE, String requestedZColumn )
	{
		final ViewManager viewManager = moBIE.getViewManager();
		final List< SegmentationDisplay > segmentationDisplays = viewManager.getCurrentSegmentationDisplays();

		if ( segmentationDisplays.isEmpty() )
			throw new RuntimeException(
					"No segmentation displays found in the current view. "
							+ "Open a view that contains a segmentation (e.g. view \"" + VIEW + "\")." );

		// Find the nuclei segmentation display, falling back to the first one.
		SegmentationDisplay nucleiDisplay = null;
		for ( SegmentationDisplay display : segmentationDisplays )
		{
			if ( display.getName() != null && display.getName().toLowerCase().contains( SEGMENTATION_DISPLAY ) )
			{
				nucleiDisplay = display;
				break;
			}
		}
		if ( nucleiDisplay == null )
		{
			nucleiDisplay = segmentationDisplays.get( 0 );
			System.out.println( "[WARNING] No display matching '" + SEGMENTATION_DISPLAY + "'; using: " + nucleiDisplay.getName() );
		}

		System.out.println( "Coloring segmentation display: " + nucleiDisplay.getName() );

		final AnnotationTableModel tableModel = ( AnnotationTableModel ) nucleiDisplay.getAnnData().getTable();

		final String column = resolveZColumn( tableModel, requestedZColumn );
		System.out.println( "Numeric column: " + column + ", LUT: " + LUTs.VIRIDIS );

		final NumericAnnotationColoringModel numericModel =
				ColoringModels.createNumericModel( column, LUTs.VIRIDIS, tableModel.getMinMax( column ) );

		final MoBIEColoringModel coloringModel = nucleiDisplay.coloringModel;
		coloringModel.setColoringModel( numericModel );
		coloringModel.setOpacityNotSelected( 1.0 );
	}

	private static String resolveZColumn( AnnotationTableModel< ? > tableModel, String requested )
	{
		final List< String > columns = tableModel.columnNames();

		for ( String c : columns )
			if ( c.equalsIgnoreCase( requested ) )
				return c;

		// Preferred z-coordinate names first.
		for ( String preferred : new String[] { "anchor_z", "z", "z_position", "z_coordinate" } )
			for ( String c : columns )
				if ( c.equalsIgnoreCase( preferred ) )
					return c;

		for ( String c : columns )
			if ( c.toLowerCase().endsWith( "_z" ) )
				return c;

		throw new RuntimeException( "Could not find a z-coordinate column. Available columns: " + columns );
	}
}
