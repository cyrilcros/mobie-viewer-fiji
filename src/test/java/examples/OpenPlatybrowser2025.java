package examples;

import net.imagej.ImageJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;

import java.io.IOException;

/**
 * Opens the platybrowser-project-2025 MoBIE project, optionally at a specific
 * git branch.
 *
 * Usage (program arguments): [branch]
 *   e.g. "muscles"  -> opens https://github.com/cyrilcros/platybrowser-project-2025 @ muscles
 *        (empty)    -> opens the default branch "main"
 *
 * Available branches on cyrilcros/platybrowser-project-2025:
 *   adding_alyona, adding_images_test, adding_views, altering_probe_ui,
 *   bookmarkstest, coloring_experiments, cross_reference, cyril_updating_patch,
 *   david, david_experimental, david_old_traces, demo_asli,
 *   demo_mobie_structure, demo_mobie_structure_harsh, detlev, detlev_inquiry,
 *   experiment_default_tsv, experiments_ongoing, main, merging_morphofeatures,
 *   mobie, mobie-py, more-validation, muscles, new-s3, normal-vie, normal2,
 *   pre-commit, propagate_information, renaming_views, simplified_syntax,
 *   spec-v2, start_point_snakemake, traces, traces-clean, ui_improvement,
 *   value-limits, variations_david, xray
 */
public class OpenPlatybrowser2025
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";

	public static void main( String[] args ) throws IOException
	{
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		final String branch = args.length > 0 ? args[ 0 ] : "main";

		final MoBIESettings settings = new MoBIESettings()
				.gitProjectBranch( branch );

		System.out.println( "Opening " + PROJECT + " @ branch: " + branch );

		final MoBIE moBIE = new MoBIE( PROJECT, settings );
	}
}
