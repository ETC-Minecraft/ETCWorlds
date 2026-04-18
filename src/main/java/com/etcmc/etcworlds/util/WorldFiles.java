package com.etcmc.etcworlds.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utilidades de filesystem para mundos: copiar, eliminar recursivo, zip/unzip.
 */
public final class WorldFiles {

    private WorldFiles() {}

    public static void copyDir(File from, File to) throws IOException {
        if (!from.exists()) return;
        if (!to.exists() && !to.mkdirs())
            throw new IOException("No se pudo crear: " + to);
        if (from.isDirectory()) {
            for (String name : from.list()) {
                File child = new File(from, name);
                File out = new File(to, name);
                if (child.isDirectory()) copyDir(child, out);
                else Files.copy(child.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        return f.delete();
    }

    public static void zip(File source, File outZip) throws IOException {
        outZip.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(outZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDir(source, source.getName(), zos);
        }
    }

    private static void zipDir(File f, String basePath, ZipOutputStream zos) throws IOException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) return;
            // entry para directorio (algunos zips lo necesitan)
            zos.putNextEntry(new ZipEntry(basePath + "/"));
            zos.closeEntry();
            for (File c : children) zipDir(c, basePath + "/" + c.getName(), zos);
        } else {
            try (FileInputStream fis = new FileInputStream(f)) {
                zos.putNextEntry(new ZipEntry(basePath));
                pipe(fis, zos);
                zos.closeEntry();
            }
        }
    }

    public static void unzip(File zip, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs())
            throw new IOException("No se pudo crear: " + destDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(destDir, e.getName());
                // Zip-slip protection
                if (!out.toPath().normalize().startsWith(destDir.toPath().normalize()))
                    throw new IOException("Entrada zip insegura: " + e.getName());
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        pipe(zis, fos);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    public static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long sum = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) sum += sizeOf(c);
        return sum;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    public static Path safeChild(Path parent, String child) {
        Path resolved = parent.resolve(child).normalize();
        if (!resolved.startsWith(parent.normalize()))
            throw new IllegalArgumentException("Path inseguro: " + child);
        return resolved;
    }
}
