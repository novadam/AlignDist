package base;

import io.FastaReader;
import io.RawSequences;

import java.io.IOException;
import java.util.Locale;

import utils.CntGroup;
import utils.Counter;
import utils.Utils;

public class DistSampler {

	// task params
	private Align ref;
	private DistCalc distCalc;
	private int targetDist;
	
	private int nonExactStrategy = 0;			// 0: do not reject, 1: reject and skip, 2: reject and resample
	private boolean writeSamples = true;		// if true writes samples to files and statistics to stdout
	private boolean restartFromRef = false;		// if true always start from ref after a sample

	// chain params
	private int nchain;
	private DistChain[] chains;
	private double tempDiff = 1;
	private int swapFreq = 1;
	private int lastSwap = 0;

	// temp tune params
	private int tunePerChain = 0;
	private int tempTuneFreq = 1000;
	private double targetAccept = 0.7;

	// statistics
	private CntGroup totalSwaps = new CntGroup("totalSwaps");
	private Counter tsReject = totalSwaps.nc("tsReject"), tsAccept = totalSwaps.nc("tsAccept");
	
	public DistSampler(Align ref, int targetDist, int nchain) {
		this.ref = ref;
		distCalc = new DistCalc(ref);
		this.targetDist = targetDist;
		this.nchain = nchain;
	}
	
	public DistSampler(Align ref, double targetAcc, int nchain) {
		this.ref = ref;
		distCalc = new DistCalc(ref);
		targetDist = distCalc.acc2dist(targetAcc);
		this.nchain = nchain;
	}
	
	public void setSwapFreq(int swapFreq) {
		this.swapFreq = swapFreq;
	}
	
	public void setTempDiff(double tempDiff) {
		this.tempDiff = tempDiff;
	}
	
	public void enableTempTune(double targetAccept, int tuneCyclesPerChain, int tempTuneFreq) {
		this.targetAccept = targetAccept;
		tunePerChain = tuneCyclesPerChain*tempTuneFreq;
		this.tempTuneFreq = tempTuneFreq;
	}
	
	public void run(int burnin, int samples, int rate) {
		int i, j;

		System.out.println("Target dist: "+targetDist);
		System.out.println("MCMC pars: "+burnin+","+samples+","+rate);
		System.out.println("Temp tuning: "+(tunePerChain==0?"disabled":targetAccept+","+tunePerChain/tempTuneFreq+","+tempTuneFreq));

		// init
		chains = new DistChain[nchain];
		for(i = 0; i < nchain; i++) {
//			chains[i] = new DistChain(i, ref, distCalc, Math.max(0,targetDist-i*5), 1+tempDiff*i);
//			chains[i] = new DistChain(i, ref, distCalc, Math.max(0,targetDist-i*i*5), 1+3*Math.sqrt(i));
			chains[i] = new DistChain(i, ref, distCalc, Math.max(0,targetDist), 1+tempDiff*i)
					.setRejectNonExact(nonExactStrategy > 0)
					.setWriteSamples(writeSamples);
			chains[i].init();
		}
		
		// temp tuning
		if(tunePerChain > 0) {
			System.out.println("\n*** Temperature tuning ***\n");
			
			System.out.println("Target acceptance: "+targetAccept);
			printTempInfo("\nInitial dists+temps:");
	
			for(i = 0; i < nchain-1; i++) {
				for(j = 0; j < tunePerChain; j++)
					tuneStep(i, j);
				tuneReset(i);
			}
			
			printTempInfo("\nFinal dists+temps:");
		}
		
		// MCMC run
		if(burnin > 0) {
			System.out.println("\n*** Burn-in ***");
			for(i = 0; i < burnin; i++) {
				makeStep();
			}
		}
		System.out.println("\n*** Sampling ***\n");
		for(i = 0; i < samples;) {
			for(j = 0; j < rate; j++)
				makeStep();
			if(sample() || nonExactStrategy < 2)
				i++;
			if(restartFromRef) {
				for(j = 0; j < nchain; j++)
					chains[j].jumpTo(ref);
			}
		}
		
		System.out.println("\n*** Statistics ***");
		printStats();
	}
	
	public void makeStep() {
		int j, k;
		// make steps
		for(k = 0; k < nchain; k++)
			chains[k].makeStep();
		// try one swap
		if(nchain > 1 && ++lastSwap == swapFreq) {
			lastSwap = 0;
			j = Utils.generator.nextInt(nchain-1);
			k = j+1;	// attempt to swap consecutive chains only
			if(chains[j].trySwapWith(chains[k]))
				tsAccept.inc();
			else
				tsReject.inc();
		}
	}
	
	public void tuneStep(int j, int step) {
		// make step and try swap
		int k = j+1;
		chains[j].makeStep();
		chains[k].makeStep();
		if(chains[j].trySwapWith(chains[k])) {
			tsAccept.inc();
		} else {
			tsReject.inc();
		}
		
		if((step+1) % tempTuneFreq == 0) {
			// tune temperatures
			double mult = Math.pow(1.5, 1-(double)step/(tunePerChain-1));
			if(tsAccept.getRate() < targetAccept)
				mult = 1/mult;
			for(int i = k; i < nchain; i++) {
				double x = chains[i].getHeat()*mult;
				if(chains[i-1].getHeat() < x && x < 100) {
					chains[i].changeHeat(x);
				}
			}
			tuneReset(j);
		}
	}

	private void tuneReset(int j) {
		chains[j].jumpTo(ref);
		chains[j+1].jumpTo(ref);
		chains[j].chainSwap.reset();
		chains[j+1].chainSwap.reset();
		totalSwaps.reset();
	}


	public boolean sample() {
		boolean succ = chains[0].sample();
		for(int k = 1; k < nchain; k++)
			chains[k].sample();
		return succ;
	}

	private void printStats() {
		for(int k = 0; k < nchain; k++)
			chains[k].printStats();
		
		System.out.println("\n** Acceptance statistics **\n");
		for(int i = 0; i < chains[0].getCntGroups().length; i++) {
			System.out.println(chains[0].getCntGroups()[i].getName()+":");
			for(int j = 0; j < chains[0].getCntGroups()[i].getCounters().size(); j++) {
				System.out.print("* "+String.format(Locale.US, "%-11s", chains[0].getCntGroups()[i].getCounters().get(j).getName()+": "));
				for(int k = 0; k < nchain; k++)
					System.out.print(String.format(Locale.US, "%7s", chains[k].getCntGroups()[i].getCounters().get(j).valueString()));
				System.out.println();
			}
			System.out.println();
		}
		
		System.out.println(totalSwaps);
	}
	
	private void printTempInfo(String str) {
		if(str != null)
			System.out.println(str);
		for(int i = 0; i < nchain; i++)
			System.out.print(String.format(Locale.US, " %6d", chains[i].getTargetDist()));
		System.out.println();
		for(int i = 0; i < nchain; i++)
			System.out.print(String.format(Locale.US, " %6.2f", chains[i].getHeat()));
		System.out.println();
	}	
	
	public static void main(String[] args) throws IOException {
//		Utils.generator = new Random(1);
		FastaReader r = new FastaReader();
//		RawSequences raw = r.read("data/1.fsa");
//		RawSequences raw = r.read("data/stat_f02_c04.fsa");
//		RawSequences raw = r.read("data/stat_f25_c01.fsa");
//		RawSequences raw = r.read("data/stat_f29_c01.fsa");
		RawSequences raw = r.read(args[0]);
//		RawSequences raw = new RawSequences();
//		raw.add("A", "-ABC");
//		raw.add("B", "E-FG");
//		raw.add("C", "-IJK");
//		raw.add("D", "-LM-");
//		raw.add("E", "NOP-");
//		raw.add("F", "-IJK");
		
//		raw.add("A", "ABCDEFGH");
//		raw.add("B", "EFGHIJKL");
//		raw.add("C", "IJKLMNOP");
//		raw.add("D", "MNOPQRST");
		
//		raw.add("A", "-A-BCD-A-BCD");
//		raw.add("B", "E-FGH-EF-GH-");
//		raw.add("C", "-IJ-KL-IJKL-");
//		raw.add("D", "-A-BCD-A-BCD");
//		raw.add("E", "E-FGH-E-FGH-");
//		raw.add("F", "H-I-JKH-I-JK");
//		System.out.println(raw);
		Align a = new Align(raw);
		DistSampler sampler = new DistSampler(a, .90, 10);//1.38);
		sampler.enableTempTune(.7, 20, 10000);
//		int seen = sampler.run(0, 500, 200000);
//		int seen = sampler.run(0, 180000, 500);
		sampler.run(10000, 10, 100000);
//		System.out.println(seen);
//		for(int i = 10; i <= 100000; i *= 2) {
//			DistSampler sampler = new DistSampler(a, 6);
//			int seen = sampler.run(0, i, 1);
//			System.out.println(i+"\t"+seen);
//		}
	}

}
