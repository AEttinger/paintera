package org.janelia.saalfeldlab.paintera.meshes;

import eu.mihosoft.jcsg.ext.openjfx.shape3d.PolygonMesh;

public class MeshUtils
{
	/**
	 * Creates a 3D box built up of quadrilateral faces. Uses {@link PolygonMesh} class from FXyz library.
	 * Based on {@link https://stackoverflow.com/a/47169258/7506407}.
	 *
	 * @param width
	 * @param height
	 * @param depth
	 * @return
	 */
	public static PolygonMesh createQuadrilateralMesh(final float width, final float height, final float depth)
	{
		final float L = 2f * width + 2f * depth;
		final float H = height + 2f * depth;
		final float hw = width/2f, hh = height/2f, hd = depth/2f;

		final float[] points = new float[] {
				 hw,  hh, hd,             hw,  hh, -hd,
				 hw, -hh, hd,             hw, -hh, -hd,
				-hw,  hh, hd,            -hw,  hh, -hd,
				-hw, -hh, hd,            -hw, -hh, -hd
			};

		final float[] texCoords = new float[] {
				depth / L, 0f,                              (depth + width) / L, 0f,
				0f, depth / H,                              depth / L, depth / H,
				(depth + width) / L, depth / H,             (2f * depth + width) / L, depth/H,
				1f, depth / H,                              0f, (depth + height) / H,
				depth / L, (depth + height)/H,              (depth + width) / L, (depth + height) / H,
				(2f * depth + width) / L, (depth + height) / H,  1f, (depth + height) / H,
				depth / L, 1f,                              (depth + width) / L, 1f
			};

		final int[][] faces = new int[][] {
				{0, 8, 2, 3, 3, 2, 1, 7},
				{4, 9, 5, 10, 7, 5, 6, 4},
				{0, 8, 1, 12, 5, 13, 4, 9},
				{2, 3, 6, 4, 7, 1, 3, 0},
				{0, 8, 4, 9, 6, 4, 2, 3},
				{1, 11, 3, 6, 7, 5, 5, 10}
			};

		final int[] smooth = new int[] {
				1, 2, 3, 4, 5, 6
			};

		final PolygonMesh mesh = new PolygonMesh(points, texCoords, faces);
		mesh.getFaceSmoothingGroups().addAll(smooth);

		return mesh;
	}
}
