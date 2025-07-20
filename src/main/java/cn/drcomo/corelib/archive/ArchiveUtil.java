package cn.drcomo.corelib.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import cn.drcomo.corelib.util.DebugUtil;

/**
 * 归档与压缩工具。
 * <p>基于标准的 {@link java.util.zip} API 实现文件/目录的压缩、解压，
 * 并提供按日期归档与旧文件清理能力。</p>
 */
public class ArchiveUtil {

    private static final int BUFFER_SIZE = 8192;

    private final DebugUtil logger;

    /**
     * 构造函数。
     *
     * @param logger 调试日志工具
     */
    public ArchiveUtil(DebugUtil logger) {
        this.logger = logger;
    }

    /**
     * 将指定文件或目录压缩为 ZIP。
     *
     * @param sourcePath   待压缩的文件或目录路径
     * @param targetZipPath 生成的 zip 文件路径
     */
    public void compress(String sourcePath, String targetZipPath) {
        File source = new File(sourcePath);
        if (!source.exists()) {
            logger.warn("源路径不存在: " + sourcePath);
            return;
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZipPath))) {
            addToZip(source, source.getName(), zos);
            logger.info("压缩完成: " + targetZipPath);
        } catch (IOException e) {
            logger.error("压缩失败: " + sourcePath, e);
        }
    }

    /** 递归添加文件到 ZIP */
    private void addToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            if (!entryName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
            }
            for (File child : children) {
                addToZip(child, entryName + "/" + child.getName(), zos);
            }
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                zos.putNextEntry(new ZipEntry(entryName));
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * 解压 ZIP 文件到目标目录。
     *
     * @param zipPath ZIP 文件路径
     * @param destDir 解压到的目标目录
     */
    public void extract(String zipPath, String destDir) {
        File dir = new File(destDir);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("创建目录失败: " + destDir);
            return;
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(dir, entry.getName());
                if (entry.isDirectory()) {
                    if (!newFile.exists() && !newFile.mkdirs()) {
                        logger.warn("创建目录失败: " + newFile.getPath());
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        logger.warn("创建目录失败: " + parent.getPath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
            logger.info("解压完成: " + zipPath);
        } catch (IOException e) {
            logger.error("解压失败: " + zipPath, e);
        }
    }

    /**
     * 按当前日期生成归档文件并压缩。
     * 文件名格式: yyyyMMdd-HHmmss.zip
     *
     * @param sourcePath 待归档的文件或目录
     * @param archiveDir 归档输出目录
     * @return 生成的归档文件路径，失败时返回 null
     */
    public String archiveByDate(String sourcePath, String archiveDir) {
        String time = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String zipPath = archiveDir + File.separator + time + ".zip";
        compress(sourcePath, zipPath);
        File zipFile = new File(zipPath);
        return zipFile.exists() ? zipPath : null;
    }

    /**
     * 清理指定目录下超过一定天数的 ZIP 文件。
     *
     * @param archiveDir 归档目录
     * @param days       保留天数
     */
    public void cleanupOldArchives(String archiveDir, int days) {
        File dir = new File(archiveDir);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("目录不存在: " + archiveDir);
            return;
        }
        long expire = System.currentTimeMillis() - days * 86400000L;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null) return;
        for (File f : files) {
            if (f.lastModified() < expire && f.delete()) {
                logger.debug("已删除旧归档: " + f.getName());
            }
        }
    }

    /**
     * 将字节大小转换为可读格式。
     *
     * @param size 字节数
     * @return 友好的字符串，如 "10 MB"
     */
    public String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int unit = 0;
        double d = size;
        String[] units = {"KB", "MB", "GB", "TB"};
        while (d >= 1024 && unit < units.length - 1) {
            d /= 1024;
            unit++;
        }
        return String.format("%.1f %s", d, units[unit]);
    }
}
