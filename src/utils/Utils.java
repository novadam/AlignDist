package utils;

import java.util.Random;

public class Utils {
	
	public static Random generator = new Random();
	
	public static final boolean DEBUG = false;
	
	public static int[] copyOf(int[] inds) {
		int len = inds.length;
		int[] copy = new int[len];
		System.arraycopy(inds, 0, copy, 0, len);
		return copy;
	}
	
	public static int parseValue(String string) {
		if (string.isEmpty())
			return -1;
		int factor = 1;
		switch (Character.toUpperCase(string.charAt(string.length() - 1))) {
		case 'K':
			factor = 1000;
			break;
		case 'M':
			factor = 1000000;
			break;
		}
		if (factor > 1)
			string = string.substring(0, string.length() - 1);
		int result = -1;
		try {
			result = factor * Integer.parseInt(string);
		} catch (NumberFormatException e) {
		}
		return result;
	}


}
