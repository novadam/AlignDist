package base;


/**
 * Represents one column of an {@link Align}ment.
 * @author novadam
 */
public class AlignCol {
	int[] inds;			// alignment column as array of character indices (-1 for gaps)
	AlignCol prev;		// reference to previous alignment column
	AlignCol next;		// reference to next alignment column
	int ord;			// column ordinal (index in Align.cols)
	int singOrd = -1;	// column single non-gap ordinal (index in Align.singCols or -1)
	
	public AlignCol(AlignCol prev, int ord, int size) {
		if(prev != null) {
			this.prev = prev;
			prev.next = this;
		}
		this.ord = ord;
		inds = new int[size];
	}
	
	public AlignCol(int[] inds) {
		this.inds = inds;
	}

	public int nonGaps() {
		int nongaps = 0, len = inds.length;
		for(int i = 0; i < len; i++)
			if(inds[i] >= 0)
				nongaps++;
		return nongaps;
	}
		
	public String toString(Align align) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < inds.length; i++) {
			int ch = inds[i];
			if(ch >= 0)
				sb.append(align.seqs[i].charAt(ch));
			else
				sb.append('-');
		}
		return sb.toString();
	}
}
