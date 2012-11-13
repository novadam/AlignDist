package io;

import java.io.BufferedWriter;
import java.io.IOException;

public class FastaWriter extends FileFormatWriter {

	private int wrapSize;

	/**
	 * Constructs default <code>FastaWriter</code> with line wrapping switched off.
	 */
	public FastaWriter() {
	}

	/**
	 * Constructs <code>FastaWriter</code> with given wrap size.
	 * 
	 * @param wrapSize
	 *            wrap size to set (maximum number of sequence characters
	 *            written on one line)
	 */
	public FastaWriter(int wrapSize) {
		this.wrapSize = wrapSize;
	}

	@Override
	public void write(RawSequences seqs, BufferedWriter writer) throws IOException {
		for (int i = 0; i < seqs.size(); i++) {
			writer.write("> ");
			if (i < seqs.size())
				writer.write(seqs.getSeqName(i));
			writer.newLine();
			String seq = seqs.getSequence(i);
			if (wrapSize <= 0) {
				writer.write(seq);
				writer.newLine();
			} else {
				for (int pos = 0; pos < seq.length(); pos += wrapSize) {
					writer.write(seq.substring(pos, Math.min(seq.length(), pos + wrapSize)));
					writer.newLine();
				}
			}
		}
		writer.close();
	}

	/**
	 * Returns wrap size (maximum number of sequence characters written on one
	 * line).
	 * 
	 * @return wrap size
	 */
	public int getWrapSize() {
		return wrapSize;
	}

	/**
	 * Sets wrap size (maximum number of sequence characters written on one
	 * line). A non-positive value will turn off line wrapping completely.
	 * 
	 * @param wrapSize
	 *            new wrap size
	 */
	public void setWrapSize(int wrapSize) {
		this.wrapSize = wrapSize;
	}

}
