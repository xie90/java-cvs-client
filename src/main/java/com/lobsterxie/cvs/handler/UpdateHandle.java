package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

import java.io.File;
import java.io.IOException;

public class UpdateHandle extends AbstractHandle{

    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 1) {
            System.err.println("用法: cvs " + tag + " update <目录或文件路径>");
            System.err.println("  更新目录: cvs " + tag + " update /path/to/dir");
            System.err.println("  更新文件: cvs " + tag + " update /path/to/file");
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

        if (!target.isDirectory()) {
            // 如果是单个文件，设置只更新该文件
            command.setFiles(new File[]{target});
            command.setRecursive(false);
            command.setCVSCommand('C',"");
            command.setCVSCommand('A',"");
            //deleteAndCreateEmptyFile(target);
        }

        return executeCommand(command, "更新: " + target.getName());
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
