package cn.drcomo.corelib.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.Deflater;

import cn.drcomo.corelib.util.DebugUtil;

/**
 * 归档与压缩工具。
 * <p>基于标准的 <code>java.util.zip</code> API 实现文件/目录的压缩、解压， 并提供按日期归档与旧文件清理能力。</p>
 */
public class ArchiveUtil {

    /** 缓冲区大小 */
    private static final int BUFFER_SIZE = 8192;

    /** 调试日志工具 */
    private final DebugUtil logger;

    /**
     * 构造函数。
     *
     * @param logger 调试日志工具
     */
    public ArchiveUtil(DebugUtil logger) {
        this.logger = logger;
    }

    // ========================== 压缩相关 ==========================

    /**
     * 将指定文件或目录压缩为 ZIP，使用默认压缩级别。
     *
     * @param sourcePath    待压缩的文件或目录路径
     * @param targetZipPath 生成的 zip 文件路径
     */
    public void compress(String sourcePath, String targetZipPath) {
        compress(sourcePath, targetZipPath, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * 将指定文件或目录压缩为 ZIP，并指定压缩级别。
     *
     * @param sourcePath    待压缩的文件或目录路径
     * @param targetZipPath 生成的 zip 文件路径
     * @param level         压缩级别，范围 -1~9
     */
    public void compress(String sourcePath, String targetZipPath, int level) {
        File source = new File(sourcePath);
        if (!source.exists()) {
            logger.warn("源路径不存在: " + sourcePath);
            return;
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZipPath))) {
            zos.setLevel(level);
            addToZip(source, source.getName(), zos);
            logger.info("压缩完成: " + targetZipPath);
        } catch (IOException e) {
            logger.error("压缩失败: " + sourcePath, e);
        }
    }

    // ========================== 解压相关 ==========================

    /**
     * 解压 ZIP 文件到目标目录。
     *
     * @param zipPath ZIP 文件路径
     * @param destDir 解压到的目标目录
     */
    public void extract(String zipPath, String destDir) {
        File dir = new File(destDir);
        // 初始化目标目录
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("创建目录失败: " + destDir);
            return;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(dir, entry.getName());
                if (entry.isDirectory()) {
                    // 创建子目录
                    createDir(newFile, false);
                } else {
                    // 确保父目录存在
                    createDir(newFile.getParentFile(), false);
                    // 写入文件内容
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        copyBytes(zis, fos);
                    }
                }
                zis.closeEntry();
            }
            logger.info("解压完成: " + zipPath);
        } catch (IOException e) {
            logger.error("解压失败: " + zipPath, e);
        }
    }

    // ========================== 归档与清理 ==========================

    /**
     * 按当前日期生成归档文件并压缩。
     * <p>文件名格式: yyyyMMdd-HHmmss.zip</p>
     *
     * @param sourcePath 待归档的文件或目录
     * @param archiveDir 归档输出目录
     * @return 生成的归档文件路径，失败时返回 null
     */
    public String archiveByDate(String sourcePath, String archiveDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String zipPath = archiveDir + File.separator + timestamp + ".zip";
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
        long expireTime = System.currentTimeMillis() - days * 86_400_000L;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.lastModified() < expireTime && f.delete()) {
                logger.debug("已删除旧归档: " + f.getName());
            }
        }
    }

    // ========================== 工具方法 ==========================

    /**
     * 递归添加文件或目录到 ZIP。
     *
     * @param file      当前处理的文件或目录
     * @param entryName 在 ZIP 中的条目名称
     * @param zos       ZIP 输出流
     * @throws IOException IO 异常
     */
    private void addToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            // 目录条目须以“/”结尾
            if (!entryName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
            }
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(child, entryName + "/" + child.getName(), zos);
                }
            }
        } else {
            // 文件内容写入
            try (FileInputStream fis = new FileInputStream(file)) {
                zos.putNextEntry(new ZipEntry(entryName));
                copyBytes(fis, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * 创建目录，如果已存在则跳过，失败时记录日志。
     *
     * @param dir       目标目录
     * @param isError   是否以 error 级别记录（false 则 warn）
     * @return true 表示目录存在或创建成功，false 表示创建失败
     */
    private boolean createDir(File dir, boolean isError) {
        if (dir == null) {
            return false;
        }
        if (dir.exists()) {
            return true;
        }
        if (dir.mkdirs()) {
            return true;
        } else {
            String msg = "创建目录失败: " + dir.getPath();
            if (isError) {
                logger.error(msg);
            } else {
                logger.warn(msg);
            }
            return false;
        }
    }

    /**
     * 从输入流复制字节到输出流，直至 EOF。
     *
     * @param in  输入流
     * @param out 输出流
     * @throws IOException IO 异常
     */
    private void copyBytes(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    // ========================== 未使用代码（隐藏） ==========================
    // 下面为暂未调用的方法或示例，保留以备后续扩展：
    // // public void unusedMethod() { … }
}
