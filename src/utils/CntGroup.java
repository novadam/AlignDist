package utils;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class CntGroup {
	
	private String name;
	int cnt;
	private List<Counter> list = new ArrayList<Counter>();
	
	public CntGroup(String name) {
		this.name = name;
	}
	
	public void add(Counter counter) {
		list.add(counter);
	}
	
	public List<Counter> getCounters() {
		return list;
	}
	
	public String getName() {
		return name;
	}
	
	public int getCnt() {
		return cnt;
	}
	
	public void inc() {
		cnt++;
	}
	
	public void reset() {
		cnt = 0;
		for(Counter counter : list)
			counter.reset();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(name+":\n* total: "+cnt+"\n");
		int rem = cnt;
		for(Counter counter : list) {
			sb.append("* "+counter+" ("+String.format(Locale.US, Counter.format, (double)counter.cnt/rem*100)+")"+"\n");
			rem -= counter.cnt;
		}
		return sb.toString();
	}

	/**
	 * Creates and returns a new counter in the group.
	 * @return the newly created {@link Counter} object
	 */
	public Counter nc(String name) {
		return new Counter(name, this);
	}
}