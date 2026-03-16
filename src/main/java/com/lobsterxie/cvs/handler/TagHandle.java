package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * CVS Tag 处理器 - 用于给文件或目录打标签
 */
public class TagHandle extends AbstractHandle {

    public TagHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 2) {
            System.err.println("用法：jcvs " + tag + " tag <标签名> <文件或目录路径>");
            System.err.println("  给文件打标签：jcvs tag " + tag + " v1.0.0 /path/to/file.txt");
            System.err.println("  给目录打标签：jcvs tag " + tag + " v1.0.0 /path/to/dir");
            System.err.println("  删除标签：jcvs tag " + tag + " -d v1.0.0 /path/to/file.txt");
            System.err.println("  移动标签：jcvs tag " + tag + " -F -r 新版本号 v1.0.0 /path/to/file.txt");
            return;
        }

        // 解析参数
        int argIndex = 0;
        String tagName = null;
        boolean deleteTag = false;
        boolean forceTag = false;
        String revision = null;

        while (argIndex < commandArgs.length) {
            String arg = commandArgs[argIndex];
            if (arg.equals("-d")) {
                deleteTag = true;
                argIndex++;
            } else if (arg.equals("-F")) {
                forceTag = true;
                argIndex++;
            } else if (arg.equals("-r")) {
                argIndex++;
                if (argIndex < commandArgs.length) {
                    revision = commandArgs[argIndex];
                    argIndex++;
                } else {
                    System.err.println("错误：-r 选项后需要指定版本号");
                    return;
                }
            } else {
                if (tagName == null) {
                    tagName = arg;
                }
                argIndex++;
            }
        }

        if (tagName == null) {
            System.err.println("错误：必须指定标签名");
            return;
        }

        // 获取最后一个参数作为文件/目录路径
        String pathArg = commandArgs[commandArgs.length - 1];
        File target = new File(pathArg);

        if (!target.exists()) {
            System.err.println("错误：目标不存在：" + target.getAbsolutePath());
            return;
        }

        File workingDir = target.isDirectory() ? target : target.getParentFile();
        setWorkingDirectory(workingDir);

        // 执行 tag 操作
        tagFile(target, tagName, deleteTag, forceTag, revision);
    }

    /**
     * 给文件或目录打标签
     *
     * @param target     目标文件或目录
     * @param tagName    标签名称
     * @param deleteTag  是否删除标签
     * @param forceTag   是否强制移动标签
     * @param revision   指定版本号（可选）
     */
    public boolean tagFile(File target, String tagName, boolean deleteTag, boolean forceTag, String revision) throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("目标不能为空");
        }

        System.out.println("正在" + (deleteTag ? "删除" : "添加") + "标签 '" + tagName + "' 到：" + target.getAbsolutePath());

        TagCommand command = new TagCommand();
        command.setRecursive(target.isDirectory());
        command.setTag(tagName);

        if (deleteTag) {
            command.setDeleteTag(true);
        }

        if (forceTag) {
            command.setOverrideExistingTag(true);
        }

        if (revision != null) {
            command.setTagByRevision(revision);
        }

        // 设置要操作的文件
        if (!target.isDirectory()) {
            command.setFiles(new File[]{target});
        }

        return executeCommand(command, "标签操作：" + tagName);
    }

    /**
     * 使用系统命令打标签（备用方案）
     */
    @SuppressWarnings("unused")
    private boolean tagWithSystemCommand(File target, String tagName, boolean deleteTag, boolean forceTag, String revision) throws Exception {
        StringBuilder cmdBuilder = new StringBuilder("cvs tag");

        if (deleteTag) {
            cmdBuilder.append(" -d");
        }
        if (forceTag) {
            cmdBuilder.append(" -F");
        }
        if (revision != null) {
            cmdBuilder.append(" -r ").append(revision);
        }
        cmdBuilder.append(" ").append(tagName);

        if (target != null) {
            cmdBuilder.append(" ").append(target.getAbsolutePath());
        }

        String[] cmd = {"/bin/sh", "-c", cmdBuilder.toString()};

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);

        System.out.println("执行命令：" + cmdBuilder.toString());

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("标签操作成功：" + tagName);
            return true;
        } else {
            System.err.println("标签操作失败，退出代码：" + exitCode);
            return false;
        }
    }
}
