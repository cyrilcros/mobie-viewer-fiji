package examples;

import bdv.viewer.Source;
import net.imagej.ImageJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;
import org.embl.mobie.lib.annotation.AnnotatedSegment;
import org.embl.mobie.lib.data.DataStore;
import org.embl.mobie.lib.image.AnnotatedLabelImage;
import org.embl.mobie.lib.serialize.DataSource;
import org.embl.mobie.lib.serialize.SegmentationDataSource;
import org.embl.mobie.lib.source.AnnotationType;
import org.embl.mobie.lib.table.AnnData;
import org.embl.mobie.lib.util.MoBIEHelper;
import org.embl.mobie.lib.volume.MeshCreator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Diagnostic runner: loads every trace (segmentation) source of
 * platybrowser-project-2025 and tries to build the 3D mesh for each trace,
 * logging which traces fail and why.
 * <p>
 * Run from IntelliJ via {@code main()}. Arguments (all optional):
 * <ol>
 *   <li>git branch of the project (default {@code main})</li>
 *   <li>source-name substring to select traces (default {@code trace})</li>
 *   <li>report path (default {@code trace_load_report.tsv})</li>
 * </ol>
 * Errors are contained per source and per trace: one bad trace never aborts
 * the run. Progress goes to stdout (flushed) and outcomes to the TSV report.
 * <p>
 * Note: MoBIE and BDV-Playground also write to the ImageJ Log window
 * ({@code Plugins > Log}); this runner mirrors what matters to stdout. The
 * {@code src/test/resources/logback-test.xml} keeps Logback at WARN so the
 * console is not drowned in DEBUG output.
 */
public class LoadAllTraces
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";

	private static final int MESH_SMOOTHING_ITERATIONS = 5;
	private static final long MAX_NUM_SEGMENT_VOXELS = 100L * 100 * 100;
	private static final int PROGRESS_EVERY = 50;

	public static void main( String[] args ) throws Exception
	{
		final String branch = args.length > 0 ? args[ 0 ] : "main";
		final String nameFilter = args.length > 1 ? args[ 1 ] : "trace";
		final Path report = Paths.get( args.length > 2 ? args[ 2 ] : "trace_load_report.tsv" );

		log( "================================================================" );
		log( " LoadAllTraces" );
		log( "   project: " + PROJECT );
		log( "   branch:  " + branch );
		log( "   filter:  " + nameFilter );
		log( "   report:  " + report.toAbsolutePath() );
		log( "================================================================" );

		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		log( "[1/3] Opening project ..." );
		final MoBIE moBIE = new MoBIE( PROJECT, new MoBIESettings().gitProjectBranch( branch ) );
		log( "      project opened." );

		final List< SegmentationDataSource > traceSources = new ArrayList<>();
		for ( final DataSource ds : moBIE.getDataset().sources().values() )
		{
			if ( ds instanceof SegmentationDataSource
					&& ds.getName().toLowerCase().contains( nameFilter.toLowerCase() ) )
				traceSources.add( ( SegmentationDataSource ) ds );
		}
		traceSources.sort( Comparator.comparing( DataSource::getName ) );

		log( "[2/3] Found " + traceSources.size() + " source(s) matching '" + nameFilter + "':" );
		for ( final DataSource ds : traceSources )
			log( "        - " + ds.getName() );

		final MeshCreator< AnnotatedSegment > meshCreator =
				new MeshCreator<>( MESH_SMOOTHING_ITERATIONS, MAX_NUM_SEGMENT_VOXELS );

		int total = 0;
		int rendered = 0;
		int skippedNoVoxels = 0;
		int failed = 0;

		try ( final PrintWriter out = new PrintWriter( new BufferedWriter( new FileWriter( report.toFile() ) ) ) )
		{
			out.println( "source\tlabel\tstatus\treason" );

			for ( int s = 0; s < traceSources.size(); s++ )
			{
				final SegmentationDataSource ds = traceSources.get( s );
				log( "" );
				log( "[3/3] (" + ( s + 1 ) + "/" + traceSources.size() + ") source " + ds.getName() );

				if ( ds.getTableData() == null )
				{
					log( "      WARNING: source has no table data; skipping." );
					out.println( ds.getName() + "\t-\tNO_TABLE\tno table data" );
					continue;
				}

				final long t0 = System.currentTimeMillis();
				int sourceTotal = 0;
				int sourceRendered = 0;
				int sourceSkipped = 0;
				int sourceFailed = 0;

				try
				{
					log( "      loading table + image ..." );
					ds.preInit( true );
					moBIE.initDataSources( Collections.singletonList( ( DataSource ) ds ) );

					final AnnotatedLabelImage< ? > image = ( AnnotatedLabelImage< ? > ) DataStore.getImage( ds.getName() );

					@SuppressWarnings( "unchecked" )
					final Source< AnnotationType< AnnotatedSegment > > source =
							( Source< AnnotationType< AnnotatedSegment > > ) ( Source< ? > ) image.getSourcePair().getSource();

					// Force the image open now, so image-level errors surface per source.
					source.getSource( 0, 0 ).randomAccess();

					double[] spacing = null;
					final ArrayList< double[] > spacings = MoBIEHelper.getVoxelSpacings( source );
					if ( spacings != null && !spacings.isEmpty() )
						spacing = spacings.get( 0 );

					final AnnData< ? > annData = image.getAnnData();
					final ArrayList< ? > traces = annData.getTable().annotations();

					log( "      loaded in " + ( System.currentTimeMillis() - t0 ) + " ms"
							+ " | traces: " + traces.size()
							+ " | finest spacing: " + Arrays.toString( spacing ) );
					log( "      rendering meshes ..." );

					for ( final Object annotation : traces )
					{
						total++;
						sourceTotal++;
						final AnnotatedSegment trace = ( AnnotatedSegment ) annotation;
						try
						{
							meshCreator.createSmoothCustomTriangleMesh( trace, spacing, true, source );
							rendered++;
							sourceRendered++;
						}
						catch ( final Exception e )
						{
							final String reason = rootReason( e );
							if ( isNoVoxels( e ) )
							{
								skippedNoVoxels++;
								sourceSkipped++;
								out.println( ds.getName() + "\t" + trace.label() + "\tSKIP_NO_VOXELS\t" + clean( reason ) );
							}
							else
							{
								failed++;
								sourceFailed++;
								out.println( ds.getName() + "\t" + trace.label() + "\tFAILED\t" + clean( reason ) );
								out.flush();
								log( "      [FAIL] label " + trace.label() + ": " + reason );
							}
						}

						if ( sourceTotal % PROGRESS_EVERY == 0 )
							log( "      ... " + sourceTotal + "/" + traces.size()
									+ " (ok=" + sourceRendered + ", skip=" + sourceSkipped + ", fail=" + sourceFailed + ")" );
					}
				}
				catch ( final Throwable t )
				{
					failed++;
					sourceFailed++;
					out.println( ds.getName() + "\t-\tSOURCE_FAILED\t" + clean( rootReason( t ) ) );
					out.flush();
					log( "      [SOURCE FAILED] " + rootReason( t ) );
				}

				log( "      done: " + sourceTotal + " traces in " + ( System.currentTimeMillis() - t0 ) + " ms"
						+ " (ok=" + sourceRendered + ", skip=" + sourceSkipped + ", fail=" + sourceFailed + ")" );
			}
		}

		log( "" );
		log( "================ SUMMARY ================" );
		log( "sources:           " + traceSources.size() );
		log( "traces:            " + total );
		log( "  rendered:        " + rendered );
		log( "  no voxels (skip): " + skippedNoVoxels );
		log( "  failed:          " + failed );
		log( "report:            " + report.toAbsolutePath() );
	}

	private static void log( final String message )
	{
		System.out.println( message );
		System.out.flush();
	}

	private static boolean isNoVoxels( final Throwable t )
	{
		for ( Throwable cause = t; cause != null; cause = cause.getCause() )
			if ( cause.getMessage() != null && cause.getMessage().contains( "has no voxels in the image volume" ) )
				return true;
		return false;
	}

	private static String rootReason( final Throwable t )
	{
		Throwable root = t;
		while ( root.getCause() != null && root.getCause() != root )
			root = root.getCause();
		final String message = root.getMessage() != null ? root.getMessage() : root.toString();
		return root.getClass().getSimpleName() + ": " + message;
	}

	private static String clean( final String s )
	{
		return s == null ? "" : s.replace( '\t', ' ' ).replace( '\n', ' ' ).replace( '\r', ' ' );
	}
}
