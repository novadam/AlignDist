package base;

import io.RawSequences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import utils.Utils;

/**
 * Alignment representation with support for efficient local realignment operations.
 * 
 * @author novadam
 */
public class Align {

	public String[] names;		// sequence names (after lexicographic sorting)
	public String[] seqs;		// sequence characters (in lexicographic order of names)
	public AlignCol first;		// reference to first column
	public List<AlignCol> cols;			// unordered array of all columns (for efficient random column selection)
	public List<AlignCol> singCols;		// unordered array of all 'singular' columns (single non-gap char)
										// (for efficient random column merge selection)
	
	public Align(final RawSequences raw) {
		int size = raw.size(), len = raw.len();
		
		if(len == -1)
			throw new Error("Sequences are unaligned!");
		
		Integer[] ord = new Integer[size];
		for(int i = 0; i < size; i++)
			ord[i] = i;
		
		Arrays.sort(ord, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return raw.getSeqName(o1).compareTo(raw.getSeqName(o2));
			}
		});
		
		names = new String[size];
		for(int i = 0; i < size; i++)
			names[i] = raw.getSeqName(ord[i]);
		
		seqs = new String[size];
		cols = new ArrayList<AlignCol>(len);
		singCols = new ArrayList<AlignCol>();
		AlignCol prev = null;
		for(int i = 0; i < len; i++) {
			prev = new AlignCol(prev, i, size);
			cols.add(prev);
		}
		first = cols.get(0);
		char ch;
		int ind;
		for(int i = 0; i < size; i++) {
			String seq = raw.getSequence(ord[i]);
			StringBuilder sb = new StringBuilder();
			for(int j = 0; j < len; j++) {
				ch = seq.charAt(j);
				if(ch != '-') {
					ind = sb.length();
					sb.append(ch);
				} else {
					ind = -1;
				}
				cols.get(j).inds[i] = ind;
			}
			seqs[i] = sb.toString();
		}
		for(int i = 0; i < len; i++) {
			while(i < len && updateSing(prev=cols.get(i)) == 0) {
				removeCol(prev);
				len--;
				System.out.println("Warning: gap-only column found and removed");
			}
		}
	}
	
	/**
	 * Randomly selects an alignment window of length <b>len</b>.
	 * Random numbers are taken from {@link Utils#generator}.
	 * @param len window length
	 * @return alignment window represented by an {@link AlignWin} object
	 */
	public AlignWin randWin(int len) {
		AlignWin win = new AlignWin(this, len);
		do {
			int ind = Utils.generator.nextInt(cols.size());
			win.first = win.last = cols.get(ind);
			for(int i = 0; i < len-1 && win.last != null; i++)
				win.last = win.last.next;
		} while(win.last == null);
		return win;
	}
	
	/**
	 * Randomly selects an alignment column.
	 * Random numbers are taken from {@link Utils#generator}.
	 * @return alignment column represented by an {@link AlignCol} object
	 */
	public AlignCol randCol() {
		int ind = Utils.generator.nextInt(cols.size());
		return cols.get(ind);
	}

	/**
	 * Randomly selects a singular alignment column (i.e. one with a single non-gap character).
	 * Random numbers are taken from {@link Utils#generator}.
	 * @return alignment column represented by an {@link AlignCol} object or <code>null</code> if none exist
	 */
	public AlignCol randSingCol() {
		if(singCols.size() == 0)
			return null;
		int ind = Utils.generator.nextInt(singCols.size());
		return singCols.get(ind);
	}

	/**
	 * Inserts column into alignment after a specified column and updates all references.
	 * @param col column to be inserted
	 * @param prev column after which to insert <code>col</code> (or <code>null</code>
	 *   if column should be the first one of the alignment)
	 */
	public void insertCol(AlignCol col, AlignCol prev) {
		// update links
		col.prev = prev;
		if(prev != null) {
			col.next = prev.next;
			prev.next = col;
		} else {
			col.next = first;
			first = col;
		}
		if(col.next != null)
			col.next.prev = col;
		
		// update unordered arrays
		col.ord = cols.size();
		cols.add(col);
		updateSing(col);
	}
	
	/**
	 * Removes column from the alignment and updates all references.
	 * @param col column to be removed
	 */
	public void removeCol(AlignCol col) {
		// update links
		if(col.prev != null)
			col.prev.next = col.next;
		else
			first = col.next;
		if(col.next != null)
			col.next.prev = col.prev;
		
		updateUnordDel(col);
	}
	
	/**
	 * Updates the unordered {@link #cols} and {@link #singCols} arrays after removal
	 * of a column.
	 * @param col the column that has just been removed from the columns linked list
	 */
	void updateUnordDel(AlignCol col) {
		// update column array
		int ord = col.ord, clast = cols.size()-1;
		if(ord != clast) {		// if deleting in the middle move last col forward
			AlignCol tcol = cols.get(clast);
			tcol.ord = ord;
			cols.set(ord, tcol);
		}
		cols.remove(clast);

		// update 'singular' column array
		singDel(col);
	}
	
	private void singDel(AlignCol col) {
		int ord = col.singOrd;
		if(ord >= 0) {
			int clast = singCols.size()-1;
			if(ord != clast) {
				AlignCol tcol = singCols.get(clast);
				tcol.singOrd = ord;
				singCols.set(ord, tcol);
			}
			singCols.remove(clast);
			col.singOrd = -1;
		}
	}
	
	/**
	 * Updates the unordered {@link #singCols} array after a column has been changed/inserted.
	 * @param col the column that has changed
	 * @return the number of non-gap characters in the column
	 */
	int updateSing(AlignCol col) {
		int nongaps = col.nonGaps();
		if(nongaps == 1) {
			if(col.singOrd == -1) {
				col.singOrd = singCols.size();
				singCols.add(col);
			}
		} else {
			singDel(col);
		}
		return nongaps;
	}
	
	public RawSequences toRaw() {
		if(Utils.DEBUG)
			checkCons();
		RawSequences raw = new RawSequences();

		for(int i = 0; i < names.length; i++) {
			StringBuilder sb = new StringBuilder();
			
			for(AlignCol col = first; col != null; col = col.next) {
				sb.append(col.inds[i] >= 0 ? seqs[i].charAt(col.inds[i]) : '-');
			}
			
			raw.add(names[i], sb.toString());
		}
		
		return raw;
	}
	
	@Override
	public String toString() {
		return toRaw().toString();
	}
	
	/**
	 * Checks alignment representation consistency.
	 */
	public void checkCons() {
		int[] poss = new int[first.inds.length];

		int n = 0, sings = 0;
		for(AlignCol col = first; col != null; col = col.next, n++) {
			int[] inds = col.inds;
			for(int i = 0; i < inds.length; i++) {
				if(inds[i] >= 0) {
					if(poss[i] != inds[i])
						throw new Error("Inconsistency in alignment column "+(n+1)+" at seq "+(i+1)+" character "+(poss[i]+1));
					poss[i]++;
				}
			}
			if(col.ord < 0 || col.ord >= cols.size())
				throw new Error("Bad ordinal "+col.ord+" for alignment column "+(n+1)+": "+col.toString(this));
			if(cols.get(col.ord) != col)
				throw new Error("Inconsistency in unordered column array for column "+(n+1)+" ordinal "+col.ord+": "+col.toString(this));
			int nongaps = col.nonGaps();
			if(nongaps == 0)
				throw new Error("Gap-only column "+(n+1));
			if((nongaps == 1) ^ (col.singOrd >= 0))
				throw new Error("Inconsistency between singOrd and singularity for column "+(n+1)+": "+col.toString(this));
			if(col.singOrd >= 0) {
				if(col.singOrd >= singCols.size())
					throw new Error("Bad singular ordinal "+col.singOrd+" for alignment column "+(n+1)+": "+col.toString(this));
				if(singCols.get(col.singOrd) != col)
					throw new Error("Inconsistency in singular column array for column "+(n+1)+" ordinal "+col.singOrd+": "+col.toString(this));
				sings++;
			}
		}
		if(cols.size() != n)
			throw new Error("Alignment length mismatch");
		if(singCols.size() != sings)
			throw new Error("Singular columns count mismatch");
		for(int i = 0; i < seqs.length; i++)
			if(poss[i] != seqs[i].length())
				throw new Error("Sequence length mismatch for seq "+(i+1));
	}
	
	public static void main(String[] args) {
		RawSequences raw = new RawSequences();
		raw.add("A", "-A--B");
		raw.add("E", "-EF--");
		raw.add("C", "C-D--");
		Align a = new Align(raw);
		System.out.print(a);
		System.out.println("Singular columns: ");
		for(AlignCol col : a.singCols)
			System.out.println(col.toString(a));
	}
}
