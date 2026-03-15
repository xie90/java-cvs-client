package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.commit.CommitCommand;

import java.io.File;
import java.util.Arrays;

public class CommitHandle extends AbstractHandle{
    public CommitHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 3 || !commandArgs[1].equals("-m")) {
            System.err.println("用法: cvs " + tag + " commit <工作目录或文件> -m <提交信息>");
            System.err.println("  提交整个目录: cvs " + tag + " commit /path/to/dir -m \"信息\"");
            System.err.println("  提交单个文件: cvs " + tag + " commit /path/to/file.txt -m \"信息\"");
            //System.err.println("  提交多个文件: cvs " + tag + " commit file1.txt file2.txt -m \"信息\"");
            return;
        }

        // 解析参数
        String message = commandArgs[commandArgs.length - 1];  // 最后一个是提交信息
        String[] paths = Arrays.copyOfRange(commandArgs, 0, commandArgs.length - 2);  // 前面的都是文件路径

        // 单个目标
        File target = new File(paths[0]);
        if (!target.exists()) {
            System.err.println("错误: 路径不存在 - " + paths[0]);
            return;
        }

        if (target.isDirectory()) {
            // 提交整个目录
            setWorkingDirectory(target);
            commit(message);
        } else {
            // 提交单个文件
            setWorkingDirectory(target.getParentFile());
            commit(message, target);
        }
    }

    /**
     * 提交修改
     */
    public boolean commit(String message) throws Exception {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("提交信息不能为空");
        }

        CommitCommand command = new CommitCommand();
        command.setMessage(message);
        command.setRecursive(true);

        return executeCommand(command, "提交修改");
    }

    /**
     * 提交指定文件的修改
     * @param message 提交信息
     * @param file 要提交的文件
     * @return 命令执行是否成功
     * @throws Exception 执行过程中可能抛出的异常
     */
    public boolean commit(String message, File file) throws Exception {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("提交信息不能为空");
        }
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

        // 检查文件是否有粘性标签
        // if (hasStickyTag(file)) {
        //     System.err.println("警告：文件 " + file.getName() + " 有粘性标签，不能直接提交");
        //     System.err.println("请先执行：cvs update -A " + file.getName());
        //     return false;
        // }

        CommitCommand command = new CommitCommand();
        command.setMessage(message);
        command.setFiles(new File[]{file});  // 指定要提交的文件

        System.out.println("正在提交文件: " + file.getName());
        System.out.println("提交信息: " + message);

        return executeCommand(command, "提交文件");
    }
}
