package io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 
 * Ancestor for sequence/alignment file writer classes that handle one specific
 * format.
 * 
 * @author novak
 * 
 */
public abstract class FileFormatWriter {

	/**
	 * Writes sequences/alignment to a file using the RawSequences
	 * representation.
	 * 
	 * @param seqs
	 *            object containing the data to write
	 * @param fileName
	 *            name of file to write to
	 * @throws IOException
	 *             when an I/O error occurs
	 */
	public void write(RawSequences seqs, String fileName) throws IOException {
		write(seqs, new BufferedWriter(new FileWriter(fileName)));
	}

	/**
	 * Writes sequences/alignment to a <code>Writer</code> using the
	 * RawSequences representation. For efficiency <code>writer</code> is
	 * strongly recommended to be buffered.
	 * 
	 * @param seqs
	 *            object containing the data to write
	 * @param writer
	 *            object to write to
	 * @throws IOException
	 *             when an I/O error occurs
	 */
	public abstract void write(RawSequences seqs, BufferedWriter writer) throws IOException;

}
