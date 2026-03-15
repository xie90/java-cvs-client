package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractHandle {
    protected Client client;
    protected boolean connected;
    protected String tag;
    protected final ReentrantLock lock = new ReentrantLock();
    protected File workingDirectory;
    protected String cvsRootString;

    public abstract void handle(String[] commandArgs) throws Exception;

    public AbstractHandle(Client client, boolean connected, String tag, String cvsRootString) {
        this.client = client;
        this.connected = connected;
        this.tag = tag;
        this.cvsRootString = cvsRootString;
    }


    /**
     * 获取文件当前版本号（从 status 命令）
     */
    public String getCurrentRevision(File file) throws Exception {
        final String[] revision = new String[1];

        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && message.contains("Working revision:")) {
                    // 解析 "Working revision: 1.5"
                    String[] parts = message.split(":");
                    if (parts.length > 1) {
                        revision[0] = parts[1].trim();
                    }
                }
            }
        });

        StatusCommand command = new StatusCommand();
        command.setFiles(new File[]{file});

        executeCommand(command, "获取版本信息");
        return revision[0];
    }


    /**
     * 执行CVS命令的模板方法
     */
    protected  <T extends org.netbeans.lib.cvsclient.command.Command> boolean executeCommand(
            T command, String operationName) throws Exception {

        ensureReady(command);

        GlobalOptions globalOptions = new GlobalOptions();
        globalOptions.setCVSRoot(cvsRootString);

        System.out.println("正在" + operationName + "...");

        lock.lock();
        try {
            boolean success = client.executeCommand(command, globalOptions);
            if (success) {
                System.out.println(operationName + "完成");
            } else {
                System.err.println(operationName + "失败");
            }
            return success;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 确保已连接和工作目录已设置
     */
    private void ensureReady(org.netbeans.lib.cvsclient.command.Command command) {
        if (!connected || client == null) {
            throw new IllegalStateException("未连接到服务器，请先调用 connect()");
        }
        // 对于非检出命令，需要确保工作目录已设置
        if (!(command instanceof CheckoutCommand)) {
            if (workingDirectory == null) {
                throw new IllegalStateException("未设置工作目录，请先调用 setWorkingDirectory()");
            }
        }
    }

    /**
     * 设置工作目录
     */
    public void setWorkingDirectory(File workingDirectory) {
        if (workingDirectory == null) {
            throw new IllegalArgumentException("工作目录不能为空");
        }
        if (!workingDirectory.exists()) {
            throw new IllegalArgumentException("工作目录不存在: " + workingDirectory.getAbsolutePath());
        }
        if (!workingDirectory.isDirectory()) {
            throw new IllegalArgumentException("路径不是目录: " + workingDirectory.getAbsolutePath());
        }

        this.workingDirectory = workingDirectory;
        if (client != null) {
            client.setLocalPath(workingDirectory.getAbsolutePath());
        }
    }

    /**
     * 查看指定文件的版本号列表
     * @param file 要查看版本历史的文件
     * @return 版本号列表
     * @throws Exception 执行过程中可能抛出的异常
     */
    public List<String> logRevisions(File file) throws Exception {
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

        // 创建一个列表来存储版本号
        List<String> revisions = new ArrayList<>();

        // 设置一个自定义的事件监听器来捕获输出并提取版本号
        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && !message.isEmpty()) {
                    // 只提取版本号行，格式为 "revision X.X"
                    if (message.trim().startsWith("revision ")) {
                        String revision = message.trim().substring(9).trim(); // 去掉"revision "前缀
                        revisions.add(revision );
                        System.out.println("   #   " + "版本号: " + revision + "   #   "); // 立即打印版本号
                    }
                    // 不打印其他信息
                }
            }
        });

        LogCommand command = new LogCommand();
        command.setFiles(new File[]{file});

        boolean success = executeCommand(command, "获取文件版本号: " + file.getName());

        if (success) {
            System.out.println("\n共找到 " + revisions.size() + " 个版本");
        }

        return revisions;
    }


    /**
     * 检查文件是否有粘性标签
     */
    private boolean hasStickyTag(File file) throws Exception {
        final boolean[] hasSticky = new boolean[]{false};

        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && message.contains("Sticky Tag:")) {
                    hasSticky[0] = true;
                }
            }
        });

        StatusCommand command = new StatusCommand();
        command.setFiles(new File[]{file});

        executeCommand(command, "检查粘性标签");
        return hasSticky[0];
    }

    /**
     * 使用系统命令添加目录
     */
    private boolean handleWithSystemCommand(File dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cvs",
                "add",
                dir.getAbsolutePath()
        );
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);

        System.out.println("执行命令: cvs add " + dir.getAbsolutePath());

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
            System.out.println("✅ 目录添加成功: " + dir.getName());

            return true;
        } else {
            System.err.println("❌ 目录添加失败，退出代码: " + exitCode);
            return false;
        }
    }


}
