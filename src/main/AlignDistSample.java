package main;

import io.FastaReader;
import io.RawSequences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import ml.options.OptionSet;
import ml.options.Options;
import ml.options.Options.Multiplicity;
import ml.options.Options.Separator;
import utils.Utils;
import base.Align;
import base.DistSampler;


public class AlignDistSample {

	public static final String VERSION = "v1.02";
	
	private static final int DEFAULT_CHAINS = 10;
	private static final String DEFAULT_PARS = "10k,10,100k";
	private static final String DEFAULT_TUNE = "0.7,20,10k";
	
	private static final String USAGE =
		"AlignDistSample "+VERSION+" (C) Adam Novak, 2012.\n\n" +
		"Usage:\n  java -jar adsample.jar [options] ref.fsa/mpd\n\n" +
		"Description:\n"+
		"  Samples approximately uniformly from the space of alignments that are\n" +
		"  a given distance away from a reference alignment.\n\n"+
		"Options:\n" +
		"  -d=DIST\n" +
		"     The distance at which to sample alignments, as defined by:\n" +
		"       Schwartz et al. (2005) Alignment Metric Accuracy, arXiv:q-bio/0510052\n" +
		"     Either -d or -a must be specified\n\n"+
		"  -a=AGR\n"+
		"     The distance given as agreement (similarity) to the reference (value\n" +
		"     between 0 and 1). Either -d or -a must be specified\n\n"+
		"  -c=NC\n"+
		"     The number of parallel chains to run\n"+
		"     Default: "+DEFAULT_CHAINS+"\n\n"+
		"  -p=BURN,SAMP,RATE\n"+
		"     MCMC parameters (burn-in steps, number of samples, sampling rate)\n"+
		"     Default: "+DEFAULT_PARS+"\n\n"+
		"  -t=TACC,CYC,FREQ,\n"+
		"     Enable/disable temperature (heat) tuning with parameters:\n"+
		"       TACC: target acceptance rate\n" +
		"       CYC: total tuning cycles per chain (disabling if zero)\n"+
		"       FREQ: tuning cycle frequency in steps\n" +
		"     Tuning process requires NC*CYC*FREQ steps in total\n" +
		"     Default: "+DEFAULT_TUNE+"\n\n" +
		"  -s=SEED\n" +
		"     Set random seed for reproducible output\n" +
		"     Default: based on current system time\n\n";

	public static void main(String[] args) {
		Options opt = new Options(args, Multiplicity.ZERO_OR_ONE, 1, 1);
		opt.addSet("run")
				.addOption("d", Separator.EQUALS)
				.addOption("a", Separator.EQUALS)
				.addOption("c", Separator.EQUALS)
				.addOption("p", Separator.EQUALS)
				.addOption("t", Separator.EQUALS)
				.addOption("s", Separator.EQUALS);
		
		OptionSet set = null;
		if((set = opt.getMatchingSet(false, false)) == null) {
			System.out.println(USAGE);
			System.exit(1);
		}

		ArrayList<String> data = set.getData();
		String refFile = data.get(0);
		
		if(!new File(refFile).exists()) {
			error("reference alignment file '"+refFile+"' does not exist.");
		}
		
		FastaReader reader = new FastaReader();
		RawSequences seqs = null;
		try {
			seqs = reader.read(refFile);
			if(reader.getErrors() > 0)
				throw new IOException();
		} catch (IOException e) {
			error("error reading reference fasta file: "+refFile);
		}
		Align ref = new Align(seqs);

		int chains = DEFAULT_CHAINS;
		if(set.isSet("c")) {
			String val = set.getOption("c").getResultValue(0);
			try {
				chains = Integer.parseInt(val);
				if(chains < 1)
					throw new NumberFormatException();
			} catch (NumberFormatException e) {
				error("bad format for option c: "+val);
			}
		}
		
		DistSampler sampler = null;
		if(!set.isSet("d") && !set.isSet("a")) {
			error("either -d or -a must be given.");
		}
		if(set.isSet("d")) {
			String val = set.getOption("d").getResultValue(0);
			try {
				int dist = Integer.parseInt(val);
				if(dist < 0)
					throw new NumberFormatException();
				sampler = new DistSampler(ref, dist, chains);
			} catch (NumberFormatException e) {
				error("bad format for option d: "+val);
			}
		}
		if(set.isSet("a")) {
			String val = set.getOption("a").getResultValue(0);
			try {
				double agree = Double.parseDouble(val);
				if(agree < 0 || agree > 1)
					throw new NumberFormatException();
				sampler = new DistSampler(ref, agree, chains);
			} catch (NumberFormatException e) {
				error("bad format for option a: "+val);
			}
		}
		
		String pars = DEFAULT_PARS;
		if(set.isSet("p")) {
			pars = set.getOption("p").getResultValue(0);
		}
		String[] arr = pars.split(",");
		int burn = 0, samp = 0, rate = 0;
		try {
			if(arr.length != 3)
				throw new NumberFormatException();
			burn = Utils.parseValue(arr[0]);
			samp = Utils.parseValue(arr[1]);
			rate = Utils.parseValue(arr[2]);
			if(burn < 0 || samp < 0 || rate < 0)
				throw new NumberFormatException();
		} catch (NumberFormatException e) {
			error("bad format for option p: "+pars);
		}

		String tune = DEFAULT_TUNE;
		if(set.isSet("t")) {
			tune = set.getOption("t").getResultValue(0);
		}
		arr = tune.split(",");
		double tacc = 0;
		int tcyc = 0, tfreq = 0;
		try {
			if(arr.length != 3)
				throw new NumberFormatException();
			tacc = Double.parseDouble(arr[0]);
			tcyc = Utils.parseValue(arr[1]);
			tfreq = Utils.parseValue(arr[2]);
			if(tacc < 0 || tcyc < 0 || tfreq < 0)
				throw new NumberFormatException();
		} catch (NumberFormatException e) {
			error("bad format for option t: "+tune);
		}
		
		if(set.isSet("s")) {
			String seedStr = set.getOption("s").getResultValue(0);
			try {
				int seed = Integer.parseInt(seedStr);
				// fix seed
				Utils.generator = new Random(seed);
			} catch (NumberFormatException e) {
				error("bad format for option s: "+seedStr);
			}
		}

		sampler.enableTempTune(tacc, tcyc, tfreq);
		sampler.run(burn, samp, rate);
	}
	
	private static void error(String msg) {
		System.out.println("AlignDistSample: " + msg);
		System.exit(1);
	}
	
}

