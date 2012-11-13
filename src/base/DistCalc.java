package base;

import io.RawSequences;
import utils.Utils;

public class DistCalc {

	private int[][][] rp2c;		// (row, pos) -> col array
	int maxD;
	
	private String[] names;		// to verify compatibility
	
	private boolean verifyNames = true;
	
	public DistCalc() {
	}
	
	public DistCalc(Align from) {
		cacheFrom(from);
	}
	
	public void setVerifyNames(boolean verifyNames) {
		this.verifyNames = verifyNames;
	}
	
	public void cacheFrom(Align from) {
		if(Utils.DEBUG)
			from.checkCons();
		names = from.names;
		
		AlignCol col = from.first;
		int size = col.inds.length;
		rp2c = new int[size][from.cols.size()][];
		maxD = 0;
		for(; col != null; col = col.next) {
			int[] inds = col.inds;
			for(int j = 0; j < size; j++) {
				if(inds[j] >= 0) {
					rp2c[j][inds[j]] = inds;
					maxD++;
				}
			}
		}
		maxD *= size-1;
	}
	
	public int dist(Align from, Align to) {
		cacheFrom(from);
		return dist(to);
	}
	
	public int dist(Align to) {
		if(rp2c == null)
			return -1;
//		if(Utils.DEBUG)
//			to.checkCons();
		if(verifyNames)
			matchNames(names, to.names);
		
		int d = 0;
		for(AlignCol col = to.first; col != null; col = col.next)
			d += dist(col);
		
		return d;
	}
	
	public int dist(AlignCol col) {
		int[] inds = col.inds, inds2;
		int size = inds.length;
		int d = 0, i, j, ch;
		for(i = 0; i < size; i++) {
			if((ch=inds[i]) >= 0) {
				inds2 = rp2c[i][ch];
				for(j = 0; j < size; j++)
					if(inds[j] != inds2[j])
						d++;
			}
		}
		return d;
	}
	
	/**
	 * Calculates the distance contribution of one specified character when
	 * substituted into a given position of a column. Runs in linear time.
	 * 
	 * <p>Adding up the contributions of all characters in a column gives twice the
	 * total distance of the column (as each contributing char-char/char-gap
	 * pair is counted twice)
	 * 
	 * @param inds a column
	 * @param pos position in the column to substitute character into
	 * @param ch the character as index (or -1 for a gap)
	 * @return the distance contribution
	 */
	public int distSingle(int[] inds, int pos, int ch) {
		int size = inds.length;
		int[] inds2;
		int d = 0, i;
		if(ch >= 0) {
			inds2 = rp2c[pos][ch];
			for(i = 0; i < size; i++)
				if(i != pos && (ch=inds[i]) != inds2[i])
					d += ch >= 0 ? 2 : 1;
		} else {
			for(i = 0; i < size; i++)
				if(i != pos && (ch=inds[i]) >= 0 && rp2c[i][ch][pos] != -1)
					d++;
		}
		return d;
	}
	
	public int dist(AlignWin win) {
		int d = 0;
		for(AlignCol col = win.first;; col = col.next) {
			d += dist(col);
			if(col == win.last)
				break;
		}
		return d;
	}
	
	private void matchNames(String[] n1, String[] n2) {
		if(n1.length != n2.length)
			throw new Error("Incompatible alignments with lengths "+n1.length+" and "+n2.length);
		for(int i = 0; i < n1.length; i++)
			if(n1[i].compareToIgnoreCase(n2[i]) != 0)
				throw new Error("Incompatible sequence "+n1[i]+" and "+n2[i]);
	}

	public void printDistData(Align al) {
		int d = dist(al);
		System.out.println(d+"\t"+dist2acc(d));//+"\t"+maxD*(lens.length-1));
	}
	
	public double dist2acc(int d) {
		return 1-(double)d/maxD;
	}
	
	public int acc2dist(double acc) {
		return (int)(maxD*(1-acc)+.5);
	}
	
	public static void main(String[] args) {
		RawSequences raw = new RawSequences();
		raw.add("A", "-A-B");
		raw.add("C", "C-D-");
		raw.add("E", "-EF-");
		Align ref = new Align(raw);
		
		raw.clear();
		raw.add("A", "A--B");
		raw.add("C", "-CD-");
		raw.add("E", "EF--");
		Align t1 = new Align(raw);
		
		raw.clear();
		raw.add("A", "-AB");
		raw.add("C", "CD-");
		raw.add("E", "E-F");
		Align t2 = new Align(raw);
		
		DistCalc dc = new DistCalc(ref);
		int d = dc.dist(t1);
		System.out.println(d+" "+dc.dist2acc(d));
		d = dc.dist(t2);
		System.out.println(d+" "+dc.dist2acc(d));
		d = dc.dist(ref);
		System.out.println(d+" "+dc.dist2acc(d));
		AlignCol col = t1.first.next.next;
		int d1 = dc.dist(col);
		int d2 = dc.distSingle(col.inds, 2, col.inds[2]);
		System.out.println(col.toString(t1)+" "+d1+" "+d2);
	}
}
