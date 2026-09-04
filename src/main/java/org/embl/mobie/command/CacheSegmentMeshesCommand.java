/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2024 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.embl.mobie.command;

import ij.IJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.lib.serialize.display.SegmentationDisplay;
import org.embl.mobie.lib.volume.SegmentMeshCacher;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-renders and caches the segment meshes of one or all segmentation
 * displays of the currently open MoBIE view, at a user-chosen resolution.
 * <p>
 * Works with any segmentation data source; no segmentation name is hard-coded.
 * An empty segmentation name caches all segmentation displays of the current
 * view.
 */
@Plugin( type = Command.class, menuPath = CommandConstants.MOBIE_PLUGIN_ROOT + "Segments>Cache Segment Meshes..." )
public class CacheSegmentMeshesCommand implements Command
{
	static { net.imagej.patcher.LegacyInjector.preinit(); }

	@Parameter( label = "Segmentation (optional; empty = all in the current view)", required = false )
	public String segmentationName = "";

	@Parameter( label = "Voxel spacing (um); 0 = auto (finest cached or current)", style = "format:#0.000", required = false )
	public double voxelSpacingUm = 0.0;

	@Override
	public void run()
	{
		final MoBIE moBIE = MoBIE.getInstance();
		if ( moBIE == null )
		{
			IJ.log( "[MoBIE] No project is open; cannot cache segment meshes." );
			return;
		}

		final List< SegmentationDisplay > targets = matchingDisplays(
				new ArrayList<>( moBIE.getViewManager().getCurrentSegmentationDisplays() ) );

		if ( targets.isEmpty() )
		{
			IJ.log( "[MoBIE] No segmentation display matching \"" + segmentationName + "\" is open in the current view." );
			return;
		}

		final double spacing = voxelSpacingUm;
		new Thread( () ->
		{
			for ( SegmentationDisplay display : targets )
			{
				try
				{
					final int newlyCached = SegmentMeshCacher.cacheSegmentsAt( display, spacing );
					IJ.log( "[MoBIE] Cached " + newlyCached + " new meshes for \"" + display.getName() + "\""
							+ ( spacing > 0 ? " at " + spacing + " um" : " (auto resolution)" ) + "." );
				}
				catch ( Exception e )
				{
					IJ.log( "[MoBIE] Mesh caching failed for \"" + display.getName() + "\": " + e.getMessage() );
				}
			}
		} ).start();
	}

	private List< SegmentationDisplay > matchingDisplays( List< SegmentationDisplay > displays )
	{
		if ( segmentationName == null || segmentationName.isEmpty() )
			return displays;

		final List< SegmentationDisplay > targets = new ArrayList<>();
		for ( SegmentationDisplay display : displays )
			if ( ( display.getName() != null && display.getName().equalsIgnoreCase( segmentationName ) )
					|| ( display.getSources() != null && display.getSources().contains( segmentationName ) ) )
				targets.add( display );
		return targets;
	}
}
