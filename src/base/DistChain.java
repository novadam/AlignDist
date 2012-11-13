package base;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

import utils.CntGroup;
import utils.Counter;
import utils.SimpleStats;
import utils.Utils;

public class DistChain {
	// settings
	private int id;
	private Align ref;
	private DistCalc distCalc;
	private int targetDist;
	private double heat;
	
	private boolean writeSamples = false;
	private boolean rejectNonExact = false;

	// current state
	private Align curAli;
	private int curDist;
	private double curPi;
	
	// util
	private int[] gapCol;
	private int[] rows;
	
	// statistics
	HashMap<Integer,Integer> distCnts;
	HashMap<Integer,Integer> distUniqs;
	HashMap<String,Integer> sampleMap;
	int totalSamples;
	int maxCnt;
	int maxDist;
	
	private CntGroup slideChar = new CntGroup("slideChar");
	private Counter scNoMoves = slideChar.nc("scNoMoves"), scSing = slideChar.nc("scSing"),
					scReject = slideChar.nc("scReject"), scAccept = slideChar.nc("scAccept");
	private CntGroup breakCol = new CntGroup("breakCol");
	private Counter bcNoMoves = breakCol.nc("bcNoMoves"), bcReject = breakCol.nc("bcReject"),
					bcAccept = breakCol.nc("bcAccept");
	private CntGroup joinCol = new CntGroup("joinCol");
	private Counter jcNoMoves = joinCol.nc("jcNoMoves"), jcBadMove = joinCol.nc("jcBadMove"),
					jcReject = joinCol.nc("jcReject"), jcAccept = joinCol.nc("jcAccept");
	CntGroup chainSwap = new CntGroup("chainSwap");
	Counter csReject = chainSwap.nc("csReject"), csAccept = chainSwap.nc("csAccept");
	private CntGroup[] cntGroups = new CntGroup[] { slideChar, breakCol, joinCol, chainSwap };

	public DistChain(int id, Align ref, DistCalc distCalc, int targetDist, double heat) {
		this.id = id;
		this.ref = ref;
		this.distCalc = distCalc != null ? distCalc : new DistCalc(ref);
		this.targetDist = targetDist;
		this.heat = heat;
	}
	
	public DistChain setWriteSamples(boolean writeSamples) {
		this.writeSamples = writeSamples;
		return this;
	}
	
	public DistChain setRejectNonExact(boolean rejectNonExact) {
		this.rejectNonExact = rejectNonExact;
		return this;
	}
	
	public void init() {
		// reset counters
		for(CntGroup group : cntGroups)
			group.reset();
		distCnts = new HashMap<Integer, Integer>();
		distUniqs = new HashMap<Integer, Integer>();
		sampleMap = new HashMap<String, Integer>();
		totalSamples = 0;
		maxCnt = 0;
		
		// working arrays
		gapCol = new int[ref.first.inds.length];
		Arrays.fill(gapCol, -1);
		rows = new int[gapCol.length];

		// current state
		curAli = new Align(ref.toRaw());
		curDist = 0;
		curPi = logPi(curDist);
	}
	
	public void jumpTo(Align align) {
		curAli = new Align(align.toRaw());
		curDist = distCalc.dist(curAli);
		curPi = logPi(curDist);
	}
	
	public void changeHeat(double newHeat) {
		heat = newHeat;
		curPi = logPi(curDist);
	}

	public void makeStep() {
		if(Utils.DEBUG) {
			curAli.checkCons();
			if(curDist != distCalc.dist(curAli))
				throw new Error("Inconsistency in distance calculation");
			if(curPi != logPi(curDist))
				throw new Error("Inconsistency in likelihood calculation: "+logPi(curDist));
		}

		int type = Utils.generator.nextInt(3);
		switch (type) {
		case 0:
			slideChar(); break;
		case 1:
			breakCol(); break;
		default:
			joinCol();
		}

		// save distance distribution info
		Integer cnt = distCnts.get(curDist);
		cnt = cnt == null ? 1 : cnt+1;
		distCnts.put(curDist, cnt);
		
		if(Utils.DEBUG) {
			curAli.checkCons();
			if(curDist != distCalc.dist(curAli))
				throw new Error("Inconsistency in distance calculation");
			if(curPi != logPi(curDist))
				throw new Error("Inconsistency in likelihood calculation: "+logPi(curDist));
		}
	}
	
	private DistCalc sampleDCalc;
	
	public boolean sample() {
		if(rejectNonExact && curDist != targetDist)		// reject sample if distance does not match exactly
			return false;
		String key = curAli.toString();
		Integer cnt = sampleMap.get(key);
		if(cnt == null) {
			cnt = 1;
//			if(sampleMap.size()%100==99)
//				System.out.println("found: "+(sampleMap.size()+1));
//			if(sampleMap.size() == 220000)
//				return true;

			Integer cnt2 = distUniqs.get(curDist);
			cnt2 = cnt2 == null ? 1 : cnt2+1;
			distUniqs.put(curDist, cnt2);
		} else {
			cnt++;
		}
		if(cnt > maxCnt) {
			maxCnt = cnt;
			maxDist = curDist;
		}
		sampleMap.put(key, cnt);
		
		if(writeSamples && id == 0) {
			if(totalSamples == 0)
				sampleDCalc = new DistCalc(new Align(curAli.toRaw()));
			
			int d1 = distCalc.dist(curAli);		// distance from ref
			int d2 = sampleDCalc.dist(curAli);	// distance from first sample
//			sampleDCalc = new DistCalc(new Align(curAli.toRaw()));
			System.out.println((totalSamples+1)+"\t"+d1+"\t"+
					String.format(Locale.US, "%.2f", distCalc.dist2acc(d1))+"\t"+d2+"\t"+
					String.format(Locale.US, "%.2f", sampleDCalc.dist2acc(d2)));
			
			// write samples to files
			try {
				BufferedWriter w = new BufferedWriter(new FileWriter("sample"+(totalSamples+1)+".fsa"));
				w.write(key);
				w.close();
			} catch (Exception e) {
			}
		}

		totalSamples++;
		return true;
	}
	
	public double getHeat() {
		return heat;
	}
	
	public int getTargetDist() {
		return targetDist;
	}

	public CntGroup[] getCntGroups() {
		return cntGroups;
	}
	
	public void printStats() {
		System.out.println("\n** Chain "+(id+1)+" **\n");

//		System.out.println("\nDistance counts:\n");
//		List<Integer> keys = new ArrayList<Integer>(distCnts.keySet());
//		Collections.sort(keys);
//		for(int dist : keys)
//			System.out.println(dist+": "+distCnts.get(dist)+" uniq: "+distUniqs.get(dist));
		
		SimpleStats st = new SimpleStats("abs diff");
		double goal = totalSamples/(double)sampleMap.size();
		System.out.println("Alignments seen: "+sampleMap.size()+" goal: "+goal);
		for(String key : sampleMap.keySet()) {
			double ad = Math.abs(((double)sampleMap.get(key)-goal)/goal*100);
			st.addData(ad);
		}
		System.out.println(st);
		if(maxCnt > 0)
			System.out.println("max cnt: "+maxCnt+" dist: "+maxDist);
//		if(amax != null)
//			printAli(amax);
		System.out.println("ref cnt: "+sampleMap.get(ref.toString()));
//		List<String> samps = new ArrayList<String>(sampleMap.keySet());
//		Collections.sort(samps, new Comparator<String>() {
//			@Override
//			public int compare(String o1, String o2) {
//				try {
//					RawSequences r = new FastaReader().read(new BufferedReader(new StringReader(o1)));
//					int d1 = distCalc.dist(new Align(r));
//					r = new FastaReader().read(new BufferedReader(new StringReader(o2)));
//					int d2 = distCalc.dist(new Align(r));
//					return d2-d1;
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//				return 0;
//			}
//		});
//		for(int i = 0; i < 3; i++) {
//			String ali = samps.get(i);
//			printAli(ali);
//		}
	}
	
//	private void printAli(String ali) {
//		double goal = 1/(double)sampleMap.size();
//		System.out.println((goal*totalSamples)+" "+sampleMap.get(ali)+" "+((double)sampleMap.get(ali)/totalSamples));
//		System.out.println(ali);
//		try {
//			RawSequences r = new FastaReader().read(new BufferedReader(new StringReader(ali)));
//			System.out.println(distCalc.dist(new Align(r)));
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	public int slideChar() {
		AlignWin win = curAli.randWin(2);
		AlignCol col1 = win.first, col2 = win.last;
		int[] inds1 = col1.inds, inds2 = col2.inds;
		int s = 0;	// slidables
		for(int i = 0; i < inds1.length; i++)
			if(inds1[i] >= 0 ^ inds2[i] >= 0)
				rows[s++] = i;
		if(s > 0) {
			// choose a random slidable
			s = rows[Utils.generator.nextInt(s)];

			if((inds1[s] >= 0 ? col1.singOrd : col2.singOrd) < 0) {		// disallow sliding character from singular column
				// evaluate swap
				int ch1 = inds1[s], ch2 = inds2[s];	// characters to swap
				int newDist = curDist;
				newDist -= distCalc.distSingle(inds1, s, ch1)+distCalc.distSingle(inds2, s, ch2);
				newDist += distCalc.distSingle(inds1, s, ch2)+distCalc.distSingle(inds2, s, ch1);
				if(Utils.DEBUG) {
					inds1[s] = ch2; inds2[s] = ch1;
					int testDist = distCalc.dist(curAli);
					inds1[s] = ch1; inds2[s] = ch2;
					if(testDist != newDist)
						throw new Error("Inconsistency in distance calculation in slideChar");
				}
				double newPi = logPi(newDist);
//				double mh = newPi/curPi;
				double logMh = newPi-curPi;
				if(logMh >= 0 || Utils.generator.nextDouble() < Math.exp(logMh)) {
					scAccept.inc();
					inds1[s] = ch2; inds2[s] = ch1;
					curAli.updateSing(col1); curAli.updateSing(col2);
					curDist = newDist;
					curPi = newPi;
					return 1;
				} else {
					scReject.inc();
				}
			} else {
				scSing.inc();	// shift from singular column was selected
			}
		} else {
			scNoMoves.inc();	// no rows with possible shift (i.e. gap - nongap pair)
		}
		return 0;
	}
	
	public int breakCol() {
		AlignCol col = curAli.randCol();
		int[] inds = col.inds;
		int s = 0;		// non-gaps, where col can be broken
		for(int i = 0; i < inds.length; i++)
			if(inds[i] >= 0)
				rows[s++] = i;
		if(s > 1) {
			double mh = (double)curAli.cols.size()*s/(curAli.singCols.size()+1);
			// choose a random non-gap
			s = rows[Utils.generator.nextInt(s)];
			// and a random direction (0 = left)
			int dir = Utils.generator.nextInt(2);
			
			// evaluate column break
			int ch = inds[s];
			int newDist = curDist;
			newDist -= distCalc.distSingle(inds, s, ch);
			newDist += distCalc.distSingle(inds, s, -1)+distCalc.distSingle(gapCol, s, ch);
			double newPi = logPi(newDist);
//			mh *= newPi/curPi;
			mh *= Math.exp(newPi-curPi);
			if(Utils.generator.nextDouble() < mh) {
				// always accept as MH = cols.size()*s/(singCols.size()+1) > 1
				bcAccept.inc();
				inds[s] = -1;
				curAli.updateSing(col);
				int[] newcol = Utils.copyOf(gapCol);
				newcol[s] = ch;
				curAli.insertCol(new AlignCol(newcol), dir>0?col:col.prev);
				curDist = newDist;
				curPi = newPi;
				return 1;
			} else {
				bcReject.inc();
			}
		} else {
			bcNoMoves.inc();	// no possible chars to break column at (i.e. a nongap)
		}
		return 0;
	}
	
	public int joinCol() {
		AlignCol col = curAli.randSingCol();
		if(col != null) {
			int[] inds = col.inds, jinds;
			
			// find the single non-gap
			int s = 0;
			while(inds[s] < 0)
				s++;
			// choose a random direction (0 = left)
			int dir = Utils.generator.nextInt(2);
			AlignCol jcol = dir>0 ? col.next : col.prev;
			
			if(jcol != null && (jinds=jcol.inds)[s] < 0) {
				// evaluate column join
				int ch = inds[s];
				int newDist = curDist;
				newDist -= distCalc.distSingle(inds, s, ch)+distCalc.distSingle(jinds, s, -1);
				newDist += distCalc.distSingle(jinds, s, ch);
				// calculate MH ratio (ignoring directions as they are chosen analogously in break and join)
				double mh = (double)curAli.singCols.size()/(curAli.cols.size()-1)/(jcol.nonGaps()+1);
				double newPi = logPi(newDist);
//				mh *= newPi/curPi;
				mh *= Math.exp(newPi-curPi);
				if(Utils.generator.nextDouble() < mh) {
					jcAccept.inc();
					jinds[s] = ch;
					curAli.updateSing(jcol);
					curAli.removeCol(col);
					curDist = newDist;
					curPi = newPi;
					return 1;
				} else {
					jcReject.inc();
				}
			} else {
				jcBadMove.inc();	// the selected direction is invalid (i.e. two nongaps in the row)
			}
		} else {
			jcNoMoves.inc();	// no columns available for join (i.e. a singular column)
		}
		return 0;
	}
	
	private double logPi(int dist) {
//		return Math.exp(-Math.abs(dist-targetDist)*2/heat);
		return -Math.abs(dist-targetDist)*2/heat;
	}
	
	public boolean trySwapWith(DistChain chain) {
		// calc swap Metropolis ratio
		double newPi = logPi(chain.curDist), cnewPi = chain.logPi(curDist);
		double m = newPi+cnewPi-curPi-chain.curPi;
		if(m >= 0 || Utils.generator.nextDouble() < Math.exp(m)) {
			csAccept.inc();
			// swap states
			Align ali = curAli;
			curAli = chain.curAli;
			chain.curAli = ali;
			int dist = curDist;
			curDist = chain.curDist;
			chain.curDist = dist;
			curPi = newPi;
			chain.curPi = cnewPi;
			return true;
		}
		csReject.inc();
		return false;
	}	

}
