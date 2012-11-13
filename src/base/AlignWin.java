package base;

import java.util.List;

import utils.Utils;

/**
 * Represents a window (a set of alignment columns) within an alignment. Provides efficient
 * operations to replace itself with a realigned window.
 *  
 * @author novadam
 */
public class AlignWin {
	Align align;
	AlignCol first;
	AlignCol last;
	int len;

	public AlignWin(Align align, int len) {
		this.align = align;
		this.len = len;
	}
	
	/**
	 * Returns an array with copies of the column index arrays of this window.
	 * Allows efficient realignment operations that do not change the number of columns.
	 */
	public int[][] getCols() {
		int[][] copy = new int[len][];
		AlignCol col = first;
		for(int i = 0; i < len; i++, col = col.next)
			copy[i] = Utils.copyOf(col.inds);
		return copy;
	}
	
	/**
	 * Replaces window with the supplied window within the alignment. New window
	 * columns must already be linked both ways with each other.
	 * Updates all other references in the alignment.
	 * @param other window to replace this window with
	 */
	public void replaceWith(AlignWin other) {
		// integrate new window into pointer list
		AlignCol col = first.prev;
		if(col != null) {
			other.first.prev = col;
			col.next = other.first;
		} else {
			align.first = other.first;
		}
		col = last.next;
		if(col != null) {
			other.last.next = col;
			col.prev = other.last;
		}
		
		// update unordered column array
		col = first;
		AlignCol ocol = other.first;
		List<AlignCol> cols = align.cols;
		// (1) replace 'overlapping' columns
		int overlap = Math.min(len, other.len), i, ord;
		for(i = 0; i < overlap; i++, col = col.next, ocol = ocol.next) {
			ord = ocol.ord = col.ord;
			cols.set(ord, ocol);
		}
		// (2) delete remaining old columns
		for(; i < len; i++, col = col.next)
			align.updateUnordDel(col);
		// (3) or add remaining new columns
		int clast = cols.size()-1;
		for(; i < other.len; i++, ocol = ocol.next) {
			ocol.ord = ++clast;
			cols.add(ocol);
		}
	}
	
	/**
	 * Replaces alignment columns within window with the supplied columns. Provides a
	 * more efficient alternative to {@link #replaceWith(AlignWin)} when the number of
	 * columns does not change.
	 * @param cols alignment columns to replace contents of this window with
	 * @throws Error when <code>cols.length</code> does not agree with <code>this.len</code>
	 */
	public void replaceWith(int[][] cols) {
		if(cols.length != len)
			throw new Error("Window length does not agree with the size of new column array");
		AlignCol col = first;
		for(int i = 0; i < cols.length; i++, col = col.next)
			col.inds = cols[i];
	}
	
}
