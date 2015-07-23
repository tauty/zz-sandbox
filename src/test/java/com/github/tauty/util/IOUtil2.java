package com.github.tauty.util;

import java.io.*;
import java.util.MissingResourceException;

/**
 * Created by tetsuo.uchiumi on 7/22/15.
 */
public class IOUtil2 {

    public static void copy(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream os = new FileOutputStream(dst);){
            byte[] buf = new byte[0xFFF];
            int len;
            while (-1 != (len = in.read(buf))) {
                os.write(buf, 0, len);
            }
        } catch (IOException e) {
            MissingResourceException me = new MissingResourceException("The files specified can not be read or written.",
                    src.getName() + ", " + dst.getName(), "-");
            me.initCause(e);
            throw me;
        }
    }

    public static void writeText(File file, String text) {
        try (OutputStream os = new FileOutputStream(file)){
            os.write(text.getBytes());
        } catch (IOException e) {
            MissingResourceException me = new MissingResourceException("The files specified can not be written.", file.getName(), "-");
            me.initCause(e);
            throw me;
        }
    }

    private static String readTextFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[0xFFF];
            int len;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (-1 != (len = in.read(buf))) {
                baos.write(buf, 0, len);
            }
            return baos.toString();
        } catch (IOException e) {
            MissingResourceException me = new MissingResourceException("The file specified can not be read.", file.getName(), "-");
            me.initCause(e);
            throw me;
        }
    }
}
