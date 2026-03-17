package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.history.HistoryCommand;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.File;

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
            System.err.println("  查看历史记录：jcvs history " + tag);
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
            System.err.println("  -r    指定版本号");
            System.err.println("  -t    指定标签");
            return;
        }

        // 解析参数
        HistoryCommand command = new HistoryCommand();

        String module = null;
        String user = null;
        String date = null;
        String revision = null;
        String tag = null;
        boolean showCommit = false;
        boolean showUpdate = false;
        boolean showCheckout = false;

        for (int i = 0; i < commandArgs.length; i++) {
            String arg = commandArgs[i];

            if (arg.equals("-c")) {
                showCommit = true;
                command.setReportCommits(true);
            } else if (arg.equals("-u")) {
                showUpdate = true;
                command.setReportEventType("U");
            } else if (arg.equals("-o")) {
                showCheckout = true;
                command.setReportCheckouts(true);
            } else if (arg.equals("-m") && i + 1 < commandArgs.length) {
                module = commandArgs[++i];
                command.addReportOnModule(module);
            } else if (arg.equals("-U") && i + 1 < commandArgs.length) {
                user = commandArgs[++i];
                command.addForUsers(user);
            } else if (arg.equals("-D") && i + 1 < commandArgs.length) {
                date = commandArgs[++i];
                command.setSinceDate(date);
            } else if (arg.equals("-r") && i + 1 < commandArgs.length) {
                revision = commandArgs[++i];
                command.setSinceRevision(revision);
            } else if (arg.equals("-t") && i + 1 < commandArgs.length) {
                tag = commandArgs[++i];
                command.setSinceTag(tag);
            } else if (arg.equals("-a")) {
                command.setForAllUsers(true);
            } else if (arg.equals("-w") && i + 1 < commandArgs.length) {
                String workingDir = commandArgs[++i];
                setWorkingDirectory(new File(workingDir));
                command.setForWorkingDirectory(true);
            }
        }

        // 如果没有设置工作目录，使用当前用户目录
        if (workingDirectory == null) {
            setWorkingDirectory(new File(System.getProperty("user.dir")));
        }

        // 默认显示提交记录
        if (!showCommit && !showUpdate && !showCheckout) {
            command.setReportCommits(true);
        }

        System.out.println("正在查询 CVS 历史记录...");
        System.out.println();

        // 添加事件监听器来捕获并输出历史记录
        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && !message.isEmpty()) {
                    // 过滤掉无效记录的警告信息
                    if (message.contains("warning") && message.contains("invalid")) {
                        return;
                    }
                    if (e.isError()) {
                        System.err.println("错误：" + message);
                    } else {
                        System.out.println(message);
                    }
                }
            }
        });

        executeCommand(command, "查询历史记录");
    }
}
