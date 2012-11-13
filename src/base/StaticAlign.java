package base;

import io.RawSequences;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Simple column-based alignment representation.
 * 
 * @author novadam
 */
public class StaticAlign {

	public String[] names;
	public String[] seqs;
	public byte[][] cols;
	
	public StaticAlign(final RawSequences raw) {
		int size = raw.size(), len = raw.len();
		
		if(len == -1)
			throw new Error("Sequences are unaligned!");

		Integer[] ind = new Integer[size];
		for(int i = 0; i < size; i++)
			ind[i] = i;
		
		Arrays.sort(ind, new Comparator<Integer>() {
			public int compare(Integer o1, Integer o2) {
				return raw.getSeqName(o1).compareTo(raw.getSeqName(o2));
			}
		});
		
		names = new String[size];
		for(int i = 0; i < size; i++)
			names[i] = raw.getSeqName(ind[i]);
		
		cols = new byte[len][size];
		seqs = new String[size];
		for(int i = 0; i < size; i++) {
			String seq = raw.getSequence(ind[i]);
			StringBuilder sb = new StringBuilder();
			char ch;
			for(int j = 0; j < len; j++) {
				ch = seq.charAt(j);
				if(ch != '-') {
					sb.append(ch);
					cols[j][i] = 1;
				} else {
					cols[j][i] = 0;
				}
			}
			seqs[i] = sb.toString();
		}
	}
	
	public RawSequences toRaw() {
		RawSequences raw = new RawSequences();

		for(int i = 0; i < names.length; i++) {
			StringBuilder sb = new StringBuilder();
			int pos = 0;
			
			for(int j = 0; j < cols.length; j++) {
				sb.append(cols[j][i] > 0 ? seqs[i].charAt(pos++) : '-');
			}
			
			raw.add(names[i], sb.toString());
		}
		
		return raw;
	}
	
	@Override
	public String toString() {
		return toRaw().toString();
	}
	
	public static void main(String[] args) {
		RawSequences raw = new RawSequences();
		raw.add("A", "-A-B");
		raw.add("E", "-EF-");
		raw.add("C", "C-D-");
		StaticAlign a = new StaticAlign(raw);
		System.out.print(a);
	}
}
