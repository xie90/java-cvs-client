package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.add.AddCommand;

import java.io.File;

public class AddHandle extends AbstractHandle{
    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 2) {
            System.err.println("用法: cvs " + tag + " add <工作目录> <文件路径>");
            return;
        }
        setWorkingDirectory(new File(commandArgs[0]));
        add(new File(commandArgs[1]));
    }

    public AddHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    /**
     * 添加文件
     */
    public boolean add(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }

        AddCommand command = new AddCommand();
        command.setFiles(new File[]{file});

        return executeCommand(command, "添加文件: " + file.getName());
    }

}
