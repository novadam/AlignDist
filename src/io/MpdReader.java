package io;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Class to read files in MPD/Fasta format.
 * 
 * @author novak
 * 
 */
public class MpdReader extends FileFormatReader {

	private static final String SCORES_SEPARATOR = "#scores";

	private int errors;
	private List<Double> scores;

	/**
	 * Reads the contents (aligned/non-aligned sequences) of the given data
	 * source in Fasta format.
	 * 
	 * @param reader
	 *            Data source
	 * @return RawSequences representation of the contents
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public RawSequences read(BufferedReader reader) throws IOException {
		RawSequences result = new RawSequences();
		String line;
		boolean inSeq = false;
		StringBuilder actSeq = new StringBuilder();
		String seqname = "initial_seqName";
		String sequence;
		errors = 0;
		while (true) {
			line = reader.readLine();
			// skip empty lines
			if (line != null && line.length() == 0) {
				continue;
			}
			// end of a sequence : end of file or beginning of a new sequence
			// name
			if (line == null || line.startsWith(SCORES_SEPARATOR) || line.charAt(0) == '>') {
				if (inSeq) {
					if (actSeq.length() == 0) {
						errors++;
					} else {
						// in case of no errors: add the last sequence and the
						// previous name to result
						sequence = actSeq.toString();
						result.add(seqname, sequence);
					}
				}
				if (line == null) {
					break;
				}
				if(line.startsWith(SCORES_SEPARATOR)) {
					scores = new ArrayList<Double>();
					for(;;) {
						line = reader.readLine();
						if(line == null)
							break;
						if(line.length() == 0)
							continue;
						try {
							scores.add(Double.parseDouble(line));
						} catch (NumberFormatException e) {
							if(line.equals("*"))
								scores.add(null);	// missing value
							else
								errors++;
						}
					}
					if(result.size() == 0 || result.len() != scores.size())
						errors++;
					break;
				}
				// beginning a new sequence
				actSeq.setLength(0);
				inSeq = true;
				int start = 1, index;
				if ((index = line.indexOf(' ', 1)) == 1) {
					index = line.indexOf(' ', 2);
					start = 2;
				}
				if (index == -1) {
					line = line.substring(start);
				} else {
					// line = line.substring(start, index);
					line = line.substring(start);
				}
				line = line.replaceAll("[ \t]+", "_");
				// line.replaceAll(" ", "_");
				line = line.replaceAll("\\(", "{");
				line = line.replaceAll("\\)", "}");
				// System.out.println("new name: "+line);
				seqname = line;

			} else if (inSeq) { // within a sequence
				int len = line.length();
				char ch;
				for (int i = 0; i < len; i++) {
					if (!Character.isWhitespace(ch = line.charAt(i))) {
						if (Character.isLetter(ch))
							actSeq.append(ch);
						else if(ch == '-' || ch == '.')		// treat . as gap, too
							actSeq.append('-');
						else
							errors++;
					}
				}
			} else {
				errors++;
			}
		}

		if (errors > 0) {
//			System.out.println("Errors: " + errors);
		} else {
//			System.out.println("FastaReader: successfully read " + result.size() + " sequences.");
		}

		return result;
	}

	/**
	 * Return the error count of the last read operation.
	 * 
	 * @return number of errors
	 */
	public int getErrors() {
		return errors;
	}

	public List<Double> getScores() {
		return scores;
	}

	/**
	 * Only for testing/debugging purposes: reads the given Fasta file
	 * 
	 * @param args
	 *            First element must be the name of the Fasta file to read
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static void main(String[] args) throws IOException {
		MpdReader f = new MpdReader();
		f.read(args[0]);
	}
}
