package utils;

import java.util.Locale;

public class Counter {

	private String name;
	int cnt;
	private CntGroup group;
	
	static String format = "%.3g%%";
	
	public Counter(String name) {
		this.name = name;
	}
	
	public Counter(String name, CntGroup group) {
		init(name, group);
	}
	
	public Counter(String name, Counter other) {
		init(name, other.group);
	}
	
	public void init(String name, CntGroup group) {
		this.name = name;
		this.group = group;
		if(group != null)
			group.add(this);
	}
	
	public int getCnt() {
		return cnt;
	}
	
	public double getRate() {
		return (double)cnt/group.cnt;
	}
	
	public String getName() {
		return name;
	}
	
	public void inc() {
		cnt++;
		if(group != null)
			group.inc();
	}
	
	public void reset() {
		cnt = 0;
	}
	
	@Override
	public String toString() {
		return name+": "+valueString();
	}
	
	/**
	 * With name omitted.
	 */
	public String valueString() {
		return (group != null ? (group.cnt != 0 ? String.format(Locale.US, format, getRate()*100) : "N/A") : ""+cnt);
	}
}