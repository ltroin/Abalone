package edu.rit.datacom.abalone.common;

import java.io.Serializable;

/**
 * Represents an immutable player move.
 */
public class Move implements Serializable {

	private static final long serialVersionUID = 5088432699229762217L;

	private final int color;
	private final int direction;
	private final int[][] tiles;

	public Move(int color, int direction, int[][] tiles) {
		this.color = color;
		this.direction = direction;
		this.tiles = tiles;
	}

	public int getColor() {
		return color;
	}

	public int getDirection() {
		return direction;
	}

	public int[][] getMarbles() {
		return tiles.clone();
	}

}
