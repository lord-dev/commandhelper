package com.laytonsmith.PureUtilities;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *
 * @author layton
 */
public class FileUtility {

	private FileUtility() {
	}
	public static final int OVERWRITE = 0;
	public static final int APPEND = 1;
	
	private static final Map<String, Object> fileLocks = new HashMap<String, Object>();
	/**
	 * A more complicated mechanism is required to ensure global access across the JVM
	 * is synchronized, so file system accesses do not throw OverlappingFileLockExceptions.
	 * Though process safe, file locks are not thread safe -.-
	 * @param file
	 * @return
	 * @throws IOException 
	 */
	private static Object getLock(File file) throws IOException{
		String canonical = file.getAbsoluteFile().getCanonicalPath();
		if(!fileLocks.containsKey(canonical)){
			fileLocks.put(canonical, new Object());
		}
		return fileLocks.get(canonical);
	}

	public static String read(File f) throws IOException {
		try {
			return read(f, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new Error(ex);
		}
	}

	/**
	 * Returns the contents of this file as a string
	 *
	 * @param f The file to read
	 * @return a string with the contents of the file
	 * @throws FileNotFoundException
	 */
	public static String read(File file, String charset) throws IOException, UnsupportedEncodingException {
		synchronized (getLock(file)) {
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			FileLock lock = null;
			try {
				lock = raf.getChannel().lock();
				ByteBuffer buffer = ByteBuffer.allocate((int) raf.length());
				raf.getChannel().read(buffer);
				String s = StreamUtils.GetString(new ByteArrayInputStream(buffer.array()), charset);
				return s;
			} finally {
				if (lock != null) {
					lock.release();
				}
				raf.close();
			}
		}
//	    FileInputStream fis = new FileInputStream(f);
//	    try{
//		return StreamUtils.GetString(fis, charset);
//	    } finally {
//		    fis.close();
//		    fis = null;
//		    System.gc();
//	    }
	}

	/**
	 * Writes out a String to the given file, either appending or
	 * overwriting, depending on the selected mode
	 *
	 * @param data The string to write to the file
	 * @param file The File to write to
	 * @param mode Either OVERWRITE or APPEND
	 * @throws IOException If the File f cannot be written to
	 */
	public static void write(String data, File file, int mode) throws IOException {
		boolean append;
		if (mode == OVERWRITE) {
			append = false;
		} else {
			append = true;
		}
		synchronized (getLock(file)) {
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			FileLock lock = null;
			try {
				lock = raf.getChannel().lock();
				//Clear out the file
				if (!append) {
					raf.getChannel().truncate(0);
				} else {
					raf.seek(raf.length() - 1);
				}
				//Write out the data
				raf.getChannel().write(ByteBuffer.wrap(data.getBytes("UTF-8")));
			} finally {
				if (lock != null) {
					lock.release();
				}
				raf.close();
			}
		}
//        FileWriter fw = new FileWriter(f, append);
//        fw.write(s);
//        fw.close();
	}

	/**
	 * This function writes out a String to a file, overwriting it if it
	 * already exists
	 *
	 * @param s The string to write to the file
	 * @param f The File to write to
	 * @throws IOException If the File f cannot be written to
	 */
	public static void write(String s, File f) throws IOException {
		write(s, f, OVERWRITE);
	}

	/**
	 * Copies a file from one location to another. If overwrite is null,
	 * prompts the user on the console if the file already exists. If
	 * overwrite is false, the operation throws an exception if the file
	 * already exists. If overwrite is true, the file is overwritten without
	 * prompting if it already exists.
	 *
	 * @param fromFile
	 * @param toFile
	 * @param overwrite
	 * @throws IOException
	 */
	public static void copy(File fromFile, File toFile, Boolean overwrite)
		throws IOException {

		if (!fromFile.exists()) {
			throw new IOException("FileCopy: " + "no such source file: "
				+ fromFile.getName());
		}
		if (!fromFile.isFile()) {
			throw new IOException("FileCopy: " + "can't copy directory: "
				+ fromFile.getName());
		}
		if (!fromFile.canRead()) {
			throw new IOException("FileCopy: " + "source file is unreadable: "
				+ fromFile.getName());
		}

		if (toFile.isDirectory()) {
			toFile = new File(toFile, fromFile.getName());
		}

		if (toFile.exists()) {
			if (!toFile.canWrite()) {
				throw new IOException("FileCopy: "
					+ "destination file is unwriteable: " + toFile.getName());
			}

			String response = null;
			if (overwrite == null) {
				System.out.print("Overwrite existing file " + toFile.getName()
					+ "? (Y/N): ");
				System.out.flush();
				BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
				response = in.readLine();
			}
			if ((overwrite != null && overwrite == false) || (!response.equals("Y") && !response.equals("y"))) {
				throw new IOException("FileCopy: "
					+ "existing file was not overwritten.");
			}
			//overwrite being true falls through
		} else {
			String parent = toFile.getParent();
			if (parent == null) {
				parent = System.getProperty("user.dir");
			}
			File dir = new File(parent);
			if (!dir.exists()) {
				throw new IOException("FileCopy: "
					+ "destination directory doesn't exist: " + parent);
			}
			if (dir.isFile()) {
				throw new IOException("FileCopy: "
					+ "destination is not a directory: " + parent);
			}
			if (!dir.canWrite()) {
				throw new IOException("FileCopy: "
					+ "destination directory is unwriteable: " + parent);
			}
		}

		FileInputStream from = null;
		FileOutputStream to = null;
		try {
			from = new FileInputStream(fromFile);
			to = new FileOutputStream(toFile);
			byte[] buffer = new byte[4096];
			int bytesRead;

			while ((bytesRead = from.read(buffer)) != -1) {
				to.write(buffer, 0, bytesRead); // write
			}
		} finally {
			if (from != null) {
				try {
					from.close();
				} catch (IOException e) {
					;
				}
			}
			if (to != null) {
				try {
					to.close();
				} catch (IOException e) {
					;
				}
			}
		}
	}

	/**
	 * Moves a file from one location to another. This is a simple wrapper
	 * around File.renameTo.
	 *
	 * @param from
	 * @param to
	 */
	public static boolean move(File from, File to) {
		return from.renameTo(to);
	}

	/**
	 * Recursively deletes a file/folder structure. True is returned if ALL
	 * files were deleted. If it returns false, some or none of the files
	 * may have been deleted.
	 *
	 * @param file
	 * @return
	 */
	public static boolean recursiveDelete(File file) {
		if (file.isDirectory()) {
			boolean ret = true;
			for (File f : file.listFiles()) {
				if (!recursiveDelete(f)) {
					ret = false;
				}
			}
			if (!file.delete()) {
				ret = false;
			}
			return ret;
		} else {
			return file.delete();
		}
	}
}
