package com.sckftr.android.securephoto.utils;

import java.io.Closeable;
import java.io.IOException;

public class IOUtils {

	public static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				//can be ignored
			}
		}
	}
	
}
