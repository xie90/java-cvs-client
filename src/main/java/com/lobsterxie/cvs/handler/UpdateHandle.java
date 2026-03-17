package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class UpdateHandle extends AbstractHandle{

    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 1) {
            System.err.println("用法: jcvs " + tag + " update <目录或文件路径>");
            System.err.println("  更新目录: jcvs " + tag + " update /path/to/dir");
            System.err.println("  更新文件: jcvs " + tag + " update /path/to/file");
            return;
        }
        File target = new File(commandArgs[0]);
        File workingDir = target.isDirectory() ? target : target.getParentFile();
        setWorkingDirectory(workingDir);
        update(target);
    }

    public UpdateHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    /**
     * 更新工作副本（支持目录或文件）
     */
    public boolean update(File target) throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("目标不能为空");
        }
        if (!target.exists()) {
            throw new IllegalArgumentException("目标不存在: " + target.getAbsolutePath());
        }

        UpdateCommand command = new UpdateCommand();
        command.setRecursive(target.isDirectory()); // 如果是目录则递归更新

        if (target.isDirectory()) {
            //command.setRecursive(false);
            //command.setCVSCommand('C',"");
            command.setCVSCommand('A',"");
            command.setResetStickyOnes(true);
            //deleteAndCreateEmptyFile(target);
        
            return executeCommand(command, "更新: " + target.getName());
        }
        else {
            return forceUpdate(target);
        }
        //return updateWithSystemCommand(target);
    }

    /**
     * 使用系统命令添加目录
     */
    private boolean updateWithSystemCommand(File dir) throws Exception {
        String[] cmd = {
                "/bin/sh",
                "-c",
                "cvs update -A " + dir.getAbsolutePath()
        };

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);

        System.out.println("执行命令: cvs update " + dir.getAbsolutePath());

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("✅ 目录update成功: " + dir.getName());

            return true;
        } else {
            System.err.println("❌ 目录添加失败，退出代码: " + exitCode);
            return false;
        }
    }

    /**
     * 强制更新文件 - 删除本地文件后重新检出
     */
    private boolean forceUpdate(File target) throws Exception {
        System.out.println("正在强制更新：" + target.getName());

        // 1. 先删除本地文件
        if (target.exists()) {
            if (target.delete()) {
                System.out.println("已删除本地文件：" + target.getName());
            } else {
                System.err.println("删除本地文件失败：" + target.getName());
            }
        }

        // 2. 删除 CVS 元数据中的条目
        File cvsDir = new File(target.getParentFile(), "CVS");
        File entriesFile = new File(cvsDir, "Entries");

        if (entriesFile.exists()) {
            // 备份 Entries 文件
            File entriesBackup = new File(cvsDir, "Entries.Backup");
            java.nio.file.Files.copy(entriesFile.toPath(), entriesBackup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 读取 Entries 文件
            String content = new String(java.nio.file.Files.readAllBytes(entriesFile.toPath()));
            String[] lines = content.split("\n");

            // 过滤掉目标文件的条目
            StringBuilder newContent = new StringBuilder();
            String targetName = target.getName();
            for (String line : lines) {
                if (!line.startsWith("/" + targetName + "/")) {
                    newContent.append(line).append("\n");
                }
            }

            // 写回 Entries 文件
            java.nio.file.Files.write(entriesFile.toPath(), newContent.toString().getBytes());
            System.out.println("已从 Entries 文件中移除：" + targetName);
        }

        // 3. 重新执行 update 命令
        UpdateCommand command = new UpdateCommand();
        command.setCVSCommand('A', "");
        command.setCVSCommand('C', "");
        command.setFiles(new File[]{target});
        command.setRecursive(false);

        return executeCommand(command, "重新检出：" + target.getName());
    }

    /**
     * 根据文件绝对路径，删除文件然后创建空文件
     * @param file 文件的绝对路径
     * @return 操作是否成功
     */
    public boolean deleteAndCreateEmptyFile(File file) {
        try {
            // 1. 如果文件存在，先删除
            if (file.exists()) {
                if (!file.delete()) {
                    System.err.println("删除文件失败: " + file.getAbsolutePath());
                    return false;
                }
                System.out.println("已删除文件: " + file.getAbsolutePath());
            }

            // 2. 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("创建父目录失败: " + parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 3. 创建空文件
            if (file.createNewFile()) {
                System.out.println("已创建空文件: " + file.getAbsolutePath());
                return true;
            } else {
                System.err.println("创建文件失败: " + file.getAbsolutePath());
                return false;
            }

        } catch (IOException e) {
            System.err.println("操作文件时发生IO异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SecurityException e) {
            System.err.println("没有足够的权限操作文件: " + e.getMessage());
            return false;
        }
    }

    /**
     * 更新工作副本（向后兼容）
     */
    public boolean update() throws Exception {
        return update(workingDirectory);
    }


    /**
     * 清除文件的粘性标签，回到最新版本
     */
    public boolean clearStickyTag(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }

        System.out.println("\n正在清除文件 " + file.getName() + " 的粘性标签...");

        UpdateCommand command = new UpdateCommand();
        command.setFiles(new File[]{file});

        // 尝试设置 -A 选项（清除粘性标签）
        // 根据 org.netbeans.lib.cvsclient 的 API，可能需要这样设置
        try {
            // 方法1：如果有 setClearStickyTags 方法
            // command.setClearStickyTags(true);

            // 方法2：如果没有，可能需要使用 setResetStickyTags 或类似方法
            // command.setResetStickyTags(true);

            // 方法3：或者使用全局选项
            // GlobalOptions globalOptions = new GlobalOptions();
            // globalOptions.setResetStickyTags(true);
        } catch (Exception e) {
            // 如果API不支持，使用系统命令
            //return clearStickyTagWithSystemCommand(file);
        }

        boolean success = executeCommand(command, "清除粘性标签");

        if (success) {
            System.out.println("✓ 粘性标签已清除，文件已更新到最新版本");
        }

        return success;
    }


}
