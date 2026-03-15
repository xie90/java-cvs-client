package com.lobsterxie.cvs.handler;

import com.lobsterxie.cvs.CVSClient;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;

import java.io.File;
import java.nio.file.Files;

public class CheckoutHandle extends AbstractHandle{

    public CheckoutHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 2) {
            System.err.println("用法: jcvs checkout " + tag + " <模块名> <目标目录>");
            return;
        }
        checkout(commandArgs[0], new File(commandArgs[1]));
    }

    /**
     * 检出模块
     */
    public boolean checkout(String module, File targetDirectory) throws Exception {
        if (module == null || module.trim().isEmpty()) {
            throw new IllegalArgumentException("模块名不能为空");
        }
        if (targetDirectory == null) {
            throw new IllegalArgumentException("目标目录不能为空");
        }

        // 确保目标目录存在
        Files.createDirectories(targetDirectory.toPath());

        if (!connected || client == null) {
            throw new IllegalStateException("未连接到服务器，请先调用 connect()");
        }

        CheckoutCommand command = new CheckoutCommand();
        command.setModule(module);
        command.setRecursive(true);

        // 设置本地路径为工作目录
        client.setLocalPath(targetDirectory.getAbsolutePath());

        boolean success = executeCommand(command, "检出模块: " + module);

        if (success) {
            // 设置工作目录为检出后的目录
            setWorkingDirectory(new File(targetDirectory, module));
        }

        return success;
    }

}
