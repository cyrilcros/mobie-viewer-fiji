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
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
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
package org.embl.mobie.lib.volume;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.creation.GltfModelBuilder;
import de.javagl.jgltf.model.creation.MeshPrimitiveBuilder;
import de.javagl.jgltf.model.impl.DefaultMeshModel;
import de.javagl.jgltf.model.impl.DefaultMeshPrimitiveModel;
import de.javagl.jgltf.model.impl.DefaultNodeModel;
import de.javagl.jgltf.model.impl.DefaultSceneModel;
import de.javagl.jgltf.model.io.GltfModelWriter;
import ij.IJ;
import org.embl.mobie.lib.annotation.Segment;
import org.embl.mobie.lib.serialize.display.SegmentationDisplay;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Exports the smoothed meshes of a segmentation display as a glTF 2.0 asset:
 * a {@code .gltf} JSON file plus an external {@code .bin} buffer (referenced by
 * URI), with one mesh per segment. The result can be opened in Blender,
 * BigVolumeViewer and other glTF-capable tools.
 * <p>
 * The external-buffer (rather than single {@code .glb}) form is used because
 * glTF's 32-bit buffer offsets cap a single buffer / {@code .glb} at ~4 GiB,
 * which large segmentations can exceed.
 */
public final class GltfMeshExporter
{
	private GltfMeshExporter()
	{}

	/**
	 * Exports all segments of the given display to the given {@code .gltf}
	 * file. The binary buffer is written next to it and referenced by URI.
	 *
	 * @param display    segmentation display whose segment meshes to export
	 * @param outputFile target {@code .gltf} file
	 * @return the number of meshes that were written
	 * @throws IOException if the glTF asset could not be written
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static int export( SegmentationDisplay display, File outputFile ) throws IOException
	{
		if ( display.segmentVolumeViewer == null )
			throw new IllegalStateException(
					"The 3D segment viewer is not initialised for display \"" + display.getName() + "\"." );

		final SegmentVolumeViewer viewer = display.segmentVolumeViewer;
		final Collection< Segment > segments = new ArrayList<>( ( Collection ) segments( display ) );

		final DefaultSceneModel scene = new DefaultSceneModel();
		int exported = 0;
		for ( final Segment segment : segments )
		{
			final float[] vertices = viewer.getSmoothedMeshVertices( segment );
			if ( vertices == null || vertices.length == 0 )
				continue;

			final MeshPrimitiveBuilder primitiveBuilder = MeshPrimitiveBuilder.create();
			primitiveBuilder.addPositions3D( FloatBuffer.wrap( vertices ) );
			final DefaultMeshPrimitiveModel primitive = primitiveBuilder.build();

			final String name = segment.imageId() + ";" + segment.label();

			final DefaultMeshModel meshModel = new DefaultMeshModel();
			meshModel.setName( name );
			meshModel.addMeshPrimitiveModel( primitive );

			final DefaultNodeModel nodeModel = new DefaultNodeModel();
			nodeModel.setName( name );
			nodeModel.addMeshModel( meshModel );

			scene.addNode( nodeModel );
			exported++;
		}

		final GltfModelBuilder builder = GltfModelBuilder.create();
		builder.addSceneModel( scene );
		final GltfModel gltfModel = builder.build();

		new GltfModelWriter().write( gltfModel, outputFile );

		IJ.log( "[MoBIE] Exported " + exported + " segment meshes to " + outputFile.getAbsolutePath() );
		return exported;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static Collection segments( SegmentationDisplay display )
	{
		if ( display.getAnnData() != null && display.getAnnData().getTable() != null )
			return new ArrayList( display.getAnnData().getTable().annotations() );
		return display.selectionModel.getSelected();
	}
}
