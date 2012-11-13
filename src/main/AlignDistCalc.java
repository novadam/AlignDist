package main;

import io.MpdReader;
import io.RawSequences;
import io.SampleReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import ml.options.OptionSet;
import ml.options.Options;
import ml.options.Options.Multiplicity;
import ml.options.Options.Separator;
import base.Align;
import base.DistCalc;


public class AlignDistCalc {
	
	public static final String VERSION = "v1.21";
	
	private static final String USAGE =
		"AlignDistCalc "+VERSION+" (C) Adam Novak, 2012.\n\n" +
		"Usage:\n  java -jar adcalc.jar ref.fsa/mpd/log test1.fsa/mpd/log [test2...]\n\n" +
		"Description:\n"+
		"  Calculates distances and accuracy (similarity) values between a reference\n" +
		"  alignment and a set of test alignments. Outputs the two values separated by\n" +
		"  a tab, in one line per test alignment. Distance is the one described in\n" +
		"  Schwartz et al. (2005) Alignment Metric Accuracy, arXiv:q-bio/0510052\n" +
		"  and accuracy is between 0 and 1, defined as 1-dist/maxdist where maxdist\n" +
		"  is (n-1)sum(len_i), with n denoting the number of sequences, len_i the\n" +
		"  length of ith sequence. Accepts FASTA or StatAlign mpd/log files. In the\n" +
		"  latter case all alignment samples are scored.\n\n"+
		"Options:\n" +
		"  -s=N\n" +
		"     Skips first N samples and reads next as reference (applicable when\n" +
		"       reference is given as StatAlign log file)\n";

	public static void main(String[] args) {
		Options opt = new Options(args, Multiplicity.ZERO_OR_ONE, 2, 100);
		opt.addSet("run")
				.addOption("s", Separator.EQUALS);
//				.addOption("cm")
//				.addOption("t", Separator.BLANK)
//				.addOption("n", Separator.EQUALS)
//				.addOption("fsa");
		
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
		
		for(int i = 1; i < data.size(); i++)
			if(!new File(data.get(i)).exists())
				error("alignment file '"+data.get(i)+"' does not exist.");
		
		int skip = 0;
		if(set.isSet("s")) {
			String val = set.getOption("s").getResultValue(0);
			try {
				skip = Integer.parseInt(val);
				if(skip < 0)
					throw new NumberFormatException();
			} catch (NumberFormatException e) {
				error("bad format for option s: "+val);
			}
		}
		
		try {
			MpdReader mr = new MpdReader();
			RawSequences raw;
			if(refFile.endsWith(".log")) {
				SampleReader sReader = new SampleReader(new FileReader(refFile));
				raw = mr.read(sReader);
				for(int i = 0; i < skip; i++) {
					sReader.nextSample();
					raw = mr.read(sReader);
				}
			} else {
				raw = mr.read(refFile);
			}
			Align ref = new Align(raw);
			DistCalc distCalc = new DistCalc(ref);

			for(int i = 1; i < data.size(); i++) {
				String input = data.get(i);
				if(input.endsWith(".log")) {
					SampleReader sReader = new SampleReader(new FileReader(input));
					while(!sReader.isEof()) {
						try {
							Align al = new Align(mr.read(sReader));
							distCalc.printDistData(al);
							sReader.nextSample();
						} catch (IOException e) {
						}
					}
				} else {	// try Fasta/MPD
					Align al = new Align(mr.read(input));
					distCalc.printDistData(al);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	private static void error(String msg) {
		System.out.println("AlignDist: " + msg);
		System.exit(1);
	}
	
}

