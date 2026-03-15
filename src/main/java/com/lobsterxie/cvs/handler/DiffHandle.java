package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.diff.DiffCommand;

import java.io.File;

public class DiffHandle extends AbstractHandle{
    public DiffHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    @Override
    public void handle(String[] commandArgs) throws Exception {
        //String command = commandArgs[0];
        if (commandArgs.length < 1) {
            System.err.println("用法: jcvs diff" + " <工作目录>");
            return;
        }
        setWorkingDirectory(new File(commandArgs[0]));
        diff();
    }


    /**
     * 比对差异
     */
    public boolean diff() throws Exception {
        DiffCommand command = new DiffCommand();
        command.setRecursive(true);
        return executeCommand(command, "比对差异");
    }

}
