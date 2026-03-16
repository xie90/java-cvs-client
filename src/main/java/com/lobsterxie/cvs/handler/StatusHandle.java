package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;

import java.io.File;

public class StatusHandle extends AbstractHandle {
    @Override
    public void handle(String[] commandArgs) throws Exception {
        // 修改后的代码：判断参数是目录还是文件
        if (commandArgs.length < 1) {
            System.err.println("用法: jcvs " + tag + " status <目标路径>");
            System.err.println("  示例 (查看目录所有文件历史): jcvs " + tag + " status /path/to/dir");
            System.err.println("  示例 (查看单个文件历史): jcvs " + tag + " status /path/to/file.txt");
            return;
        }
        File target = new File(commandArgs[0]);
        if (!target.exists()) {
            System.err.println("错误: 路径不存在 - " + commandArgs[0]);
            return;
        }

        if (target.isDirectory()) {
            // 如果是目录，设置工作目录并执行原有的目录 log 命令
            setWorkingDirectory(target);
            status(); // 调用你已有的无参 log() 方法（用于目录）
        } else {
            // 如果是文件，设置其父目录为工作目录，然后查看该文件的 log
            setWorkingDirectory(target.getParentFile());
            status(target); // 调用我们刚刚创建的新方法
        }
    }

    public StatusHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    /**
     * 查看文件状态
     */
    public boolean status() throws Exception {
        StatusCommand command = new StatusCommand();
        command.setRecursive(true);
        return executeCommand(command, "获取文件状态");
    }

    /**
     * 查看单个文件日志
     */
    public boolean status(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }
        LogCommand command = new LogCommand();
        //command.setRecursive(true);
        // 确保文件在工作目录下
        String filePath = file.getAbsolutePath();
        String workingPath = this.workingDirectory.getAbsolutePath();
        if (!filePath.startsWith(workingPath)) {
            throw new IllegalArgumentException("文件必须位于当前工作目录下");
        }
        command.setFiles(new File[]{file});
        return executeCommand(command, "获取日志");
    }
}
