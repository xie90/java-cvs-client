package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * CVS History 处理器 - 用于查看 CVS 历史记录
 */
public class HistoryHandle extends AbstractHandle {

    public HistoryHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 1) {
            System.err.println("用法：jcvs " + tag + " history [选项]");
            System.err.println("  查看历史记录：jcvs history " + tag + "");
            System.err.println("  查看提交记录：jcvs history " + tag + " -c");
            System.err.println("  查看更新记录：jcvs history " + tag + " -u");
            System.err.println("  查看检出记录：jcvs history " + tag + " -o");
            System.err.println("  查看指定模块：jcvs history " + tag + " -m 模块名");
            System.err.println("  查看指定用户：jcvs history " + tag + " -U 用户名");
            System.err.println("  查看指定日期后：jcvs history " + tag + " -D 2024-01-01");
            System.err.println("\n选项:");
            System.err.println("  -c    查看提交 (commit) 记录");
            System.err.println("  -u    查看更新 (update) 记录");
            System.err.println("  -o    查看检出 (checkout) 记录");
            System.err.println("  -m    指定模块名");
            System.err.println("  -U    指定用户名");
            System.err.println("  -D    指定起始日期 (YYYY-MM-DD)");
            System.err.println("  -r    只查看最近 N 条记录");
            return;
        }

        // 解析参数
        StringBuilder cmdBuilder = new StringBuilder("cvs history");

        String module = null;
        String user = null;
        String date = null;
        int limit = -1;
        boolean showCommit = false;
        boolean showUpdate = false;
        boolean showCheckout = false;

        for (int i = 0; i < commandArgs.length; i++) {
            String arg = commandArgs[i];

            if (arg.equals("-c")) {
                showCommit = true;
                cmdBuilder.append(" -c");
            } else if (arg.equals("-u")) {
                showUpdate = true;
                cmdBuilder.append(" -u");
            } else if (arg.equals("-o")) {
                showCheckout = true;
                cmdBuilder.append(" -o");
            } else if (arg.equals("-m") && i + 1 < commandArgs.length) {
                module = commandArgs[++i];
                cmdBuilder.append(" -m ").append(module);
            } else if (arg.equals("-U") && i + 1 < commandArgs.length) {
                user = commandArgs[++i];
                cmdBuilder.append(" -U ").append(user);
            } else if (arg.equals("-D") && i + 1 < commandArgs.length) {
                date = commandArgs[++i];
                cmdBuilder.append(" -D ").append(date);
            } else if (arg.equals("-r") && i + 1 < commandArgs.length) {
                limit = Integer.parseInt(commandArgs[++i]);
            }
        }

        // 默认显示提交记录
        if (!showCommit && !showUpdate && !showCheckout) {
            showCommit = true;
            cmdBuilder.append(" -c");
        }

        System.out.println("执行命令：" + cmdBuilder);
        System.out.println();

        // 使用系统命令执行 cvs history
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmdBuilder.toString());
        pb.directory(workingDirectory != null ? workingDirectory : new File("."));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (limit > 0 && count >= limit) break;
                System.out.println(line);
                count++;
            }
        }

        int exitCode = process.waitFor();

        System.out.println();
        if (exitCode == 0) {
            System.out.println("共找到 " + count + " 条历史记录");
        } else {
            System.err.println("history 命令执行失败，退出代码：" + exitCode);
        }
    }
}
