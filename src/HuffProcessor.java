import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	/**
	 * Creates a frequency table for the characters in the file to be compressed.
	 * @param input the file to be compressed
	 * @return a table that contains each character's frequency
	 */
	private int[] readForCounts(BitInputStream input) {
		int[] freq = new int[ALPH_SIZE + 1];
		while(true) {
			int bits = input.readBits(BITS_PER_WORD);
			if(bits == -1) {
				freq[PSEUDO_EOF] = 1;
				return freq;
			}
			freq[bits]++;
		}
	}
	
	/**
	 * Creates a greedy tree to create character encodings.
	 * @param counts a table that maps characters to their frequency
	 * @return a greedy tree that allows the trace of character encodings
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < counts.length; i++) {
			if(counts[i] == 0) continue;
			pq.add(new HuffNode(i, counts[i], null, null));
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		return pq.remove();
	}
	
	/**
	 * Generates the character encodings from the tree
	 * @param root the root of the tree to be encoded
	 * @return a table of characters mapped to their encoding
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	// Used for makeCodingsFromTree - recursively searches tree to
	// generate encodings
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		codingHelper(root.myLeft, path + "0", encodings);
		codingHelper(root.myRight, path + "1", encodings);
	}

	/**
	 * Passes the tree to the compressed file
	 * @param root the root of the tree to put in the compressed file
	 * @param out the compressed file output
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root == null) return;
		if(root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1,  root.myValue);
		}
		else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}
	
	/**
	 * Creates a compressed version of the input file by using the character encodings
	 * @param codings the table that maps characters to their respective encodings
	 * @param in the input file to be compressed
	 * @param out the output file to write the compressed file to
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		for(int i = 0; i < codings.length; i++) {
			System.out.println(i + ": " + codings[i]);
		}
		
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) break;
			
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	/**
	 * Rebuilds tree from compressed file by reading in header
	 * @param in the compressed file
	 * @return the root of the tree used to decode the compressed file
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit == -1) {
			throw new HuffException("illegal header starts with "+bit);
		}
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);
			// in.reset();   ???
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
	
	/**
	 * Decodes the compressed file's bits using the tree root and writes the
	 * decoded version of in to out
	 * @param root the root of the tree used to decode the input file
	 * @param in the input file, which is compressed
	 * @param out the output file, which is decompressed
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if(bits == 0) current = current.myLeft;
				else current = current.myRight;
				
				if(current.myLeft == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}