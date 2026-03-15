package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LogHandle extends AbstractHandle {
    @Override
    public void handle(String[] commandArgs) throws Exception {
        // 修改后的代码：判断参数是目录还是文件
        if (commandArgs.length < 1) {
            System.err.println("用法: cvs " + tag + " log <目标路径>");
            System.err.println("  示例 (查看目录所有文件历史): cvs " + tag + " log /path/to/dir");
            System.err.println("  示例 (查看单个文件历史): cvs " + tag + " log /path/to/file.txt");
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
            logRevisions(); // 调用你已有的无参 log() 方法（用于目录）
        } else {
            // 如果是文件，设置其父目录为工作目录，然后查看该文件的 log
            setWorkingDirectory(target.getParentFile());
            logRevisions(target); // 调用我们刚刚创建的新方法
        }
    }



    /**
     * 查看当前目录下所有文件的版本号
     * @return 版本号列表
     * @throws Exception 执行过程中可能抛出的异常
     */
    public List<String> logRevisions() throws Exception {
        List<String> allRevisions = new ArrayList<>();

        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && !message.isEmpty()) {
                    if (message.trim().startsWith("revision ")) {
                        String revision = message.trim().substring(9).trim();
                        allRevisions.add(revision);
                        System.out.println("   #   " + "版本号: " + revision + "   #   ");
                    }
                }
            }
        });

        LogCommand command = new LogCommand();
        command.setRecursive(true);

        boolean success = executeCommand(command, "获取所有文件版本号");

        if (success) {
            System.out.println("\n共找到 " + allRevisions.size() + " 个版本记录");
        }

        return allRevisions;
    }

    public LogHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }




    /**
     * 查看日志
     */
    public boolean log() throws Exception {
        LogCommand command = new LogCommand();
        command.setRecursive(true);
        return executeCommand(command, "获取日志");
    }



    /**
     * 查看指定文件的版本历史 (log)
     * @param file 要查看历史的文件
     * @return 命令执行是否成功
     * @throws Exception 执行过程中可能抛出的异常
     */
    public boolean log(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }

        // 确保文件在工作目录下
        String filePath = file.getAbsolutePath();
        String workingPath = this.workingDirectory.getAbsolutePath();
        if (!filePath.startsWith(workingPath)) {
            throw new IllegalArgumentException("文件必须位于当前工作目录下");
        }

        LogCommand command = new LogCommand();
        // 关键步骤：指定要查看历史的文件
        command.setFiles(new File[]{file});

        return executeCommand(command, "查看文件历史: " + file.getName());
    }


}
